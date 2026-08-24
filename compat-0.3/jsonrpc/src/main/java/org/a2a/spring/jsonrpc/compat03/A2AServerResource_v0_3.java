/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.jsonrpc.compat03;

import java.io.IOException;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Protocol v0.3 JSON-RPC endpoint, mounted on the internal path that
 * {@code A2AJsonRpcAcceptFilter} rewrites v0.3 traffic onto.
 *
 * <p>The two {@code POST} handlers share a path and are separated only by {@code produces}, which
 * Spring MVC matches against the {@code Accept} header. That reproduces the JAX-RS content
 * negotiation of the original, and works because the accept filter sets {@code Accept} before
 * dispatch. The {@code {"", "/"}} path pair is required because the filter rewrites the public
 * root to {@code /a2a_jsonrpc_v0.3/}, and Spring Framework 6 no longer matches trailing slashes
 * implicitly.
 */
@RestController
@RequestMapping("/a2a_jsonrpc_v0.3")
public class A2AServerResource_v0_3 {

    private final A2AServerResourceDelegate_v0_3 delegate;

    public A2AServerResource_v0_3(JSONRPCHandler_v0_3 jsonRpcHandler) {
        this.delegate = new A2AServerResourceDelegate_v0_3(jsonRpcHandler);
    }

    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAgentCard() {
        return delegate.getAgentCard();
    }

    @PostMapping(path = {"", "/"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleNonStreamingRequests(
            @RequestBody String body,
            HttpServletRequest httpRequest,
            Principal principal) {
        return delegate.handleNonStreamingRequests(body, httpRequest, principal);
    }

    @PostMapping(path = {"", "/"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void handleStreamingRequests(
            @RequestBody String body,
            HttpServletResponse response,
            HttpServletRequest httpRequest,
            Principal principal) throws IOException {
        delegate.handleStreamingRequests(body, response, httpRequest, principal);
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2AServerResourceDelegate_v0_3.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
