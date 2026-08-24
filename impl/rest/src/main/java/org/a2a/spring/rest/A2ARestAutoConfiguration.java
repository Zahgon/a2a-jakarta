/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.rest;

import java.util.concurrent.Executor;
import org.a2aproject.sdk.server.AgentCardCacheMetadata;
import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.a2a.spring.common.A2AServerAutoConfiguration;
import org.a2a.spring.common.SpringCdiInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Supplies the {@link RestHandler} that {@link A2ARestServerResource} injects.
 *
 * <p>In the Jakarta original the handler was a CDI bean discovered from the SDK's transport
 * archive, so the JAX-RS resource could simply {@code @Inject} it. Spring has no equivalent
 * discovery, so the handler is built here explicitly.
 */
@AutoConfiguration
@AutoConfigureAfter(A2AServerAutoConfiguration.class)
public class A2ARestAutoConfiguration {

    /**
     * Takes the extended-agent-card {@code Instance} overload, for the reason given on
     * {@code A2AJsonRpcAutoConfiguration#a2aJsonRpcHandler}: the shorter constructor stores
     * {@code null} and the authenticated-extended-card route dereferences it. Both transports
     * expose that route, so both need the real provider.
     */
    @Bean
    @ConditionalOnBean(value = {RequestHandler.class, AgentCardCacheMetadata.class}, name = "publicAgentCard")
    @ConditionalOnMissingBean
    public RestHandler a2aRestHandler(
            @Qualifier("publicAgentCard") AgentCard agentCard,
            @ExtendedAgentCard ObjectProvider<AgentCard> extendedAgentCard,
            AgentCardCacheMetadata cacheMetadata,
            RequestHandler requestHandler,
            @Qualifier("a2aInternalExecutor") Executor executor) {
        return new RestHandler(
                agentCard, new SpringCdiInstance<>(extendedAgentCard), cacheMetadata, requestHandler, executor);
    }
}
