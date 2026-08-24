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

/**
 * {@link Compat03SuiteApplication} with BASIC authentication.
 *
 * <p>Same v0.3-only scan as the unsecured compatibility application; see that class for why the
 * v1.0 transports are filtered out. {@link SuiteSecurityConfiguration} is pulled in explicitly
 * because it is profile-gated and excluded from component scanning.
 */
@SpringBootApplication(
        exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@ComponentScan(
        basePackages = "org.a2a.spring",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "org\\.a2a\\.spring\\.(jsonrpc|rest)\\.[^.]*"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = {TestAgentApplication.class, SuiteSecurityConfiguration.class})
        })
@Import({SdkTestKitConfiguration.class, Compat03TestKitConfiguration.class, A2ATestController.class,
        SuiteSecurityConfiguration.class})
public class Compat03AuthSuiteApplication {
}
