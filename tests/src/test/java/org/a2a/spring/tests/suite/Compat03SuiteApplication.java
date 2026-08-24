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
 * A v0.3-only deployment, matching what the source's {@code tests/compat-0.3} modules packaged.
 *
 * <p>Those modules built a {@code ROOT.war} containing the compatibility transports and a v0.3
 * agent card, and nothing from v1.0. That exclusivity matters: {@code AgentCardRoutingFilter}
 * routes {@code /.well-known/agent-card.json} to the highest version present, so a deployment
 * carrying both would serve the v1.0 card and the v0.3 suites would resolve the wrong interface
 * when they fetch it.
 *
 * <p>Hence the regex below. {@code org\.a2a\.spring\.(jsonrpc|rest)\.[^.]*} matches classes
 * directly in the v1.0 transport packages while leaving their {@code .compat03} subpackages
 * scanned — the compatibility controllers live one level deeper, so a broader pattern would take
 * them out along with the v1.0 ones.
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
@Import({SdkTestKitConfiguration.class, Compat03TestKitConfiguration.class, A2ATestController.class})
public class Compat03SuiteApplication {
}
