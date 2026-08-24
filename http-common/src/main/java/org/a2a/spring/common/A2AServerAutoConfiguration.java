/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import org.a2aproject.sdk.server.AgentCardCacheMetadata;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.config.DefaultValuesConfigProvider;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.requesthandlers.AuthorizationRequestHandlerDecorator;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring replacement for the A2A SDK's CDI bean graph.
 *
 * <p>In the Jakarta EE original the SDK's server-common and transport artifacts were themselves CDI
 * bean archives: the container discovered {@code InMemoryTaskStore}, {@code DefaultRequestHandler},
 * {@code MainEventBusProcessor} and friends, resolved their {@code @Inject} points, and fired
 * {@code MainEventBusProcessorInitializer} on startup. None of that happens under Spring, so every
 * one of those beans is declared explicitly here using the SDK's <em>CDI-free</em> constructor
 * overloads (each SDK type ships one specifically so it can be built without a container).
 *
 * <p>Every bean is {@link ConditionalOnMissingBean}, so an application can override any single piece
 * (for example a JDBC-backed {@code TaskStore}) without replacing this configuration. Two beans are
 * deliberately <em>not</em> declared and must be supplied by the application, exactly as they were in
 * the Jakarta version: the {@link AgentExecutor} that implements the agent's behaviour, and the
 * {@code publicAgentCard} {@link AgentCard}.
 */
@AutoConfiguration
public class A2AServerAutoConfiguration {


    @Bean
    @ConditionalOnMissingBean
    public A2AConfigProvider a2aConfigProvider() {
        return new DefaultValuesConfigProvider();
    }

