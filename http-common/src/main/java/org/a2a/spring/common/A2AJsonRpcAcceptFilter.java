/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.a2aproject.sdk.common.A2AHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the {@code Accept} header for JSON-RPC requests based on whether the invoked method streams, and routes
 * the request to the controller for the protocol version named by the {@code A2A-Version} header.
 *
 * <p>Migrated from a JAX-RS {@code @Provider @PreMatching @Priority(100) ContainerRequestFilter}. The Spring
 * counterpart of a {@code @PreMatching} filter is a Servlet filter, because both run before the framework
 * picks a handler - which is mandatory here, since this filter rewrites the path that handler selection uses.
 * {@code @Priority(100)} becomes {@code @Order(100)}; the relative order against
 * {@link AgentCardRoutingFilter} (50) and {@link A2ARestVersionRoutingFilter} (200) is preserved.
 *
 * <p>Two JAX-RS request-mutation APIs have no Servlet equivalent and are reintroduced by wrappers:
 * {@code setEntityStream} (needed because the body is inspected here and re-read by the controller) and
 * header mutation, both provided by {@link CachedBodyHttpServletRequest}; and {@code setRequestUri}, provided
 * by {@link PathRewriteHttpServletRequest}.
 */
@Component
@Order(A2AJsonRpcAcceptFilter.ORDER)
public class A2AJsonRpcAcceptFilter extends OncePerRequestFilter {

    /** Mirrors the JAX-RS {@code @Priority(100)} of the original filter. */
    public static final int ORDER = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(A2AJsonRpcAcceptFilter.class);

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+|\"[^\"]*\"|null)");

    private final List<A2AVersionProvider> allVersionProviders;
    private final List<A2AJsonRpcMethodProvider> methodProviders;

    private volatile boolean initialized;
    private Set<String> allStreamingMethods;
    private Set<String> allNonStreamingMethods;
    private A2AVersionResolver versionResolver;

    /**
     * CDI {@code Instance<T>} injection becomes constructor injection of a {@code List<T>}: both resolve every
     * bean implementing the interface, and Spring supplies an empty list when there are none.
     */
    public A2AJsonRpcAcceptFilter(List<A2AVersionProvider> allVersionProviders,
                                  List<A2AJsonRpcMethodProvider> methodProviders) {
        this.allVersionProviders = allVersionProviders;
        this.methodProviders = methodProviders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = ServletPaths.pathWithinContext(request);
        boolean isJsonRpcRequest = ("/".equals(path) || path.isEmpty())
                && HttpMethod.POST.matches(request.getMethod());
        if (!isJsonRpcRequest) {
            chain.doFilter(request, response);
            return;
        }

        ensureInitialized();
        if (!versionResolver.hasProviders()) {
            chain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestBody = cachedRequest.getBodyAsString();
        if (requestBody.isEmpty()) {
            // The original filter keyed off ContainerRequestContext.hasEntity(); an empty body is not a
            // JSON-RPC call and is passed through untouched.
            chain.doFilter(cachedRequest, response);
            return;
        }

        if (containsAny(requestBody, allStreamingMethods)) {
            LOGGER.debug("Handling request as streaming: {}", requestBody);
            cachedRequest.setHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
        } else if (containsAny(requestBody, allNonStreamingMethods)) {
            LOGGER.debug("Handling request as non-streaming: {}", requestBody);
            cachedRequest.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        }

        String requestedVersion = cachedRequest.getHeader(A2AHeaders.A2A_VERSION);
        A2AVersionProvider provider = versionResolver.resolve(requestedVersion);
        if (provider == null) {
            sendUnsupportedVersion(response, requestedVersion, requestBody);
            return;
        }

        String newPath = provider.getInternalPathPrefix() + (path.startsWith("/") ? path : "/" + path);
        chain.doFilter(new PathRewriteHttpServletRequest(cachedRequest, newPath), response);
    }

    private void sendUnsupportedVersion(HttpServletResponse response, String requestedVersion, String requestBody)
            throws IOException {
        String requestId = extractRequestId(requestBody);
        String errorBody = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32009,\"message\":\"Protocol version '"
                + InternalPaths.escapeJsonValue(requestedVersion)
                + "' is not supported. Supported versions: "
                + InternalPaths.escapeJsonValue(versionResolver.supportedVersionsString())
                + "\"},\"id\":" + requestId + "}";
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Not getWriter(): it encodes with the container default (ISO-8859-1 on Tomcat), corrupting the
        // echoed version header and appending ";charset=ISO-8859-1". The source emits bare application/json.
        response.getOutputStream().write(errorBody.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    private static boolean containsAny(String requestBody, Set<String> methodNames) {
        for (String methodName : methodNames) {
            if (requestBody.contains(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the JSON-RPC {@code id} verbatim so the error response echoes it, defaulting to {@code null}
     * when the body has no parseable id.
     */
    private static String extractRequestId(String requestBody) {
        if (requestBody == null) {
            return "null";
        }
        Matcher matcher = ID_PATTERN.matcher(requestBody);
        return matcher.find() ? matcher.group(1) : "null";
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    Set<String> streaming = new HashSet<>();
                    Set<String> nonStreaming = new HashSet<>();
                    for (A2AJsonRpcMethodProvider methodProvider : methodProviders) {
                        streaming.addAll(methodProvider.getStreamingMethodNames());
                        nonStreaming.addAll(methodProvider.getNonStreamingMethodNames());
                    }
                    allStreamingMethods = streaming;
                    allNonStreamingMethods = nonStreaming;

                    List<A2AVersionProvider> jsonRpcProviders = allVersionProviders.stream()
                            .filter(p -> p.getInternalPathPrefix().startsWith(InternalPaths.JSONRPC_PREFIX))
                            .toList();
                    versionResolver = new A2AVersionResolver(jsonRpcProviders);
                    initialized = true;
                }
            }
        }
    }
}
