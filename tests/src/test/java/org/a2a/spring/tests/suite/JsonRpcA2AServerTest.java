/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The JSON-RPC v1.0 suite, ported from {@code tests/jsonrpc}'s {@code JakartaA2AServerTest}.
 *
 * <p>The source ran this as {@code @ArquillianTest @RunAsClient} against a WildFly instance the
 * plugin provisioned. {@code @RunAsClient} is the detail that makes the port possible at all: the
 * assertions were already executing outside the container, driving the server over HTTP, so
 * nothing in {@link AbstractA2AServerTest} is bound to Jakarta. Only the deployment mechanism was,
 * and {@code webEnvironment = DEFINED_PORT} replaces it.
 *
 * <p>The port is fixed rather than random because {@link AbstractA2AServerTest} takes it as a
 * constructor argument and resolves {@link #getTransportUrl()} before Spring can inject
 * {@code @LocalServerPort}. The source made the same choice, hardcoding 8080.
 *
 * <p>{@code @DirtiesContext} closes the context, and with it the embedded server, when the
 * class finishes. Every ported suite binds the same fixed port, so without it Spring's
 * test-context cache would hold one suite's server open while the next tried to bind and the
 * second context would fail with {@code PortInUseException}. The source never hit this: each
 * Arquillian module provisioned and tore down its own WildFly.
 */
@SpringBootTest(classes = SuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "server.port=" + SuiteServerPort.PORT_STRING)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JsonRpcA2AServerTest extends AbstractA2AServerTest {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    JsonRpcA2AServerTest() {
        super(SuiteServerPort.PORT);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return SuiteServerPort.baseUrl();
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
    }
}
