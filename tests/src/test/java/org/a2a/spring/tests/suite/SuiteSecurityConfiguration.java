/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * BASIC authentication for the authenticated suites — the Spring replacement for the source's
 * Elytron wiring.
 *
 * <p>The source needed three pieces to secure an Arquillian deployment: {@code ElytronSetupTask}
 * added a filesystem realm and security domain to the running WildFly, {@code web-auth.xml}
 * declared a BASIC login-config with a security constraint over the deployment, and
 * {@code jboss-web-auth.xml} bound the deployment to the domain. All three collapse into the
 * filter chain below.
 *
 * <p>The identities match the SDK suites exactly: {@code testuser}/{@code testpass} is
 * {@code AbstractA2AServerWithAuthTest}'s {@code TEST_USERNAME}/{@code TEST_PASSWORD} and also
 * {@code USER_A} in the task-authorization suite; {@code userB}/{@code passB} is that suite's
 * second identity, needed so ownership checks have a non-owner to reject.
 *
 * <p>Authenticating puts a {@code Principal} on the request, which is what the migrated
 * {@code A2AServerResourceDelegate.createCallContext} turns into the SDK's {@code User}. That is
 * the seam the task-authorization tests actually exercise — an unauthenticated request yields
 * {@code UnauthenticatedUser} and ownership checks fail closed.
 *
 * <p>{@code @EnableWebSecurity} is declared rather than left to Boot's auto-configuration,
 * because {@link SuiteApplication} excludes that auto-configuration to keep the unauthenticated
 * suites unsecured. Relying on it here would tie this context's security to a switch thrown for
 * a different context.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Profile(SuiteSecurityConfiguration.SECURED_PROFILE)
public class SuiteSecurityConfiguration {

    /**
     * Activated only by the authenticated suites.
     *
     * <p>A profile rather than a component-scan exclusion, because this class sits in the same
     * package as the application classes and Spring Boot scans a {@code @SpringBootApplication}'s
     * own package implicitly. That implicit scan carries none of the explicit
     * {@code @ComponentScan} filters, so an exclusion there does not keep this configuration out of
     * the unsecured context — it would be registered and would secure suites that must run open.
     * Gating on a profile makes the bean definition inert unless a suite asks for it.
     */
    public static final String SECURED_PROFILE = "secured";

    /**
     * A direct transcription of the source's {@code web-auth.xml}, which declared three
     * constraints in this order:
     *
     * <pre>
     *   /.well-known/agent-card.json   no auth-constraint  -> unchecked
     *   /test/*                        no auth-constraint  -> unchecked
     *   /*                             auth-constraint user -> BASIC, role "user"
     * </pre>
     *
     * <p>The agent card is public because the A2A handshake requires it: a client reads the card
     * to learn which security schemes to satisfy, so gating it would be circular.
     * {@code testGetAgentCardIsPublic} pins that.
     *
     * <p>{@code /test/**} is public because it is the out-of-band control surface the suites use
     * to seed server state. {@code AbstractA2AServerWithAuthTest.saveTaskInTaskStore} calls it
     * without credentials while setting up the very tests that assert the A2A endpoints reject
     * anonymous callers — securing it would make those tests fail during setup, before reaching
     * their assertion.
     *
     * <p>The role is spelled {@code USER} because Spring prefixes and upper-cases role names;
     * it is the source's {@code user} role.
     *
     * <p>CSRF is off and sessions are stateless: these are non-browser API calls presenting BASIC
     * credentials on every request, and the source had neither protection to reproduce.
     */
    @Bean
    public SecurityFilterChain suiteFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/.well-known/**").permitAll()
                        .requestMatchers("/test/**").permitAll()
                        .anyRequest().hasRole("USER"))
                .httpBasic(basic -> {
                })
                .build();
    }

    @Bean
    public UserDetailsService suiteUsers(PasswordEncoder passwordEncoder) {
        UserDetails testuser = User.withUsername("testuser")
                .password(passwordEncoder.encode("testpass"))
                .roles("USER")
                .build();
        UserDetails userB = User.withUsername("userB")
                .password(passwordEncoder.encode("passB"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(testuser, userB);
    }

    /**
     * Plain-text matching, because the source's Elytron filesystem realm stored these test
     * credentials in the clear too. Hashing here would buy nothing — the passwords are literals in
     * the SDK's test classes — and would only obscure the comparison with the source.
     */
    @SuppressWarnings("deprecation")
    @Bean
    public PasswordEncoder suitePasswordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
