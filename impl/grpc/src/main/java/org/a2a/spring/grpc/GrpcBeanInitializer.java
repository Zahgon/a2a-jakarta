/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.grpc;

import java.util.concurrent.Executor;

import jakarta.annotation.PreDestroy;

import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.transport.grpc.handler.CallContextFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Publishes the application's A2A beans to {@link SpringGrpcHandler} once the context is ready.
 *
 * <p>The gRPC handler is instantiated by the gRPC server rather than by the Spring container, so it
 * cannot receive injected collaborators directly; this component bridges the two by handing the
 * beans over through a static holder.
 */
@Component
public class GrpcBeanInitializer {

    private final AgentCard agentCard;
    private final ObjectProvider<AgentCard> extendedAgentCard;
    private final RequestHandler requestHandler;
    private final ObjectProvider<CallContextFactory> callContextFactory;
    private final Executor executor;

    public GrpcBeanInitializer(
            @Qualifier("publicAgentCard") AgentCard agentCard,
            @ExtendedAgentCard ObjectProvider<AgentCard> extendedAgentCard,
            RequestHandler requestHandler,
            ObjectProvider<CallContextFactory> callContextFactory,
            @Qualifier("a2aInternalExecutor") Executor executor) {
        this.agentCard = agentCard;
        this.extendedAgentCard = extendedAgentCard;
        this.requestHandler = requestHandler;
        this.callContextFactory = callContextFactory;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        CallContextFactory ccf = callContextFactory.getIfAvailable();
        AgentCard extCard = extendedAgentCard.getIfAvailable();
        ClassLoader deploymentClassLoader = Thread.currentThread().getContextClassLoader();
        SpringGrpcHandler.setStaticBeans(agentCard, extCard, requestHandler, ccf, executor, deploymentClassLoader);
    }

    @PreDestroy
    public void cleanup() {
        SpringGrpcHandler.setStaticBeans(null, null, null, null, null, null);
    }
}
