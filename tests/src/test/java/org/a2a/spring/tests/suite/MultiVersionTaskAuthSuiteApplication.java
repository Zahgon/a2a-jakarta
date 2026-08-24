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
 * Both protocol versions in one deployment, with authentication and ownership checks.
 *
 * <p>This is the configuration the multiversion suites exist to exercise, and the only one where
 * the project's reason for being — CDI-discovered version routing, now Spring-discovered — is
 * actually under load. {@link SuiteApplication} filters the {@code compat03} controllers out and
 * {@link Compat03SuiteApplication} filters the v1.0 ones out; this class filters neither, so
 * {@code A2AVersionResolver} sees a provider for each version and the routing filters have a real
 * choice to make on every request.
 *
 * <p>Both agent cards are declared for the same reason: {@code AgentCardRoutingFilter} resolves
 * {@code /.well-known/agent-card.json} to the highest version present, which is only a meaningful
 * decision when more than one is present.
 */
@SpringBootApplication(
        exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@ComponentScan(
        basePackages = "org.a2a.spring",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = {TestAgentApplication.class, SuiteSecurityConfiguration.class})
        })
@Import({SdkTestKitConfiguration.class, Compat03TestKitConfiguration.class, A2ATestController.class, SuiteSecurityConfiguration.class, TaskAuthTestKitConfiguration.class})
public class MultiVersionTaskAuthSuiteApplication {
}
