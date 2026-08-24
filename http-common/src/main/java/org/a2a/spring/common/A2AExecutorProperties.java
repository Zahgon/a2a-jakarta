/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the A2A worker pool that replaces the Jakarta EE {@code ManagedExecutorService}.
 *
 * <p>In the Jakarta build the pool was owned and sized by the WildFly {@code ee} subsystem, so there was
 * nothing to configure in the application. On Spring Boot the pool belongs to the application, so it is
 * exposed under the {@code a2a.executor} prefix.
 */
@ConfigurationProperties(prefix = "a2a.executor")
public class A2AExecutorProperties {

    private int corePoolSize = 8;
    private int maxPoolSize = 64;
    private int queueCapacity = 1024;
    private String threadNamePrefix = "a2a-async-";
    private int awaitTerminationSeconds = 30;

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public int getAwaitTerminationSeconds() {
        return awaitTerminationSeconds;
    }

    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }
}
