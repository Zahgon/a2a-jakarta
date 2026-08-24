/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.AuthInterceptor;
import org.a2aproject.sdk.server.apps.common.AbstractA2AServerWithTaskAuthorizationTest;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Per-task ownership checks on a two-version deployment, ported from
 * {@code tests/multiversion/rest}'s {@code MultiVersionRestWithTaskAuthorizationTest}.
 */
@SpringBootTest(classes = MultiVersionTaskAuthSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=" + SuiteServerPort.PORT_STRING,
                "a2a.test.security-enabled=true",
                "spring.profiles.active=" + SuiteSecurityConfiguration.SECURED_PROFILE
                        + "," + TaskAuthTestKitConfiguration.TASK_AUTH_PROFILE})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiVersionRestWithTaskAuthorizationTest extends AbstractA2AServerWithTaskAuthorizationTest {

    static {
        SuiteServerPort.publishToAgentCardProducer();
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
    protected void configureTransportWithCredentials(
            ClientBuilder builder, String username, String password) {
        AuthInterceptor authInterceptor = new AuthInterceptor(
                (schemeName, context) -> BASIC_AUTH_SCHEME_NAME.equals(schemeName)
                        ? getEncodedCredentials(username, password) : null);
        builder.withTransport(RestTransport.class, new RestTransportConfigBuilder().addInterceptor(authInterceptor));
    }
}
