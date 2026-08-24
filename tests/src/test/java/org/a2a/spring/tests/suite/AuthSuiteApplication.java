/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2a.spring.tests.TestAgentApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * {@link SuiteApplication} with BASIC authentication switched on.
 *
 * <p>A separate application class rather than a property on the existing one, because Spring's
 * test context cache keys on the configuration classes: the secured and unsecured suites need two
 * distinct contexts and must not share a running server. The source drew the same line by building
 * a separate {@code ROOT.war} for the {@code *WithAuthTest} classes, one that added
 * {@code web-auth.xml} and the Elytron setup task.
 *
 * <p>The scan configuration is spelled out again rather than inherited from
 * {@link SuiteApplication}. Extending it does not carry the {@code @ComponentScan} exclusions
 * across, and losing them lets the scan reach {@code TestAgentApplication}, whose competing
 * {@code publicAgentCard} definition aborts context startup.
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "org.a2a.spring",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.a2a\\.spring\\..*\\.compat03\\..*"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TestAgentApplication.class)
        })
@Import({SdkTestKitConfiguration.class, A2ATestController.class, SuiteSecurityConfiguration.class})
public class AuthSuiteApplication {
}
