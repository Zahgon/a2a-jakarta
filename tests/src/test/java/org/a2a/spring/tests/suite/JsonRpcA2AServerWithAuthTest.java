/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.AuthInterceptor;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerWithAuthTest;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * JSON-RPC under BASIC authentication, ported from {@code tests/jsonrpc}'s
 * {@code JakartaA2AServerWithAuthTest}.
 *
 * <p>{@code a2a.test.security-enabled} makes the advertised agent card declare the
 * {@code basicAuth} security scheme, matching the {@code securityEnabled} flag the source fed to
 * {@code AgentCardProducer} through MicroProfile config.
 *
 * <p>{@code @DirtiesContext} closes the context, and with it the embedded server, when the
 * class finishes. Every ported suite binds the same fixed port, so without it Spring's
 * test-context cache would hold one suite's server open while the next tried to bind and the
 * second context would fail with {@code PortInUseException}. The source never hit this: each
 * Arquillian module provisioned and tore down its own WildFly.
 */
@SpringBootTest(classes = AuthSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=" + SuiteServerPort.PORT_STRING,
                "a2a.test.security-enabled=true",
                "spring.profiles.active=" + SuiteSecurityConfiguration.SECURED_PROFILE})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JsonRpcA2AServerWithAuthTest extends AbstractA2AServerWithAuthTest {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    JsonRpcA2AServerWithAuthTest() {
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

    @Override
    protected void configureTransportWithAuth(ClientBuilder builder) {
        AuthInterceptor authInterceptor = new AuthInterceptor(
                (schemeName, context) ->
                        BASIC_AUTH_SCHEME_NAME.equals(schemeName) ? getEncodedCredentials() : null);
        builder.withTransport(
                JSONRPCTransport.class,
                new JSONRPCTransportConfigBuilder().addInterceptor(authInterceptor));
    }
}
