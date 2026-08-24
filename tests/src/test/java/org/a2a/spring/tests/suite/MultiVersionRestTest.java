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
 * The v1.0 Rest suite run against a deployment that also serves v0.3, ported from
 * {@code tests/multiversion/rest}'s {@code MultiVersionRestTest}.
 *
 * <p>Same assertions as {@link RestA2AServerTest}; the difference is entirely in what surrounds
 * the server. Passing here and there proves the v0.3 controllers being present changes nothing for
 * a v1.0 client — which is the whole claim of the version-routing layer.
 */
@SpringBootTest(classes = MultiVersionSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "server.port=" + SuiteServerPort.PORT_STRING)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiVersionRestTest extends AbstractA2AServerTest {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    MultiVersionRestTest() {
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
