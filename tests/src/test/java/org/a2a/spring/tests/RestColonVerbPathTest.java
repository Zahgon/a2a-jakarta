/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.method.HandlerMethod;

/**
 * Proves that Spring MVC's {@code PathPatternParser} resolves the A2A REST transport's
 * colon verb segments ({@code message:send}, {@code tasks/{taskId}:cancel}, ...) to the
 * correct controller methods.
 *
 * <p>This was the single largest open risk of the JAX-RS to Spring MVC migration: JAX-RS
 * {@code @Path} treats {@code :} as an ordinary literal, whereas Spring had to be confirmed
 * to do the same and to not confuse it with matrix variables (which use {@code ;}).
 *
 * <p>The assertion deliberately inspects {@link MvcResult#getHandler()} rather than the
 * response status. Status is not a valid proxy for "a handler matched": several of these
 * endpoints legitimately answer 404 TaskNotFound from the A2A SDK because the task id used
 * here does not exist, which is indistinguishable from a routing miss by status alone.
 */
@SpringBootTest(classes = TestAgentApplication.class)
@AutoConfigureMockMvc
class RestColonVerbPathTest {

    private static final String BASE = "/a2a_rest_v1.0";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void messageSendResolvesToSendMessage() throws Exception {
        assertRoutesTo(post(BASE + "/message:send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"), "sendMessage");
    }

    @Test
    void messageStreamResolvesToSendMessageStreaming() throws Exception {
        assertRoutesTo(post(BASE + "/message:stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{}"), "sendMessageStreaming");
    }

    @Test
    void taskCancelResolvesToCancelTask() throws Exception {
        assertRoutesTo(post(BASE + "/tasks/task-123:cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"), "cancelTask");
    }

    @Test
    void taskSubscribeResolvesToResubscribeTask() throws Exception {
        assertRoutesTo(post(BASE + "/tasks/task-123:subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{}"), "resubscribeTask");
    }

    @Test
    void plainTaskPathResolvesToGetTask() throws Exception {
        assertRoutesTo(get(BASE + "/tasks/task-123"), "getTask");
    }

    @Test
    void pushNotificationConfigPathResolvesToGetTaskPushNotificationConfiguration() throws Exception {
        assertRoutesTo(get(BASE + "/tasks/task-123/pushNotificationConfigs/config-1"),
                "getTaskPushNotificationConfiguration");
    }

    private void assertRoutesTo(RequestBuilder request, String expectedMethodName) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        Object handler = result.getHandler();
        assertNotNull(handler, "No handler matched the request; PathPatternParser failed to resolve the path");
        HandlerMethod handlerMethod = assertInstanceOf(HandlerMethod.class, handler);
        assertEquals(expectedMethodName, handlerMethod.getMethod().getName(),
                "Path resolved to the wrong controller method");
    }
}
