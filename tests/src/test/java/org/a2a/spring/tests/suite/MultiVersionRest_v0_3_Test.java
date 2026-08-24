/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.compat03.client.ClientBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.rest.RestTransport_v0_3;
import org.a2aproject.sdk.compat03.client.transport.rest.RestTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerServerTest_v0_3;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The v0.3 Rest suite run against a deployment that also serves v1.0, ported from
 * {@code tests/multiversion/rest}'s {@code MultiVersion_v0_3_RestTest}.
 *
 * <p>The mirror of {@link MultiVersionRestTest}: a v0.3 client must keep working while the newer
 * controllers are mounted alongside. This is the direction that actually exercises
 * {@code A2ARestVersionRoutingFilter} and the {@code A2A_VERSION} header, since without them the
 * request would land on the v1.0 handler.
 */
@SpringBootTest(classes = MultiVersionSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "server.port=" + SuiteServerPort.PORT_STRING)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiVersionRest_v0_3_Test extends AbstractA2AServerServerTest_v0_3 {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    MultiVersionRest_v0_3_Test() {
        super(SuiteServerPort.PORT);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol_v0_3.HTTP_JSON.asString();
    }

    @Override
    protected String getTransportUrl() {
        return SuiteServerPort.baseUrl();
    }

    @Override
    protected void configureTransport(ClientBuilder_v0_3 builder) {
        builder.withTransport(RestTransport_v0_3.class, new RestTransportConfigBuilder_v0_3());
    }
}
