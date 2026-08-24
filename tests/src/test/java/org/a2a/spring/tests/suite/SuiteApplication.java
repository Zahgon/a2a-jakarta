/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.a2a.spring.tests.TestAgentApplication;
import org.springframework.context.annotation.Import;

/**
 * The deployment unit for the ported suites — what {@code createTestArchive()} was in the source.
 *
 * <p>Each Arquillian module built a {@code ROOT.war} by hand, listing every SDK jar it needed with
 * {@code getJarForClass(...)} because the container had no view of the Maven graph. Spring Boot
 * takes the test classpath as-is, so that list collapses to the module's {@code pom.xml} and this
 * class only has to name what to scan.
 *
 * <p>The v0.3 compatibility controllers are excluded for the same reason
 * {@code TestAgentApplication} excludes them: they require a {@code publicAgentCard_v0_3} bean that
 * the v1.0 suites do not declare, and component-scanning them without it fails context startup.
 * The v0.3 suites supply that bean themselves and scan without this filter.
 *
 * <p>Spring Boot's security auto-configuration is excluded. The authenticated suites put
 * spring-boot-starter-security on the shared test classpath, and Boot would otherwise secure
 * every context that sees it — including this one, silently turning the unauthenticated suites
 * into authenticated ones and failing them on 401. The source had no such coupling: security
 * lived in a web-auth.xml that only the secured WARs packaged.
 *
 * <p>{@link SuiteSecurityConfiguration} is excluded from the scan for the same reason. It sits in
 * this package, which is under the scanned {@code org.a2a.spring} root, so scanning would apply
 * the secured filter chain here and defeat the auto-configuration exclusion above. Only
 * {@link AuthSuiteApplication} pulls it in, and it does so explicitly with {@code @Import}.
 *
 * <p>{@code scanBasePackages} is deliberately absent from {@code @SpringBootApplication}. Setting
 * it there declares a second component scan alongside the explicit {@code @ComponentScan} below,
 * and that second scan carries only Boot's default filters — so the exclusions here would not
 * apply to it and the excluded classes would be picked up anyway.
 *
 * <p>{@code TestAgentApplication} is excluded too. It is a sibling test application with its own
 * {@code publicAgentCard} and a hand-written echo executor, and the scan of {@code org.a2a.spring}
 * reaches it. Left in, its bean definitions collide with the test-kit's and the context fails to
 * start. The two applications are deliberately kept apart: that one pins the hand-written
 * end-to-end tests, this one hosts the SDK suites.
 */
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
@Import({SdkTestKitConfiguration.class, A2ATestController.class, GrpcTestKitConfiguration.class})
public class SuiteApplication {
}
