/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.rest;

import java.io.IOException;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.a2aproject.sdk.transport.rest.handler.RestHandler;
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
 * Spring MVC front end for the A2A REST (HTTP+JSON) transport, protocol version 1.0.
 *
 * <p>Migrated from the JAX-RS {@code @Path("/a2a_rest_v1.0")} resource. As in the source,
 * one class is required per protocol version because the mount prefix is a compile-time
 * annotation value that cannot be parameterized.
 *
 * <p>Clients never call these paths directly. They call the public REST surface
 * ({@code /tasks}, {@code /message:send}, ...) and {@code A2ARestVersionRoutingFilter}
 * rewrites the request path to the internal, version-qualified prefix this controller is
 * mounted on.
 *
 * <p><strong>Colon verb segments.</strong> Several paths use the AIP-136 custom-method
 * form, where a colon separates the resource from the verb: {@code message:send},
 * {@code tasks/{taskId}:cancel}. Spring's {@code PathPatternParser} treats {@code :} as an
 * ordinary literal character, and compiles a segment that mixes a capture with a literal
 * suffix into a regex path element, so {@code tasks/{taskId}:cancel} binds {@code taskId}
 * exactly as the JAX-RS template did. Note this is distinct from matrix variables, which
 * use {@code ;} and are stripped by {@code UrlPathHelper}; colons are left intact.
 *
 * <p><strong>{@code @Consumes} on bodiless methods.</strong> The source annotated its GET
 * and DELETE methods {@code @Consumes(APPLICATION_JSON)}. JAX-RS ignores that for requests
 * that carry no entity, so it never had any effect. Spring's {@code consumes} condition is
 * matched against the {@code Content-Type} header and would reject ordinary GET/DELETE
 * requests with 415 Unsupported Media Type, so it is deliberately omitted here. Behaviour
 * is unchanged; a latent no-op is simply not carried over.
 */
@RestController
@RequestMapping("/a2a_rest_v1.0")
public class A2ARestServerResource {

    private final A2ARestServerResourceDelegate delegate;

    public A2ARestServerResource(RestHandler restHandler) {
        this.delegate = new A2ARestServerResourceDelegate(restHandler);
    }

    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAgentCard() {
        return delegate.getAgentCard();
    }

    @PostMapping(path = "/message:send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sendMessage(@RequestBody String body, HttpServletRequest httpRequest,
            Principal principal) {
        return delegate.sendMessage(body, httpRequest, principal);
    }

    @PostMapping(path = "/message:stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void sendMessageStreaming(@RequestBody String body, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse, Principal principal) throws IOException {
        delegate.sendMessageStreaming(body, httpRequest, httpResponse, principal);
    }

    @PostMapping(path = "/tasks/{taskId}:subscribe", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void resubscribeTask(@PathVariable("taskId") String taskId, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse, Principal principal) throws IOException {
        delegate.resubscribeTask(taskId, httpRequest, httpResponse, principal);
    }

    @GetMapping(path = "/card")
    public ResponseEntity<String> getAuthenticatedExtendedCard(HttpServletRequest httpRequest, Principal principal) {
        return delegate.getAuthenticatedExtendedCard(httpRequest, principal);
    }

    @GetMapping(path = "/extendedAgentCard")
    public ResponseEntity<String> getExtendedAgentCard(HttpServletRequest httpRequest, Principal principal) {
        return delegate.getExtendedAgentCard(httpRequest, principal);
    }

    @GetMapping(path = "/tasks")
    public ResponseEntity<String> listTasks(HttpServletRequest httpRequest, Principal principal) {
        return delegate.listTasks(httpRequest, principal);
    }

    @GetMapping(path = "/tasks/{taskId}")
    public ResponseEntity<String> getTask(@PathVariable("taskId") String taskId,
            @RequestParam(name = "historyLength", required = false) String historyLengthStr,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.getTask(taskId, historyLengthStr, httpRequest, principal);
    }

    @PostMapping(path = "/tasks/{taskId}:cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancelTask(@PathVariable("taskId") String taskId, @RequestBody String body,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.cancelTask(taskId, body, httpRequest, principal);
    }

    @PostMapping(path = "/tasks/{taskId}/pushNotificationConfigs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> setTaskPushNotificationConfiguration(@PathVariable("taskId") String taskId,
            @RequestBody String body, HttpServletRequest httpRequest, Principal principal) {
        return delegate.setTaskPushNotificationConfiguration(taskId, body, httpRequest, principal);
    }

    @GetMapping(path = "/tasks/{taskId}/pushNotificationConfigs/{configId}")
    public ResponseEntity<String> getTaskPushNotificationConfiguration(@PathVariable("taskId") String taskId,
            @PathVariable("configId") String configId, HttpServletRequest httpRequest, Principal principal) {
        return delegate.getTaskPushNotificationConfiguration(taskId, configId, httpRequest, principal);
    }

    @GetMapping(path = "/tasks/{taskId}/pushNotificationConfigs")
    public ResponseEntity<String> getOrListTaskPushNotificationConfigurations(@PathVariable("taskId") String taskId,
            HttpServletRequest httpRequest, Principal principal) {
        return delegate.getOrListTaskPushNotificationConfigurations(taskId, httpRequest, principal);
    }

    @DeleteMapping(path = "/tasks/{taskId}/pushNotificationConfigs/{configId}")
    public ResponseEntity<String> deleteTaskPushNotificationConfiguration(@PathVariable("taskId") String taskId,
            @PathVariable("configId") String configId, HttpServletRequest httpRequest, Principal principal) {
        return delegate.deleteTaskPushNotificationConfiguration(taskId, configId, httpRequest, principal);
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2ARestServerResourceDelegate.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }
}
