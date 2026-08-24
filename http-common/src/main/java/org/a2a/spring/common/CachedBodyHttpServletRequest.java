/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

/**
 * Request wrapper that buffers the entity body so it can be read more than once, and that allows a filter to
 * override request headers.
 *
 * <p>This exists because of a structural difference between JAX-RS and the Servlet API. A JAX-RS
 * {@code @PreMatching ContainerRequestFilter} is handed a mutable {@code ContainerRequestContext}: the old
 * {@code A2AJsonRpcAcceptFilter} could call {@code getEntityStream()}, drain it, and then hand a fresh
 * {@code ByteArrayInputStream} back with {@code setEntityStream(...)}, and it could rewrite the {@code Accept}
 * header simply by mutating {@code getHeaders()}. A Servlet {@code Filter} has neither capability - the body
 * is a one-shot stream and the header map is read-only - so both are reintroduced here through a wrapper.
 *
 * <p>Spring's {@code ContentCachingRequestWrapper} is deliberately not used: it caches bytes only <em>as they
 * are consumed downstream</em>, so it cannot serve a filter that needs the full body <em>before</em> the
 * controller runs, and it offers no header override.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;
    private final Map<String, String> overriddenHeaders = new LinkedHashMap<>();

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /**
     * Returns the buffered entity body decoded with the request character encoding, defaulting to UTF-8.
     * Equivalent to the JAX-RS {@code new String(entityStream.readAllBytes(), UTF_8)} in the original filter.
     */
    public String getBodyAsString() {
        String encoding = getCharacterEncoding();
        return encoding == null
                ? new String(cachedBody, StandardCharsets.UTF_8)
                : new String(cachedBody, java.nio.charset.Charset.forName(encoding));
    }

    /**
     * Replaces the value of a request header for everything downstream of this wrapper. Header names are
     * matched case-insensitively, as required by the Servlet API.
     */
    public void setHeader(String name, String value) {
        overriddenHeaders.put(name.toLowerCase(Locale.ROOT), value);
    }

    @Override
    public String getHeader(String name) {
        String override = overriddenHeaders.get(name.toLowerCase(Locale.ROOT));
        return override != null ? override : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String override = overriddenHeaders.get(name.toLowerCase(Locale.ROOT));
        return override != null
                ? Collections.enumeration(Collections.singletonList(override))
                : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original != null && original.hasMoreElements()) {
            names.add(original.nextElement());
        }
        for (String overridden : overriddenHeaders.keySet()) {
            boolean alreadyPresent = names.stream().anyMatch(n -> n.equalsIgnoreCase(overridden));
            if (!alreadyPresent) {
                names.add(overridden);
            }
        }
        return Collections.enumeration(names);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffer = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return buffer.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("Non-blocking reads are not supported on a cached body");
            }

            @Override
            public int read() {
                return buffer.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return buffer.read(b, off, len);
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        return new BufferedReader(new InputStreamReader(getInputStream(),
                encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
    }
}
