/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Serves the well-known agent card from the highest protocol version deployed.
 *
 * <p>The agent card is fetched before a client knows which protocol versions the agent speaks, so it carries
 * no {@code A2A-Version} header and cannot be routed by version negotiation. It is therefore always answered
 * by the newest version present, which advertises the full set of supported versions.
 *
 * <p>Migrated from a JAX-RS {@code @Provider @PreMatching @Priority(50) ContainerRequestFilter}; see
 * {@link A2AJsonRpcAcceptFilter} for the {@code @PreMatching} to Servlet filter mapping. The order (50) keeps
 * this filter ahead of both version-routing filters, as it was under JAX-RS priorities.
 */
@Component
@Order(AgentCardRoutingFilter.ORDER)
public class AgentCardRoutingFilter extends OncePerRequestFilter {

    /** Mirrors the JAX-RS {@code @Priority(50)} of the original filter. */
    public static final int ORDER = 50;

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentCardRoutingFilter.class);

    private static final String AGENT_CARD_PATH = ".well-known/agent-card.json";

    private final List<A2AVersionProvider> allVersionProviders;

    private volatile boolean initialized;
    private A2AVersionProvider selectedProvider;

    public AgentCardRoutingFilter(List<A2AVersionProvider> allVersionProviders) {
        this.allVersionProviders = allVersionProviders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = ServletPaths.pathWithinContext(request);
        if (!HttpMethod.GET.matches(request.getMethod()) || !path.endsWith(AGENT_CARD_PATH)) {
            chain.doFilter(request, response);
            return;
        }

        ensureInitialized();
        if (selectedProvider == null) {
            chain.doFilter(request, response);
            return;
        }

        String prefix = selectedProvider.getInternalPathPrefix();
        String restBasePath = selectedProvider.getRestBasePath();
        if (restBasePath != null && !"/".equals(restBasePath)) {
            prefix += restBasePath;
        }
        String newPath = prefix + (path.startsWith("/") ? path : "/" + path);
        LOGGER.debug("Agent card routing: {} -> {}", path, newPath);
        chain.doFilter(new PathRewriteHttpServletRequest(request, newPath), response);
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    A2AVersionProvider highest = null;
                    for (A2AVersionProvider provider : allVersionProviders) {
                        if (highest == null || compareVersions(provider.getVersion(), highest.getVersion()) > 0) {
                            highest = provider;
                        }
                    }
                    selectedProvider = highest;
                    initialized = true;
                }
            }
        }
    }

    /**
     * Compares dotted version strings numerically, so {@code "0.10"} sorts above {@code "0.9"} and a missing
     * trailing segment is treated as zero.
     */
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseSegment(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseSegment(parts2[i]) : 0;
            int comparison = Integer.compare(p1, p2);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int parseSegment(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
