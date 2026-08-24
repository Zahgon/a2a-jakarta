/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Request wrapper that reports a rewritten path, so that Spring MVC handler mapping selects the versioned
 * internal controller instead of the client-facing path the caller actually requested.
 *
 * <p>This replaces the JAX-RS {@code ContainerRequestContext.setRequestUri(baseUri, newUri)} call the three
 * routing filters used. In JAX-RS that method is only legal from a {@code @PreMatching} filter, precisely
 * because it must happen before resource matching. The Servlet equivalent is to hand the rewritten path to
 * {@code DispatcherServlet}, which resolves the request path from {@link HttpServletRequest#getRequestURI()}
 * during {@code doService} - i.e. after all filters have run - so wrapping here is honoured by handler
 * mapping.
 *
 * <p>The supplied path is context-relative and must start with {@code /}; the context path is re-applied for
 * {@link #getRequestURI()} and {@link #getRequestURL()} so that absolute URLs stay correct behind a non-root
 * context path.
 *
 * <p><strong>Behavioural note.</strong> JAX-RS {@code setRequestUri} rewrote only the JAX-RS view of the
 * request; the underlying {@code HttpServletRequest.getRequestURI()} kept returning the original, client-facing
 * URI. Server code that injected {@code @Context HttpServletRequest} - notably the tenant extraction in both
 * transport delegates - therefore saw the <em>unrewritten</em> URI. Overriding {@link #getRequestURI()} here is
 * what makes Spring MVC handler mapping work, but it would silently change that tenant extraction, so the
 * pre-rewrite URI is preserved as the {@link #ORIGINAL_REQUEST_URI_ATTRIBUTE} request attribute and should be
 * read back through {@link #originalRequestUri(HttpServletRequest)}.
 */
public class PathRewriteHttpServletRequest extends HttpServletRequestWrapper {

    /**
     * Request attribute holding the client-facing request URI as it was before any routing filter rewrote it.
     */
    public static final String ORIGINAL_REQUEST_URI_ATTRIBUTE =
            PathRewriteHttpServletRequest.class.getName() + ".originalRequestUri";

    private final String rewrittenPath;

    public PathRewriteHttpServletRequest(HttpServletRequest request, String rewrittenPath) {
        super(request);
        if (rewrittenPath == null || !rewrittenPath.startsWith("/")) {
            throw new IllegalArgumentException("Rewritten path must be absolute within the context: " + rewrittenPath);
        }
        this.rewrittenPath = rewrittenPath;
        if (request.getAttribute(ORIGINAL_REQUEST_URI_ATTRIBUTE) == null) {
            request.setAttribute(ORIGINAL_REQUEST_URI_ATTRIBUTE, request.getRequestURI());
        }
    }

    /**
     * Returns the client-facing request URI, i.e. the URI before the A2A routing filters prefixed it with a
     * version-internal path. Falls back to {@link HttpServletRequest#getRequestURI()} when the request was
     * never rewritten.
     *
     * <p>This is the accessor that reproduces the JAX-RS behaviour described in the class javadoc, and it is
     * what tenant extraction must use.
     */
    public static String originalRequestUri(HttpServletRequest request) {
        Object original = request.getAttribute(ORIGINAL_REQUEST_URI_ATTRIBUTE);
        return original instanceof String uri ? uri : request.getRequestURI();
    }

    @Override
    public String getRequestURI() {
        String contextPath = getContextPath();
        return contextPath == null || contextPath.isEmpty() ? rewrittenPath : contextPath + rewrittenPath;
    }

    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        url.append(getScheme()).append("://").append(getServerName());
        int port = getServerPort();
        boolean defaultPort = ("http".equals(getScheme()) && port == 80)
                || ("https".equals(getScheme()) && port == 443);
        if (!defaultPort && port > 0) {
            url.append(':').append(port);
        }
        url.append(getRequestURI());
        return url;
    }

    /**
     * The A2A application always runs on the default {@code DispatcherServlet} mapping ({@code /}), where the
     * servlet path carries the whole context-relative path and the path info is {@code null}.
     */
    @Override
    public String getServletPath() {
        return rewrittenPath;
    }

    @Override
    public String getPathInfo() {
        return null;
    }
}
