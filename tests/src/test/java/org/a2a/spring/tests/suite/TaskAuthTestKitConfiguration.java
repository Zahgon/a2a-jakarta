/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.server.apps.common.TestTaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Adds the per-task ownership provider that the task-authorization suites assert on.
 *
 * <p>Deliberately a separate configuration rather than part of {@link SdkTestKitConfiguration},
 * because its presence changes server behaviour for every other suite. The source made the same
 * split the other way round: its base deployments carried the class and then removed it by hand —
 *
 * <pre>
 *   archive.delete("/WEB-INF/classes/" + TestTaskAuthorizationProvider.class.getName()... );
 * </pre>
 *
 * with the comment that WildFly ignores the Quarkus {@code @IfBuildProperty} guard and would
 * always activate the bean, making unauthenticated requests fail with {@code TaskNotFoundError}.
 * Here nothing is deleted; the bean is simply only declared by the applications that want it.
 *
 * <p>{@code A2AServerAutoConfiguration.a2aTaskStore} takes an {@code ObjectProvider} of this type,
 * so declaring it is enough to put every task read and write behind an ownership check.
 */
@Configuration(proxyBeanMethods = false)
@Profile(TaskAuthTestKitConfiguration.TASK_AUTH_PROFILE)
public class TaskAuthTestKitConfiguration {

    /**
     * Activated only by the task-authorization suites.
     *
     * <p>Profile-gated for the same reason as {@link SuiteSecurityConfiguration}: this class sits
     * in the application classes' own package, which Spring Boot scans implicitly, and that
     * implicit scan ignores the explicit {@code @ComponentScan} filters. Without the gate the
     * provider is registered in every suite's context and ownership checks reject the base suites'
     * unauthenticated traffic with "Task not found" — which is exactly the failure mode the
     * source avoided by deleting the class from its base archives.
     */
    public static final String TASK_AUTH_PROFILE = "task-auth";

    @Bean
    public TaskAuthorizationProvider testTaskAuthorizationProvider() {
        return new TestTaskAuthorizationProvider();
    }
}
