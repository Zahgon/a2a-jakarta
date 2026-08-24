/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.AuthInterceptor;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerWithAuthTest;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * HTTP+JSON/REST under BASIC authentication, ported from {@code tests/rest}'s
 * {@code JakartaA2AServerWithAuthTest}.
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
class RestA2AServerWithAuthTest extends AbstractA2AServerWithAuthTest {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    RestA2AServerWithAuthTest() {
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

    @Override
    protected void configureTransportWithAuth(ClientBuilder builder) {
        AuthInterceptor authInterceptor = new AuthInterceptor(
                (schemeName, context) ->
                        BASIC_AUTH_SCHEME_NAME.equals(schemeName) ? getEncodedCredentials() : null);
        builder.withTransport(
                RestTransport.class,
                new RestTransportConfigBuilder().addInterceptor(authInterceptor));
    }
}