    /**
     * Declared as the concrete {@link InMemoryTaskStore} rather than the {@code TaskStore} interface
     * because it also implements {@code TaskStateProvider}, which {@link InMemoryQueueManager}
     * requires. Returning the interface type would hide that second role from the container.
     */
    @Bean
    @ConditionalOnMissingBean
    public InMemoryTaskStore a2aTaskStore(ObjectProvider<TaskAuthorizationProvider> authorizationProvider) {
        TaskAuthorizationProvider provider = authorizationProvider.getIfAvailable();
        return provider == null ? new InMemoryTaskStore() : new InMemoryTaskStore(provider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PushNotificationConfigStore a2aPushNotificationConfigStore() {
        return new InMemoryPushNotificationConfigStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public PushNotificationSender a2aPushNotificationSender(PushNotificationConfigStore configStore) {
        return new BasePushNotificationSender(configStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public MainEventBus a2aMainEventBus() {
        return new MainEventBus();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueueManager a2aQueueManager(InMemoryTaskStore taskStore, MainEventBus mainEventBus) {
        return new InMemoryQueueManager(taskStore, mainEventBus);
    }

    /**
     * {@code ensureStarted()} is wired as the bean init method because the SDK's own
     * {@code MainEventBusProcessorInitializer} observes a CDI startup event that Spring never fires,
     * and the processor's {@code start()} method is package-private.
     */
    @Bean(initMethod = "ensureStarted")
    @ConditionalOnMissingBean
    public MainEventBusProcessor a2aMainEventBusProcessor(
            MainEventBus mainEventBus,
            InMemoryTaskStore taskStore,
            PushNotificationSender pushNotificationSender,
            QueueManager queueManager,
            @Qualifier("a2aInternalExecutor") Executor executor) {
        MainEventBusProcessor processor =
                new MainEventBusProcessor(mainEventBus, taskStore, pushNotificationSender, queueManager);
        processor.setPushNotificationExecutor(executor);
        return processor;
    }

    @Bean
    @ConditionalOnBean(AgentExecutor.class)
    @ConditionalOnMissingBean
    public RequestHandler a2aRequestHandler(
            AgentExecutor agentExecutor,
            InMemoryTaskStore taskStore,
            QueueManager queueManager,
            PushNotificationConfigStore pushConfigStore,
            MainEventBusProcessor mainEventBusProcessor,
            @Qualifier("a2aInternalExecutor") Executor executor,
            ObjectProvider<TaskAuthorizationProvider> authorizationProvider) {
        DefaultRequestHandler handler = DefaultRequestHandler.builder()
                .agentExecutor(agentExecutor)
                .taskStore(taskStore)
                .queueManager(queueManager)
                .pushConfigStore(pushConfigStore)
                .mainEventBusProcessor(mainEventBusProcessor)
                .executor(executor)
                .eventConsumerExecutor(executor)
                .authorizationProvider(authorizationProvider.getIfAvailable())
                .build();

        TaskAuthorizationProvider provider = authorizationProvider.getIfAvailable();
        return provider == null ? handler : authorizing(handler, provider);
    }

    /**
     * Applies the SDK's ownership checks over a request handler.
     *
     * <p>Handing the provider to {@code DefaultRequestHandler.builder()} does not enforce anything.
     * The checks live in {@link AuthorizationRequestHandlerDecorator}, which CDI applied
     * automatically as a decorator over the {@code RequestHandler} bean. Spring has no decorator
     * mechanism, so it has to be applied by hand.
     *
     * <p>Without it a {@code TaskAuthorizationProvider} is inert: nothing calls
     * {@code recordOwnership}, the provider's owner map stays empty, and its {@code checkRead}
     * returns true for a task it has never seen — so every caller can read and cancel every other
     * caller's tasks. That is a silent authorization bypass, and it is invisible to any test that
     * does not drive two different identities against one server.
     *
     * <p>The decorator is deliberately not returned directly. Its {@code delegate} field carries
     * CDI's {@code @Inject @Delegate @Any}, and Spring — which honours {@code jakarta.inject}
     * annotations — would try to satisfy that injection point from the container, find no bean
     * qualified {@code @Any}, and fail to start. Fronting it with a proxy means Spring
     * post-processes the proxy, which has no injection points, and the decorator keeps the delegate
     * the constructor already gave it.
     */
    private static RequestHandler authorizing(RequestHandler delegate, TaskAuthorizationProvider provider) {
        RequestHandler decorated = new AuthorizationRequestHandlerDecorator(delegate, provider);
        return (RequestHandler) Proxy.newProxyInstance(
                RequestHandler.class.getClassLoader(),
                new Class<?>[] {RequestHandler.class},
                (proxy, method, args) -> {
                    try {
                        return method.invoke(decorated, args);
                    } catch (InvocationTargetException e) {
                        // Unwrap, so A2AError surfaces to callers as itself rather than as a
                        // reflection wrapper the transports would not recognise.
                        throw e.getCause();
                    }
                });
    }

    @Bean
    @ConditionalOnBean(name = "publicAgentCard")
    @ConditionalOnMissingBean
    public AgentCardCacheMetadata a2aAgentCardCacheMetadata(
            @Qualifier("publicAgentCard") AgentCard agentCard, A2AConfigProvider configProvider) {
        return new AgentCardCacheMetadata(agentCard, configProvider);
    }

    @Bean
    @PublicAgentCard
    @ConditionalOnBean(name = "publicAgentCard")
    @ConditionalOnMissingBean
    public Instance<AgentCard> a2aPublicAgentCardInstance(
            @Qualifier("publicAgentCard") ObjectProvider<AgentCard> agentCardProvider) {
        return new SpringCdiInstance<>(agentCardProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public Instance<A2AConfigProvider> a2aConfigProviderInstance(ObjectProvider<A2AConfigProvider> provider) {
        return new SpringCdiInstance<>(provider);
    }

    @Bean
    @Any
    @ConditionalOnMissingBean
    public Instance<TaskAuthorizationProvider> a2aTaskAuthorizationProviderInstance(
            ObjectProvider<TaskAuthorizationProvider> authorizationProviders) {
        return new SpringCdiInstance<>(authorizationProviders);
    }
}
