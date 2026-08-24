/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.rest.compat03;

import java.util.concurrent.Executor;

import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.transport.rest.handler.RestHandler_v0_3;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.a2a.spring.common.A2AServerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Builds the protocol v0.3 REST handler that {@link A2ARestServerResource_v0_3} consumes.
 *
 * <p>Mirrors the compat-0.3 JSON-RPC configuration. Unlike the v1.0 {@code RestHandler}, the v0.3
 * REST handler takes no {@code AgentCardCacheMetadata}.
 *
 * <p>The {@link Convert_v0_3_To10RequestHandler} bean is declared identically in the compat-0.3
 * JSON-RPC module. Both are {@link AutoConfiguration}s so Spring Boot evaluates
 * {@link ConditionalOnMissingBean} against the already-registered set, and exactly one wins when
 * both transports are on the classpath.
 */
@AutoConfiguration
@AutoConfigureAfter(A2AServerAutoConfiguration.class)
public class A2ACompat03RestAutoConfiguration {

    @Bean
    @ConditionalOnBean(RequestHandler.class)
    @ConditionalOnMissingBean
    public Convert_v0_3_To10RequestHandler a2aCompat03RequestHandler(RequestHandler requestHandler) {
        return new Convert_v0_3_To10RequestHandler(requestHandler);
    }

    @Bean
    @ConditionalOnBean(value = Convert_v0_3_To10RequestHandler.class, name = "publicAgentCard_v0_3")
    @ConditionalOnMissingBean
    public RestHandler_v0_3 a2aCompat03RestHandler(
            @Qualifier("publicAgentCard_v0_3") AgentCard_v0_3 agentCard,
            @Qualifier("a2aInternalExecutor") Executor executor,
            Convert_v0_3_To10RequestHandler requestHandler) {
        return new RestHandler_v0_3(agentCard, executor, requestHandler);
    }
}
