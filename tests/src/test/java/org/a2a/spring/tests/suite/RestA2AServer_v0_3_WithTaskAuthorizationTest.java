/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.compat03.client.ClientBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.rest.RestTransport_v0_3;
import org.a2aproject.sdk.compat03.client.transport.rest.RestTransportConfigBuilder_v0_3;
import org.a2aproject.sdk.compat03.client.transport.spi.interceptors.auth.AuthInterceptor_v0_3;
import org.a2aproject.sdk.compat03.conversion.AbstractA2AServerWithTaskAuthorizationTest_v0_3;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Protocol v0.3 Rest with per-task ownership checks, ported from
 * {@code tests/compat-0.3/rest}'s {@code JakartaA2AServer_v0_3_WithTaskAuthorization} suite.
 *
 * <p>Runs against {@link Compat03TaskAuthSuiteApplication}, the only v0.3 application that
 * declares a {@code TaskAuthorizationProvider} — see {@link TaskAuthTestKitConfiguration} for
 * why that bean is kept out of the other contexts.
 */
@SpringBootTest(classes = Compat03TaskAuthSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=" + SuiteServerPort.PORT_STRING,
                "a2a.test.security-enabled=true",
                "spring.profiles.active=" + SuiteSecurityConfiguration.SECURED_PROFILE
                        + "," + TaskAuthTestKitConfiguration.TASK_AUTH_PROFILE})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RestA2AServer_v0_3_WithTaskAuthorizationTest
        extends AbstractA2AServerWithTaskAuthorizationTest_v0_3 {

    static {
        SuiteServerPort.publishToAgentCardProducer();
    }

    RestA2AServer_v0_3_WithTaskAuthorizationTest() {
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
    protected void configureTransportWithCredentials(
            ClientBuilder_v0_3 builder, String username, String password) {
        AuthInterceptor_v0_3 authInterceptor = new AuthInterceptor_v0_3(
                (schemeName, context) -> BASIC_AUTH_SCHEME_NAME.equals(schemeName)
                        ? getEncodedCredentials(username, password) : null);
        builder.withTransport(RestTransport_v0_3.class, new RestTransportConfigBuilder_v0_3().addInterceptor(authInterceptor));
    }
}
