/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.rest.compat03;

import java.io.IOException;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.a2aproject.sdk.compat03.transport.rest.handler.RestHandler_v0_3;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Protocol v0.3 REST endpoint. The class-level mapping combines the internal path prefix with the
 * v0.3 client-facing base path {@code /v1}, matching what {@code A2ARestVersionRoutingFilter} and
 * {@code AgentCardRoutingFilter} rewrite v0.3 traffic onto.
 *
 * <p>Two deliberate differences from the JAX-RS original:
 * <ul>
 *   <li>{@code @Consumes} is dropped from the {@code GET} and {@code DELETE} mappings. JAX-RS
 *       ignores it for bodiless requests, but Spring's {@code consumes} condition matches on the
 *       {@code Content-Type} header and would reject ordinary reads with 415.</li>
 *   <li>The colon verb segments ({@code message:send}, {@code tasks/{taskId}:cancel},
 *       {@code tasks/{taskId}:subscribe}) rely on {@code PathPatternParser} treating {@code :} as
 *       a literal character. This is unrelated to matrix variables, which use {@code ;}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/a2a_rest_v0.3/v1")
public class A2ARestServerResource_v0_3 {

    private final A2ARestServerResourceDelegate_v0_3 delegate;

    public A2ARestServerResource_v0_3(RestHandler_v0_3 restHandler) {
        this.delegate = new A2ARestServerResourceDelegate_v0_3(restHandler);
    }

    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAgentCard() {
        return delegate.getAgentCard();
    }

    @PostMapping(path = "/message:send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sendMessage(@RequestBody String body,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.sendMessage(body, httpRequest, principal);
    }

    @PostMapping(path = "/message:stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void sendMessageStreaming(@RequestBody String body, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse, Principal principal) throws IOException {
        delegate.sendMessageStreaming(body, httpRequest, httpResponse, principal);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<String> getTask(@PathVariable("taskId") String taskId,
            @RequestParam(name = "history_length", required = false) String historyLengthSnakeStr,
            @RequestParam(name = "historyLength", required = false) String historyLengthCamelStr,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.getTask(taskId, historyLengthSnakeStr, historyLengthCamelStr, httpRequest, principal);
    }

    @PostMapping(path = "/tasks/{taskId}:cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancelTask(@PathVariable("taskId") String taskId,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.cancelTask(taskId, httpRequest, principal);
    }

    @PostMapping(path = "/tasks/{taskId}:subscribe",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void resubscribeTask(@PathVariable("taskId") String taskId, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse, Principal principal) throws IOException {
        delegate.resubscribeTask(taskId, httpRequest, httpResponse, principal);
    }

    @GetMapping("/card")
    public ResponseEntity<String> getAuthenticatedExtendedCard(HttpServletRequest httpRequest,
            Principal principal) {
        return delegate.getAuthenticatedExtendedCard(httpRequest, principal);
    }

    @PostMapping(path = "/tasks/{taskId}/pushNotificationConfigs",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> setTaskPushNotificationConfiguration(
            @PathVariable("taskId") String taskId, @RequestBody String body,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.setTaskPushNotificationConfiguration(taskId, body, httpRequest, principal);
    }

    @GetMapping("/tasks/{taskId}/pushNotificationConfigs/{configId}")
    public ResponseEntity<String> getTaskPushNotificationConfiguration(
            @PathVariable("taskId") String taskId, @PathVariable("configId") String configId,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.getTaskPushNotificationConfiguration(taskId, configId, httpRequest, principal);
    }

    @GetMapping("/tasks/{taskId}/pushNotificationConfigs")
    public ResponseEntity<String> listTaskPushNotificationConfigurations(
            @PathVariable("taskId") String taskId, HttpServletRequest httpRequest,
            Principal principal) {
        return delegate.listTaskPushNotificationConfigurations(taskId, httpRequest, principal);
    }

    @DeleteMapping("/tasks/{taskId}/pushNotificationConfigs/{configId}")
    public ResponseEntity<String> deleteTaskPushNotificationConfiguration(
            @PathVariable("taskId") String taskId, @PathVariable("configId") String configId,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.deleteTaskPushNotificationConfiguration(taskId, configId, httpRequest, principal);
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2ARestServerResourceDelegate_v0_3.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
