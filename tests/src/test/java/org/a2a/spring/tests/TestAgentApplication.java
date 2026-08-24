/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test application replacing the Arquillian/ShrinkWrap deployments of the Jakarta original.
 *
 * <p>The {@code compat03} packages are excluded from scanning because their controllers require a
 * {@code publicAgentCard_v0_3} bean; this application exercises protocol v1.0 only.
 *
 * <p>{@code org.a2a.spring.tests.suite} is excluded too. That package hosts the ported SDK
 * suites and declares its own {@code publicAgentCard}; scanning it here would collide with the
 * one below and fail context startup. The two test applications are independent by design.
 *
 * <p>Security auto-configuration is excluded because the ported authenticated suites put
 * spring-boot-starter-security on the shared test classpath. Boot would otherwise apply its
 * default chain to this context as well, and these tests would meet a login redirect instead
 * of the A2A endpoints they assert on.
 */
@SpringBootApplication(
        scanBasePackages = "org.a2a.spring",
        exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@ComponentScan(
        basePackages = "org.a2a.spring",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.a2a\\.spring\\..*\\.compat03\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.a2a\\.spring\\.tests\\.suite\\..*")
        })
public class TestAgentApplication {

    @Bean("publicAgentCard")
    public AgentCard publicAgentCard() {
        List<AgentInterface> interfaces = new ArrayList<>();
        interfaces.add(new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://localhost:8080"));
        interfaces.add(new AgentInterface(TransportProtocol.HTTP_JSON.asString(), "http://localhost:8080"));
        return AgentCard.builder()
                .name("Test Agent")
                .description("Agent used by the Spring migration test suite")
                .version("1.0.0")
                .documentationUrl("http://example.com/docs")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(AgentSkill.builder()
                        .id("hello_world")
                        .name("Returns hello world")
                        .description("just returns hello world")
                        .tags(List.of("hello world"))
                        .examples(List.of("hi", "hello world"))
                        .build()))
                .supportedInterfaces(interfaces)
                .build();
    }

    @Bean
    public AgentExecutor testAgentExecutor() {
        return new EchoAgentExecutor();
    }

    private static final class EchoAgentExecutor implements AgentExecutor {

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            emitter.startWork();
            List<Part<?>> partsList = context.getMessage().parts();
            List<TextPart> textParts = partsList.stream()
                    .filter(part -> part instanceof TextPart)
                    .map(part -> (TextPart) part)
                    .toList();
            String name = textParts.get(textParts.size() - 1).text();
            emitter.addArtifact(Collections.singletonList(new TextPart("Hello " + name)), null, "response", null);
            emitter.complete();
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            throw new TaskNotCancelableError();
        }
    }
}
