/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.jsonrpc;

import java.io.IOException;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON-RPC transport endpoint for A2A protocol version 1.0.
 *
 * <p>Migrated from a JAX-RS {@code @Path}-annotated resource. As in the original, the mount path is
 * a compile-time constant because it cannot be parameterized, so each protocol version requires its
 * own controller class. Requests reach this internal path only after {@code A2AJsonRpcAcceptFilter}
 * rewrites the public {@code /} path to {@code /a2a_jsonrpc_v1.0}.
 *
 * <p>Both {@code POST} handlers are bound to the same path and are disambiguated purely by the
 * {@code produces} condition, which Spring MVC evaluates against the {@code Accept} header. This
 * preserves the JAX-RS content-negotiation behaviour exactly: {@code A2AJsonRpcAcceptFilter}
 * inspects the JSON-RPC method name in the request body and sets {@code Accept} to
 * {@code text/event-stream} for streaming methods or {@code application/json} otherwise.
 *
 * <p>The empty-and-slash path pair ({@code {"", "/"}}) is required because the routing filter
 * rewrites the public path {@code /} to {@code /a2a_jsonrpc_v1.0/} (with a trailing slash), and
 * Spring Framework 6 no longer matches trailing slashes implicitly the way JAX-RS did.
 */
@RestController
@RequestMapping("/a2a_jsonrpc_v1.0")
public class A2AServerResource {

    private final A2AServerResourceDelegate delegate;

    public A2AServerResource(JSONRPCHandler jsonRpcHandler) {
        this.delegate = new A2AServerResourceDelegate(jsonRpcHandler);
    }

    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAgentCard() {
        return delegate.getAgentCard();
    }

    @PostMapping(
            path = {"", "/"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleNonStreamingRequests(
            @RequestBody String body,
            HttpServletRequest httpRequest,
            Principal principal) {
        return delegate.handleNonStreamingRequests(body, httpRequest, principal);
    }

    @PostMapping(
            path = {"", "/"},
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
        A2AServerResourceDelegate.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
