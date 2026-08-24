/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2a.spring.tests.TestAgentApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/** v1.0 transports with BASIC authentication and per-task ownership checks. */
@SpringBootApplication(
        exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@ComponentScan(
        basePackages = "org.a2a.spring",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.a2a\\.spring\\..*\\.compat03\\..*"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = {TestAgentApplication.class, SuiteSecurityConfiguration.class})
        })
@Import({SdkTestKitConfiguration.class, A2ATestController.class, SuiteSecurityConfiguration.class,
        TaskAuthTestKitConfiguration.class})
public class TaskAuthSuiteApplication {
}
