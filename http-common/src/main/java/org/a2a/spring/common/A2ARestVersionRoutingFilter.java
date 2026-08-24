/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.a2aproject.sdk.common.A2AHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Routes client-facing REST paths to the controller for the requested protocol version.
 *
 * <p>Migrated from a JAX-RS {@code @Provider @PreMatching @Priority(200) ContainerRequestFilter}; see
 * {@link A2AJsonRpcAcceptFilter} for why {@code @PreMatching} maps onto a Servlet filter and how
 * {@code setRequestUri} is reproduced. Routing behaviour - including the longest-base-path-first ordering and
 * the "only rewrite paths that look like A2A paths" guard - is carried over unchanged.
 */
@Component
@Order(A2ARestVersionRoutingFilter.ORDER)
public class A2ARestVersionRoutingFilter extends OncePerRequestFilter {

    /** Mirrors the JAX-RS {@code @Priority(200)} of the original filter. */
    public static final int ORDER = 200;

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARestVersionRoutingFilter.class);

    private final List<A2AVersionProvider> allVersionProviders;

    private volatile boolean initialized;
    private A2AVersionResolver versionResolver;
    private Set<String> knownRestBasePaths;
    private Set<String> rootProviderPathPrefixes;

    public A2ARestVersionRoutingFilter(List<A2AVersionProvider> allVersionProviders) {
        this.allVersionProviders = allVersionProviders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = ServletPaths.pathWithinContext(request);

        // The agent card is routed by AgentCardRoutingFilter, and already-rewritten internal paths must not
        // be rewritten twice.
        if (path.startsWith("/.well-known/") || path.startsWith(InternalPaths.A2A_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        ensureInitialized();
        if (!versionResolver.hasProviders()) {
            chain.doFilter(request, response);
            return;
        }

        String requestedVersion = request.getHeader(A2AHeaders.A2A_VERSION);
        if (requestedVersion == null && !matchesKnownPath(path)) {
            // No version was asked for and the path is not one this application serves, so leave it alone.
            chain.doFilter(request, response);
            return;
        }

        A2AVersionProvider provider = versionResolver.resolve(requestedVersion);
        if (provider == null) {
            sendUnsupportedVersion(response, requestedVersion);
            return;
        }

        String newPath = provider.getInternalPathPrefix() + (path.startsWith("/") ? path : "/" + path);
        LOGGER.debug("REST version routing: {} -> {}", path, newPath);
        chain.doFilter(new PathRewriteHttpServletRequest(request, newPath), response);
    }

    private boolean matchesKnownPath(String path) {
        for (String basePath : knownRestBasePaths) {
            if ("/".equals(basePath)) {
                continue;
            }
            if (path.startsWith(basePath + "/") || path.equals(basePath)) {
                return true;
            }
        }
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        for (String prefix : rootProviderPathPrefixes) {
            if (relativePath.equals(prefix)
                    || relativePath.startsWith(prefix + "/")
                    || relativePath.startsWith(prefix + ":")) {
                return true;
            }
        }
        return false;
    }

    private void sendUnsupportedVersion(HttpServletResponse response, String requestedVersion) throws IOException {
        String errorBody = "{\"error\":{\"code\":-32001,\"message\":\"Protocol version '"
                + InternalPaths.escapeJsonValue(requestedVersion)
                + "' is not supported. Supported versions: "
                + InternalPaths.escapeJsonValue(versionResolver.supportedVersionsString())
                + "\"}}";
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Not getWriter(): it encodes with the container default (ISO-8859-1 on Tomcat), corrupting the
        // echoed version header and appending ";charset=ISO-8859-1". The source emits bare application/json.
        response.getOutputStream().write(errorBody.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    List<A2AVersionProvider> restProviders = new ArrayList<>();
                    // Longest base path first, so "/v1beta" wins over "/v1" for "/v1beta/tasks".
                    Set<String> basePaths = new TreeSet<>(Comparator.comparingInt(String::length).reversed()
                            .thenComparing(Comparator.naturalOrder()));
                    Set<String> rootPrefixes = new HashSet<>();
                    for (A2AVersionProvider provider : allVersionProviders) {
                        String restBasePath = provider.getRestBasePath();
                        if (restBasePath == null) {
                            continue;
                        }
                        restProviders.add(provider);
                        basePaths.add(restBasePath);
                        if ("/".equals(restBasePath)) {
                            rootPrefixes.addAll(provider.getRestPathPrefixes());
                        }
                    }
                    knownRestBasePaths = basePaths;
                    rootProviderPathPrefixes = rootPrefixes;
                    versionResolver = new A2AVersionResolver(restProviders);
                    initialized = true;
                }
            }
        }
    }
}
