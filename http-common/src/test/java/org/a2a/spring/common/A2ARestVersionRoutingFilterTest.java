/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.common.A2AHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2ARestVersionRoutingFilterTest {

    private static final Set<String> ROOT_PREFIXES = Set.of("tasks", "message:", "card", "extendedAgentCard");

    private static A2AVersionProvider rootProvider(String version, boolean isDefault) {
        return TestProviders.provider(version, isDefault, "/a2a_rest_v" + version, "/", ROOT_PREFIXES);
    }

    private static A2AVersionProvider basePathProvider(String version, boolean isDefault, String basePath) {
        return TestProviders.provider(version, isDefault, "/a2a_rest_v" + version, basePath);
    }

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    private static MockFilterChain dispatch(A2ARestVersionRoutingFilter filter, MockHttpServletRequest request,
            MockHttpServletResponse response) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain;
    }

    private static String routedPath(A2ARestVersionRoutingFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockFilterChain chain = dispatch(filter, request, new MockHttpServletResponse());
        return ((HttpServletRequest) chain.getRequest()).getRequestURI();
    }

    @Test
    void rootProvider_rewritesKnownPrefixWithoutVersionHeader() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));

        assertEquals("/a2a_rest_v1.0/tasks/abc", routedPath(filter, get("/tasks/abc")));
    }

    /**
     * Pins a latent upstream quirk that the migration preserves deliberately.
     *
     * <p>{@code RestVersionProvider_v1_0} declares the prefix literal {@code "message:"} (trailing colon), while
     * the matcher tests {@code startsWith(prefix + ":")}, i.e. {@code "message::"}. So {@code /message:send} does
     * not match any root prefix and is left unrewritten when no {@code A2A-Version} header is present. Real
     * clients send the header, which takes the branch covered by
     * {@link #colonVerbPath_isRewritten_whenVersionHeaderPresent()}.
     */
    @Test
    void colonVerbPath_isNotRewritten_withoutVersionHeader() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));

        assertEquals("/message:send", routedPath(filter, get("/message:send")));
    }

    @Test
    void colonVerbPath_isRewritten_whenVersionHeaderPresent() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));
        MockHttpServletRequest request = get("/message:send");
        request.addHeader(A2AHeaders.A2A_VERSION, "1.0");

        assertEquals("/a2a_rest_v1.0/message:send", routedPath(filter, request));
    }

    @Test
    void rootProvider_rewritesColonSuffixedPrefixExactly() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));

        assertEquals("/a2a_rest_v1.0/message:", routedPath(filter, get("/message:")));
    }

    @Test
    void rootProvider_ignoresUnknownPrefixWithoutVersionHeader() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));

        assertEquals("/some/other/resource", routedPath(filter, get("/some/other/resource")));
    }

    @Test
    void basePathProvider_rewritesMatchingBasePath() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(
                List.of(basePathProvider("0.3", true, "/v1")));

        assertEquals("/a2a_rest_v0.3/v1/tasks/abc", routedPath(filter, get("/v1/tasks/abc")));
    }

    @Test
    void explicitVersionHeader_selectsThatProvider() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(
                List.of(rootProvider("1.0", false), basePathProvider("0.3", true, "/v1")));
        MockHttpServletRequest request = get("/tasks/abc");
        request.addHeader(A2AHeaders.A2A_VERSION, "1.0");

        assertEquals("/a2a_rest_v1.0/tasks/abc", routedPath(filter, request));
    }

    @Test
    void wellKnownPath_isNeverRewritten() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));

        assertEquals("/.well-known/agent-card.json", routedPath(filter, get("/.well-known/agent-card.json")));
    }

    @Test
    void alreadyInternalPath_isNeverRewritten() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));

        assertEquals("/a2a_rest_v1.0/tasks/abc", routedPath(filter, get("/a2a_rest_v1.0/tasks/abc")));
    }

    @Test
    void jsonRpcOnlyProvider_isIgnored() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(
                List.of(TestProviders.jsonRpcProvider("1.0", true)));

        assertEquals("/tasks/abc", routedPath(filter, get("/tasks/abc")));
    }

    @Test
    void unsupportedVersion_returns400() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));
        MockHttpServletRequest request = get("/tasks/abc");
        request.addHeader(A2AHeaders.A2A_VERSION, "99.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = dispatch(filter, request, response);

        assertEquals(400, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":-32001"), body);
        assertTrue(body.contains("Protocol version '99.0' is not supported"), body);
        assertNull(chain.getRequest(), "chain must not be invoked for an unsupported version");
    }

    @Test
    void errorResponse_containsEscapedVersionHeader() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));
        MockHttpServletRequest request = get("/tasks/abc");
        request.addHeader(A2AHeaders.A2A_VERSION, "1.0\"version\"");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatch(filter, request, response);

        assertEquals(400, response.getStatus());
        String body = response.getContentAsString();
        assertFalse(body.contains("\"version\""), body);
        assertTrue(body.contains("\\\""), body);
    }

    @Test
    void multipleProviders_noDefault_nullVersionHeader_isSkipped() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(
                rootProvider("1.0", false),
                basePathProvider("0.3", false, "/v1")));

        assertEquals("/unknown-path", routedPath(filter, get("/unknown-path")));
    }

    @Test
    void longerBasePathWins_whenBasePathsOverlap() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(
                basePathProvider("0.3", false, "/v1"),
                basePathProvider("1.0", true, "/v1/beta")));
        MockHttpServletRequest request = get("/v1/beta/tasks/abc");

        assertEquals("/a2a_rest_v1.0/v1/beta/tasks/abc", routedPath(filter, request));
    }

    @Test
    void rewrite_preservesOriginalRequestUriForTenantExtraction() throws Exception {
        A2ARestVersionRoutingFilter filter = new A2ARestVersionRoutingFilter(List.of(rootProvider("1.0", true)));
        MockFilterChain chain = dispatch(filter, get("/tasks/abc"), new MockHttpServletResponse());

        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        assertEquals("/tasks/abc", PathRewriteHttpServletRequest.originalRequestUri(downstream));
    }
}
