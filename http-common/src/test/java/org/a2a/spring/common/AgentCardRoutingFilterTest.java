/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCardRoutingFilterTest {

    private static final String AGENT_CARD = "/.well-known/agent-card.json";

    @Test
    void agentCard_routesToHighestVersion() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of(
                TestProviders.provider("0.3", true, "/a2a_rest_v0.3", "/v1"),
                TestProviders.provider("1.0", false, "/a2a_rest_v1.0", "/")));

        assertEquals("/a2a_rest_v1.0" + AGENT_CARD, routedPath(filter, "GET", AGENT_CARD));
    }

    @Test
    void agentCard_appendsRestBasePath_whenNotRoot() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of(
                TestProviders.provider("0.3", true, "/a2a_rest_v0.3", "/v1")));

        assertEquals("/a2a_rest_v0.3/v1" + AGENT_CARD, routedPath(filter, "GET", AGENT_CARD));
    }

    @Test
    void agentCard_omitsRestBasePath_whenRoot() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of(
                TestProviders.provider("1.0", true, "/a2a_rest_v1.0", "/")));

        assertEquals("/a2a_rest_v1.0" + AGENT_CARD, routedPath(filter, "GET", AGENT_CARD));
    }

    @Test
    void jsonRpcProvider_withNullRestBasePath_usesInternalPrefixOnly() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of(
                TestProviders.jsonRpcProvider("1.0", true)));

        assertEquals("/a2a_jsonrpc_v1.0" + AGENT_CARD, routedPath(filter, "GET", AGENT_CARD));
    }

    @Test
    void versionComparison_isNumericNotLexicographic() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of(
                TestProviders.provider("0.9", false, "/a2a_rest_v0.9", "/"),
                TestProviders.provider("0.10", false, "/a2a_rest_v0.10", "/")));

        assertEquals("/a2a_rest_v0.10" + AGENT_CARD, routedPath(filter, "GET", AGENT_CARD));
    }

    @Test
    void nonGetRequest_isNotRewritten() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of(
                TestProviders.provider("1.0", true, "/a2a_rest_v1.0", "/")));

        assertEquals(AGENT_CARD, routedPath(filter, "POST", AGENT_CARD));
    }

    @Test
    void otherPath_isNotRewritten() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of(
                TestProviders.provider("1.0", true, "/a2a_rest_v1.0", "/")));

        assertEquals("/tasks/abc", routedPath(filter, "GET", "/tasks/abc"));
    }

    @Test
    void noProviders_isNotRewritten() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of());

        assertEquals(AGENT_CARD, routedPath(filter, "GET", AGENT_CARD));
    }

    @Test
    void rewrite_preservesOriginalRequestUriForTenantExtraction() throws Exception {
        AgentCardRoutingFilter filter = new AgentCardRoutingFilter(List.of(
                TestProviders.provider("1.0", true, "/a2a_rest_v1.0", "/")));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", AGENT_CARD), new MockHttpServletResponse(), chain);

        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        assertEquals(AGENT_CARD, PathRewriteHttpServletRequest.originalRequestUri(downstream));
    }

    static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    static String routedPath(jakarta.servlet.Filter filter, String method, String path) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(method, path), new MockHttpServletResponse(), chain);
        return ((HttpServletRequest) chain.getRequest()).getRequestURI();
    }
}
