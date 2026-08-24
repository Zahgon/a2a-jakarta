/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.examples.simple;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Entry point for the example server.
 *
 * <p>Replaces the JAX-RS {@code Application} subclass: {@code @ApplicationPath("/")} becomes Spring
 * Boot's default context path, and the transport controllers that JAX-RS discovered by scanning the
 * deployment are picked up by the component scan over {@code org.a2a.spring}. Which transports are
 * present is decided by the Maven profile that puts them on the classpath.
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "org.a2a.spring",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
        })
public class SimpleExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleExampleApplication.class, args);
    }
}
