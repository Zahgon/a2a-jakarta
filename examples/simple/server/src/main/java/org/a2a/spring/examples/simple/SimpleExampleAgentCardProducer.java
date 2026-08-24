/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.examples.simple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the public agent card.
 *
 * <p>The CDI qualifier {@code @PublicAgentCard} becomes the bean name {@code publicAgentCard}, which
 * is what consumers such as {@code GrpcBeanInitializer} qualify on. Spring has no equivalent of a
 * qualifier annotation that is not itself meta-annotated with {@code @Qualifier}, so naming the bean
 * is the direct translation.
 */
@Configuration(proxyBeanMethods = false)
public class SimpleExampleAgentCardProducer {

    @Bean("publicAgentCard")
    public AgentCard createAgentCard() {
        String jsonRpcUrl = "http://localhost:8080";
        List<AgentInterface> interfaces = new ArrayList<>();
        // At the moment we always add the JSONRPC transport. It is needed to get the AgentCard.
        // This may change in the future
        interfaces.add(
                new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), jsonRpcUrl));
        if (isRest()) {
            interfaces.add(
                    new AgentInterface(
                            TransportProtocol.HTTP_JSON.asString(), jsonRpcUrl));
        }
        if (isGrpcEnabled()) {
            interfaces.add(
                    new AgentInterface(
                            TransportProtocol.GRPC.asString(), "localhost:9555"));
        }

        return AgentCard.builder()
                .name("Hello World Agent")
                .description("Just a hello world agent")
                .version("1.0.0")
                .documentationUrl("http://example.com/docs")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.singletonList(AgentSkill.builder()
                        .id("hello_world")
                        .name("Returns hello world")
                        .description("just returns hello world")
                        .tags(Collections.singletonList("hello world"))
                        .examples(List.of("hi", "hello world"))
                        .build()))
                .supportedInterfaces(interfaces)
                .build();
    }

    private boolean isGrpcEnabled() {
        try {
            Class.forName("org.a2a.spring.grpc.GrpcBeanInitializer");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isRest() {
        try {
            Class.forName("org.a2a.spring.rest.SpringRestTransportMetadata");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
