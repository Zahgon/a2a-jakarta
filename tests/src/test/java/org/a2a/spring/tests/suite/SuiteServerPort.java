/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

/**
 * The one port the ported suites bind, and the plumbing that keeps the agent card agreeing with it.
 *
 * <p>{@code AgentCardProducer} in the SDK test-kit reads {@code test.agent.card.port} as a plain
 * system property, defaulting to 8081. Left alone it would advertise a URL nothing is listening on,
 * and every test that resolves the server through the agent card would fail with a connection
 * error rather than an assertion — so the property is set from {@link #PORT} before any context
 * starts.
 *
 * <p>8080 matches the source, whose Arquillian modules provisioned WildFly on the default HTTP
 * port and hardcoded {@code http://localhost:8080} in each suite's {@code getTransportUrl()}.
 */
final class SuiteServerPort {

    static final int PORT = 8080;

    static final String PORT_STRING = "8080";

    private SuiteServerPort() {
    }

    /**
     * Called from a static initialiser in each suite, which runs at class load — before Spring
     * builds the context and therefore before the {@code publicAgentCard} bean is produced.
     */
    static void publishToAgentCardProducer() {
        System.setProperty("test.agent.card.port", PORT_STRING);
    }

    static String baseUrl() {
        return "http://localhost:" + PORT;
    }
}
