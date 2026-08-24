/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.grpc.compat03;

import java.util.concurrent.Executor;

import jakarta.annotation.PreDestroy;

import org.a2aproject.sdk.compat03.conversion.Convert_v0_3_To10RequestHandler;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.handler.CallContextFactory_v0_3;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Protocol v0.3 counterpart of {@code GrpcBeanInitializer}: publishes the v0.3 beans to
 * {@link SpringGrpcHandler_v0_3} once the context is ready, because the gRPC server instantiates
 * the handler outside the Spring container.
 */
@Component
public class GrpcBeanInitializer_v0_3 {

    private final AgentCard_v0_3 agentCard;
    private final Convert_v0_3_To10RequestHandler requestHandler;
    private final ObjectProvider<CallContextFactory_v0_3> callContextFactory;
    private final Executor executor;

    public GrpcBeanInitializer_v0_3(
            @Qualifier("publicAgentCard_v0_3") AgentCard_v0_3 agentCard,
            Convert_v0_3_To10RequestHandler requestHandler,
            ObjectProvider<CallContextFactory_v0_3> callContextFactory,
            @Qualifier("a2aInternalExecutor") Executor executor) {
        this.agentCard = agentCard;
        this.requestHandler = requestHandler;
        this.callContextFactory = callContextFactory;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        CallContextFactory_v0_3 ccf = callContextFactory.getIfAvailable();
        ClassLoader deploymentClassLoader = Thread.currentThread().getContextClassLoader();
        SpringGrpcHandler_v0_3.setStaticBeans(agentCard, requestHandler, ccf, executor, deploymentClassLoader);
    }

    @PreDestroy
    public void cleanup() {
        SpringGrpcHandler_v0_3.setStaticBeans(null, null, null, null, null);
    }
}
