/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerTest;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The HTTP+JSON/REST v1.0 suite, ported from {@code tests/rest}'s {@code JakartaA2AServerTest}.
 *
 * <p>Identical to the JSON-RPC suite bar the transport: the same {@link AbstractA2AServerTest}
 * assertions run against the REST controllers instead of the JSON-RPC resource. That is the point
 * of the SDK's shared suite — the wire contract is asserted once and each transport must satisfy
 * it — and it carries over to Spring unchanged.
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
class RestA2AServerTest extends AbstractA2AServerTest {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    RestA2AServerTest() {
        super(SuiteServerPort.PORT);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }

    @Override
    protected String getTransportUrl() {
        return SuiteServerPort.baseUrl();
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(RestTransport.class, new RestTransportConfigBuilder());
    }
}
