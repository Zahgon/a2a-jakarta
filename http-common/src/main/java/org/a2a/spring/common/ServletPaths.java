/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Recovers the context-relative request path that the JAX-RS filters read from
 * {@code UriInfo.getPath()}.
 *
 * <p>JAX-RS hands filters a single normalised path relative to the application. The Servlet API splits the
 * same information across {@code servletPath} and {@code pathInfo}, so the two are concatenated here. The A2A
 * application runs on the default {@code DispatcherServlet} mapping ({@code /}), where {@code servletPath}
 * carries the whole path and {@code pathInfo} is {@code null}, but both are handled so the filters keep
 * working behind a non-default mapping.
 */
final class ServletPaths {

    private ServletPaths() {
    }

    static String pathWithinContext(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        String path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
        return path.isEmpty() ? "/" : path;
    }
}
