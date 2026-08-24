/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.util.concurrent.Executor;

import org.a2aproject.sdk.server.util.async.Internal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Supplies the {@link Internal} {@link Executor} the A2A SDK uses to run agent work off the request thread.
 *
 * <p>Migrated from {@code AsyncManagedExecutorServiceProducer}, which was a CDI
 * {@code @Alternative @Priority(20)} producer wrapping a container {@code ManagedExecutorService} and
 * activating a CDI request context on each worker thread via {@code RequestContextController}.
 *
 * <p>Spring equivalent:
 * <ul>
 *   <li>{@code @Resource ManagedExecutorService} &rarr; a {@link ThreadPoolTaskExecutor} bean, configurable
 *       through the {@code a2a.executor.*} properties.</li>
 *   <li>{@code RequestContextController.activate()/deactivate()} &rarr; propagation of the submitting thread's
 *       {@link RequestAttributes}. Spring has no API to activate an empty request scope, so instead of
 *       fabricating one we carry the caller's scope across the thread boundary. This is what
 *       request-scoped beans actually need, and worker threads with no originating request (for example gRPC
 *       executor threads) simply run without a request scope, exactly as before.</li>
 *   <li>{@code @Alternative @Priority(20)} &rarr; {@link ConditionalOnMissingBean}, so an application can
 *       replace the executor by declaring its own {@code @Internal Executor} bean.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(A2AExecutorProperties.class)
public class A2AAsyncExecutorConfiguration {

    @Bean(name = "a2aTaskExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "a2aTaskExecutor")
    public ThreadPoolTaskExecutor a2aTaskExecutor(A2AExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    @Bean
    @Internal
    @ConditionalOnMissingBean(annotation = Internal.class)
    public Executor a2aInternalExecutor(@Qualifier("a2aTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        return runnable -> {
            RequestAttributes callerAttributes = currentRequestAttributes();
            taskExecutor.execute(() -> {
                if (callerAttributes == null) {
                    runnable.run();
                    return;
                }
                RequestContextHolder.setRequestAttributes(callerAttributes, true);
                try {
                    runnable.run();
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
            });
        };
    }

    private static RequestAttributes currentRequestAttributes() {
        try {
            return RequestContextHolder.getRequestAttributes();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
