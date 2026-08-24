/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import org.a2aproject.sdk.compat03.conversion.AgentCardProducer_v0_3;
import org.a2aproject.sdk.compat03.spec.AgentCard_v0_3;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The v0.3 half of the test-kit, hosting {@link AgentCardProducer_v0_3} as Spring beans.
 *
 * <p>Mirrors {@link SdkTestKitConfiguration} exactly, for the same reason: the source let CDI
 * discover these from the bean archive its ShrinkWrap deployment assembled, and Spring has no
 * equivalent discovery.
 */
@Configuration(proxyBeanMethods = false)
public class Compat03TestKitConfiguration {

    /**
     * Must be named {@code publicAgentCard_v0_3}. Both compatibility auto-configurations gate
     * their handler on {@code @ConditionalOnBean(name = "publicAgentCard_v0_3")} and inject it by
     * that {@code @Qualifier}, so the name is the contract — a differently named bean leaves the
     * v0.3 controllers without a handler and the context fails to start.
     *
     * <p>Unlike the v1.0 producer this one declares only {@code @PublicAgentCard}; v0.3 has no
     * extended-card concept, so there is no second qualifier to mirror.
     */
    @Bean("publicAgentCard_v0_3")
    public AgentCard_v0_3 publicAgentCardV03(AgentCardProducer_v0_3 producer) {
        return producer.createTestAgentCard();
    }

    @Bean
    public AgentCardProducer_v0_3 agentCardProducerV03() {
        return new AgentCardProducer_v0_3();
    }
}
