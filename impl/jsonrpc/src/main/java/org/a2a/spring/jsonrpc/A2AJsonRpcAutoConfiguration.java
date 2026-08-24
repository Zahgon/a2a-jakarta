/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.jsonrpc;

import java.util.concurrent.Executor;
import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
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
 * Supplies the {@link JSONRPCHandler} that {@link A2AServerResource} injects.
 *
 * <p>In the Jakarta original the handler was a CDI bean discovered from the SDK's transport
 * archive, so the JAX-RS resource could simply {@code @Inject} it. Spring has no equivalent
 * discovery, so the handler is built here explicitly.
 */
@AutoConfiguration
@AutoConfigureAfter(A2AServerAutoConfiguration.class)
public class A2AJsonRpcAutoConfiguration {

    /**
     * Uses the four-argument constructor, not the three-argument one.
     *
     * <p>The short overload passes {@code null} for the extended-agent-card {@code Instance}, and
     * {@code onGetExtendedCardRequest} dereferences that field on every call — so
     * {@code GetExtendedAgentCard} fails outright instead of answering with the
     * {@code ExtendedAgentCardNotConfiguredError} the SDK defines for the not-configured case.
     * CDI supplied a real (possibly unsatisfied) {@code Instance} in the original, which is why
     * the method worked there.
     *
     * <p>{@code @ExtendedAgentCard} is a JSR-330 qualifier, so Spring narrows the provider to
     * beans carrying it. Declare none and the provider is empty, {@code isUnsatisfied()} reports
     * true, and the SDK returns the not-configured error — the container-managed behaviour,
     * reproduced.
     */
    @Bean
    @ConditionalOnBean(value = RequestHandler.class, name = "publicAgentCard")
    @ConditionalOnMissingBean
    public JSONRPCHandler a2aJsonRpcHandler(
            @Qualifier("publicAgentCard") AgentCard agentCard,
            @ExtendedAgentCard ObjectProvider<AgentCard> extendedAgentCard,
            RequestHandler requestHandler,
            @Qualifier("a2aInternalExecutor") Executor executor) {
        return new JSONRPCHandler(
                agentCard, new SpringCdiInstance<>(extendedAgentCard), requestHandler, executor);
    }
}
