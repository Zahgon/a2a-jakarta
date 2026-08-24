/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.compat03.client.ClientBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.rest.RestTransport_v0_3;
import org.a2aproject.sdk.compat03.client.transport.rest.RestTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.spi.interceptors.auth.AuthInterceptor_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerWithAuthTest_v0_3;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Protocol v0.3 Rest under BASIC authentication, ported from
 * {@code tests/compat-0.3/rest}'s {@code JakartaA2AServer_v0_3_WithAuth} suite.
 */
@SpringBootTest(classes = Compat03AuthSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=" + SuiteServerPort.PORT_STRING,
                "a2a.test.security-enabled=true",
                "spring.profiles.active=" + SuiteSecurityConfiguration.SECURED_PROFILE})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RestA2AServer_v0_3_WithAuthTest extends AbstractA2AServerWithAuthTest_v0_3 {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    RestA2AServer_v0_3_WithAuthTest() {
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

    @Override
    protected void configureTransportWithAuth(ClientBuilder_v0_3 builder) {
        AuthInterceptor_v0_3 authInterceptor = new AuthInterceptor_v0_3(
                (schemeName, context) ->
                        BASIC_AUTH_SCHEME_NAME.equals(schemeName) ? getEncodedCredentials() : null);
        builder.withTransport(RestTransport_v0_3.class, new RestTransportConfigBuilder_v0_3().addInterceptor(authInterceptor));
    }
}
