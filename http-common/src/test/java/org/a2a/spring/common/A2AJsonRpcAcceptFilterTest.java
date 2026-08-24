/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.common.A2AHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2AJsonRpcAcceptFilterTest {

    private static final String STREAMING_METHOD = "message/stream";
    private static final String NON_STREAMING_METHOD = "message/send";

    private static final String STREAMING_BODY =
            "{\"jsonrpc\":\"2.0\",\"method\":\"" + STREAMING_METHOD + "\",\"id\":1}";
    private static final String NON_STREAMING_BODY =
            "{\"jsonrpc\":\"2.0\",\"method\":\"" + NON_STREAMING_METHOD + "\",\"id\":1}";

    private static A2AJsonRpcAcceptFilter filter(A2AVersionProvider... providers) {
        return new A2AJsonRpcAcceptFilter(
                List.of(providers),
                List.of(TestProviders.methodProvider(Set.of(STREAMING_METHOD), Set.of(NON_STREAMING_METHOD))));
    }

    private static MockHttpServletRequest post(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        return request;
    }

    private static MockFilterChain dispatch(A2AJsonRpcAcceptFilter filter, MockHttpServletRequest request,
            MockHttpServletResponse response) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain;
    }

    @Test
    void streamingMethod_setsServerSentEventsAccept() throws Exception {
        MockFilterChain chain = dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)),
                post("/", STREAMING_BODY), new MockHttpServletResponse());

        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        assertEquals(MediaType.TEXT_EVENT_STREAM_VALUE, downstream.getHeader(HttpHeaders.ACCEPT));
    }

    @Test
    void nonStreamingMethod_setsJsonAccept() throws Exception {
        MockFilterChain chain = dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)),
                post("/", NON_STREAMING_BODY), new MockHttpServletResponse());

        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        assertEquals(MediaType.APPLICATION_JSON_VALUE, downstream.getHeader(HttpHeaders.ACCEPT));
    }

    @Test
    void requestBody_isStillReadableDownstream() throws Exception {
        MockFilterChain chain = dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)),
                post("/", NON_STREAMING_BODY), new MockHttpServletResponse());

        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        String body = new String(downstream.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(NON_STREAMING_BODY, body);
    }

    @Test
    void defaultVersion_rewritesToInternalPrefix() throws Exception {
        MockFilterChain chain = dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)),
                post("/", NON_STREAMING_BODY), new MockHttpServletResponse());

        assertEquals("/a2a_jsonrpc_v1.0/", ((HttpServletRequest) chain.getRequest()).getRequestURI());
    }

    @Test
    void explicitVersionHeader_selectsThatProvider() throws Exception {
        MockHttpServletRequest request = post("/", NON_STREAMING_BODY);
        request.addHeader(A2AHeaders.A2A_VERSION, "0.3");

        MockFilterChain chain = dispatch(
                filter(TestProviders.jsonRpcProvider("1.0", false), TestProviders.jsonRpcProvider("0.3", true)),
                request, new MockHttpServletResponse());

        assertEquals("/a2a_jsonrpc_v0.3/", ((HttpServletRequest) chain.getRequest()).getRequestURI());
    }

    @Test
    void unsupportedVersion_returnsJsonRpcErrorWithRequestId() throws Exception {
        MockHttpServletRequest request = post("/", NON_STREAMING_BODY);
        request.addHeader(A2AHeaders.A2A_VERSION, "99.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)), request, response);

        assertEquals(400, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":-32009"), body);
        assertTrue(body.contains("Protocol version '99.0' is not supported"), body);
        assertTrue(body.contains("Supported versions: 1.0"), body);
        assertTrue(body.contains("\"id\":1"), body);
        assertNull(chain.getRequest(), "chain must not be invoked for an unsupported version");
    }

    @Test
    void unsupportedVersion_withStringId_preservesQuotedId() throws Exception {
        MockHttpServletRequest request = post("/",
                "{\"jsonrpc\":\"2.0\",\"method\":\"" + NON_STREAMING_METHOD + "\",\"id\":\"abc\"}");
        request.addHeader(A2AHeaders.A2A_VERSION, "99.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)), request, response);

        assertTrue(response.getContentAsString().contains("\"id\":\"abc\""), response.getContentAsString());
    }

    @Test
    void errorResponse_containsEscapedVersionHeader() throws Exception {
        MockHttpServletRequest request = post("/", NON_STREAMING_BODY);
        request.addHeader(A2AHeaders.A2A_VERSION, "1.0\"version\"");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)), request, response);

        String body = response.getContentAsString();
        assertFalse(body.contains("\"version\""), body);
        assertTrue(body.contains("\\\""), body);
    }

    @Test
    void multipleProviders_noDefault_unknownVersion_abortsWithError() throws Exception {
        MockHttpServletRequest request = post("/", NON_STREAMING_BODY);
        request.addHeader(A2AHeaders.A2A_VERSION, "99.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = dispatch(
                filter(TestProviders.jsonRpcProvider("1.0", false), TestProviders.jsonRpcProvider("2.0", false)),
                request, response);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":-32009"), response.getContentAsString());
        assertNull(chain.getRequest(), "chain must not be invoked when no provider matches");
    }

    @Test
    void multipleProviders_noDefault_nullVersionHeader_abortsWithError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = dispatch(
                filter(TestProviders.jsonRpcProvider("1.0", false), TestProviders.jsonRpcProvider("2.0", false)),
                post("/", NON_STREAMING_BODY), response);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":-32009"), response.getContentAsString());
        assertNull(chain.getRequest(), "chain must not be invoked when no default provider exists");
    }

    @Test
    void nonRootPath_isNotTouched() throws Exception {
        MockFilterChain chain = dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)),
                post("/tasks/abc", NON_STREAMING_BODY), new MockHttpServletResponse());

        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        assertEquals("/tasks/abc", downstream.getRequestURI());
        assertNull(downstream.getHeader(HttpHeaders.ACCEPT));
    }

    @Test
    void getRequest_isNotTouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServletPath("/");

        MockFilterChain chain = dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)),
                request, new MockHttpServletResponse());

        assertEquals("/", ((HttpServletRequest) chain.getRequest()).getRequestURI());
    }

    @Test
    void emptyBody_isNotTouched() throws Exception {
        MockFilterChain chain = dispatch(filter(TestProviders.jsonRpcProvider("1.0", true)),
                post("/", ""), new MockHttpServletResponse());

        assertEquals("/", ((HttpServletRequest) chain.getRequest()).getRequestURI());
    }

    @Test
    void noJsonRpcProviders_isNotTouched() throws Exception {
        A2AJsonRpcAcceptFilter restOnly = new A2AJsonRpcAcceptFilter(
                List.of(TestProviders.provider("1.0", true, "/a2a_rest_v1.0", "/")),
                List.of(TestProviders.methodProvider(Set.of(STREAMING_METHOD), Set.of(NON_STREAMING_METHOD))));

        MockFilterChain chain = dispatch(restOnly, post("/", NON_STREAMING_BODY), new MockHttpServletResponse());

        assertEquals("/", ((HttpServletRequest) chain.getRequest()).getRequestURI());
    }
}
