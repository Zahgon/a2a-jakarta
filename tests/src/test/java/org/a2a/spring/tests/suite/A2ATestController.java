/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;

import org.a2a.spring.jsonrpc.A2AServerResource;
import org.a2a.spring.jsonrpc.compat03.A2AServerResource_v0_3;
import org.a2a.spring.rest.A2ARestServerResource;
import org.a2a.spring.rest.compat03.A2ARestServerResource_v0_3;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.apps.common.TestUtilsBean;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Out-of-band control surface for the ported suites — the Spring port of the source's
 * {@code A2ATestResource}, which each Arquillian module carried in its own package and added to
 * its WebArchive.
 *
 * <p>The abstract suites use this to seed and inspect server state directly (save a task, force a
 * queue, count child queues) so they can assert on paths the public A2A API cannot reach. The
 * route table, status codes and content types are preserved exactly; only the JAX-RS annotations
 * become their Spring MVC counterparts.
 *
 * <p>Bodies are taken and returned as {@code String} and run through the SDK's {@code JsonUtil},
 * matching the source. Letting Spring's {@code MappingJackson2HttpMessageConverter} bind these
 * types instead would serialise A2A spec objects with Jackson's defaults rather than the SDK's
 * configured Gson, which is a different wire shape.
 */
@RestController
@RequestMapping("/test")
public class A2ATestController {

    private final TestUtilsBean testUtilsBean;

    private final AtomicInteger streamingSubscribedCount = new AtomicInteger(0);

    public A2ATestController(TestUtilsBean testUtilsBean) {
        this.testUtilsBean = testUtilsBean;
    }

    /**
     * Same static hook the source installed. The counter it feeds backs
     * {@code /test/streamingSubscribedCount}, which the streaming tests poll to know a subscriber
     * has actually attached before they enqueue events.
     *
     * <p>Both transports are registered. The source could register one, because each Arquillian
     * module shipped its own copy of this resource in its own {@code ROOT.war} and knew which
     * transport it was testing. Here one controller serves every suite in a single module, so it
     * arms whichever transport the running suite happens to exercise, v1.0 and v0.3 alike.
     */
    @PostConstruct
    public void init() {
        A2AServerResource.setStreamingIsSubscribedRunnable(streamingSubscribedCount::incrementAndGet);
        A2ARestServerResource.setStreamingIsSubscribedRunnable(streamingSubscribedCount::incrementAndGet);
        A2AServerResource_v0_3.setStreamingIsSubscribedRunnable(streamingSubscribedCount::incrementAndGet);
        A2ARestServerResource_v0_3.setStreamingIsSubscribedRunnable(streamingSubscribedCount::incrementAndGet);
    }

    @PostMapping(path = "/task", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> saveTask(@RequestBody String body) throws Exception {
        testUtilsBean.saveTask(JsonUtil.fromJson(body, Task.class));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<String> getTask(@PathVariable String taskId) throws Exception {
        Task task = testUtilsBean.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(JsonUtil.toJson(task));
    }

    @DeleteMapping("/task/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable String taskId) {
        if (testUtilsBean.getTask(taskId) == null) {
            return ResponseEntity.notFound().build();
        }
        testUtilsBean.deleteTask(taskId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    @PostMapping("/queue/ensure/{taskId}")
    public ResponseEntity<Void> ensureQueue(@PathVariable String taskId) {
        testUtilsBean.ensureQueue(taskId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/queue/enqueueTaskStatusUpdateEvent/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> enqueueTaskStatusUpdateEvent(@PathVariable String taskId, @RequestBody String body)
            throws Exception {
        testUtilsBean.enqueueEvent(taskId, JsonUtil.fromJson(body, TaskStatusUpdateEvent.class));
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/queue/enqueueTaskArtifactUpdateEvent/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> enqueueTaskArtifactUpdateEvent(@PathVariable String taskId, @RequestBody String body)
            throws Exception {
        testUtilsBean.enqueueEvent(taskId, JsonUtil.fromJson(body, TaskArtifactUpdateEvent.class));
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/streamingSubscribedCount", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getStreamingSubscribedCount() {
        return ResponseEntity.ok(String.valueOf(streamingSubscribedCount.get()));
    }

    @GetMapping(path = "/queue/childCount/{taskId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getChildQueueCount(@PathVariable String taskId) {
        return ResponseEntity.ok(String.valueOf(testUtilsBean.getChildQueueCount(taskId)));
    }

    @DeleteMapping("/task/{taskId}/config/{configId}")
    public ResponseEntity<Void> deleteTaskPushNotificationConfig(
            @PathVariable String taskId, @PathVariable String configId) {
        if (testUtilsBean.getTask(taskId) == null) {
            return ResponseEntity.notFound().build();
        }
        testUtilsBean.deleteTaskPushNotificationConfig(taskId, configId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    @PostMapping(path = "/task/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> savePushNotificationConfigInStore(
            @PathVariable String taskId, @RequestBody String body) throws Exception {
        TaskPushNotificationConfig config = JsonUtil.fromJson(body, TaskPushNotificationConfig.class);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        testUtilsBean.saveTaskPushNotificationConfig(taskId, config);
        return ResponseEntity.ok().build();
    }

    @PostMapping(path = "/queue/awaitChildCountStable/{taskId}/{expectedCount}/{timeoutMs}",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> awaitChildQueueCountStable(
            @PathVariable String taskId,
            @PathVariable int expectedCount,
            @PathVariable long timeoutMs) throws InterruptedException {
        return ResponseEntity.ok(
                String.valueOf(testUtilsBean.awaitChildQueueCountStable(taskId, expectedCount, timeoutMs)));
    }
}
