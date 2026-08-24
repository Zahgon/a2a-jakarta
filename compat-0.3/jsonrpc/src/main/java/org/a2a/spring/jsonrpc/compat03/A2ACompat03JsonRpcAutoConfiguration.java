/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.jsonrpc.compat03;

import java.util.concurrent.Executor;

import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.transport.jsonrpc.handler.JSONRPCHandler_v0_3;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.a2a.spring.common.A2AServerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Builds the protocol v0.3 JSON-RPC handler that {@link A2AServerResource_v0_3} consumes.
 *
 * <p>In the Jakarta original the SDK's compat-0.3 transport artifact was a CDI bean archive, so
 * {@code JSONRPCHandler_v0_3} and {@code Convert_v0_3_To10RequestHandler} were discovered and wired
 * by the container. Spring does not scan third-party jars, so both are declared explicitly here
 * using the SDK's CDI-free constructor overloads.
 *
 * <p>The application supplies the {@code publicAgentCard_v0_3} bean; the v1.0
 * {@link RequestHandler} comes from {@code A2AServerAutoConfiguration} in {@code http-common} and is
 * adapted down to the 0.3 protocol shape by {@link Convert_v0_3_To10RequestHandler}.
 */
@AutoConfiguration
@AutoConfigureAfter(A2AServerAutoConfiguration.class)
public class A2ACompat03JsonRpcAutoConfiguration {

    @Bean
    @ConditionalOnBean(RequestHandler.class)
    @ConditionalOnMissingBean
    public Convert_v0_3_To10RequestHandler a2aCompat03RequestHandler(RequestHandler requestHandler) {
        return new Convert_v0_3_To10RequestHandler(requestHandler);
    }

    @Bean
    @ConditionalOnBean(value = Convert_v0_3_To10RequestHandler.class, name = "publicAgentCard_v0_3")
    @ConditionalOnMissingBean
    public JSONRPCHandler_v0_3 a2aCompat03JsonRpcHandler(
            @Qualifier("publicAgentCard_v0_3") AgentCard_v0_3 agentCard,
            @Qualifier("a2aInternalExecutor") Executor executor,
            Convert_v0_3_To10RequestHandler requestHandler) {
        return new JSONRPCHandler_v0_3(agentCard, executor, requestHandler);
    }
}
