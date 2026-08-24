/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.compat03.client.ClientBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.jsonrpc.JSONRPCTransport_v0_3;
import org.a2aproject.sdk.compat03.client.transport.jsonrpc.JSONRPCTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerServerTest_v0_3;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The protocol v0.3 JsonRpc suite, ported from {@code tests/compat-0.3/jsonrpc}.
 *
 * <p>Runs against {@link Compat03SuiteApplication}, a v0.3-only context, so the agent card the
 * suite fetches describes the compatibility transports rather than v1.0 — the same exclusivity the
 * source got by packaging a v0.3-only WAR.
 *
 * <p>{@code @DirtiesContext} releases the fixed port for the next suite; see
 * {@link JsonRpcA2AServerTest} for why every ported suite needs it.
 */
@SpringBootTest(classes = Compat03SuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "server.port=" + SuiteServerPort.PORT_STRING)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JsonRpcA2AServer_v0_3_Test extends AbstractA2AServerServerTest_v0_3 {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    JsonRpcA2AServer_v0_3_Test() {
        super(SuiteServerPort.PORT);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol_v0_3.JSONRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return SuiteServerPort.baseUrl();
    }

    @Override
    protected void configureTransport(ClientBuilder_v0_3 builder) {
        builder.withTransport(JSONRPCTransport_v0_3.class, new JSONRPCTransportConfigBuilder_v0_3());
    }
}
