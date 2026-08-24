/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import java.util.ArrayList;
import java.util.List;

import org.a2aproject.sdk.server.apps.common.AgentCardProducer;
import org.a2aproject.sdk.server.apps.common.AgentExecutorProducer;
import org.a2aproject.sdk.server.apps.common.RequestScopedBean;
import org.a2aproject.sdk.server.apps.common.TestUtilsBean;
import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.WebApplicationContext;

/**
 * Hosts the SDK's shared server test-kit as Spring beans.
 *
 * <p>The source declared these through CDI: the ShrinkWrap archives added
 * {@code AbstractA2AServerTest.class.getPackage()} wholesale and let the WildFly container
 * discover {@code AgentCardProducer}, {@code AgentExecutorProducer}, {@code TestUtilsBean} and
 * {@code RequestScopedBean} from the bean archive. There is no bean-discovery equivalent here, so
 * each one is declared explicitly.
 *
 * <p>The classes themselves are unmodified. Their {@code @Inject} fields are JSR-330, which Spring
 * autowires natively, so {@code TestUtilsBean} still receives the real {@code TaskStore},
 * {@code QueueManager} and {@code PushNotificationConfigStore} that
 * {@code A2AServerAutoConfiguration} publishes — the same instances the transport handlers use.
 * That shared-instance property is what makes the test-kit's out-of-band task and queue
 * manipulation observable to the server, and it is the reason these are beans rather than
 * hand-built objects.
 *
 * <p>{@code AgentCardProducer.securityEnabled} is a package-private field the source populated
 * with MicroProfile {@code @ConfigProperty}. Spring does not process that annotation, so it stays
 * {@code false} here — correct for the unauthenticated suites. The authenticated suites set it
 * explicitly; see {@link SecuredAgentCardConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
public class SdkTestKitConfiguration {

    /**
     * Must be named {@code publicAgentCard}: {@code A2AServerAutoConfiguration} gates the agent
     * card cache and the card {@code Instance} on {@code @ConditionalOnBean(name =
     * "publicAgentCard")}, so a differently named bean silently disables both.
     *
     * <p>The producer reads {@code test.agent.card.port} as a plain system property and the
     * preferred transport from {@code /a2a-requesthandler-test.properties} on the classpath.
     * {@link SuiteServerPort} sets the former before the context starts.
     *
     * <p>It carries {@code @PublicAgentCard} as well as the name. The transport auto-configuration
     * resolves the card by bean name, but {@code AgentExecutorProducer} injects it by CDI
     * qualifier; since {@code @PublicAgentCard} is meta-annotated {@code jakarta.inject.Qualifier},
     * Spring honours it and both lookups land on this one bean.
     *
     * <p>{@code @ExtendedAgentCard} is on it for the same reason. The SDK's producer method
     * declares both qualifiers on the single {@code agentCard()} method, so under CDI one bean
     * answered both the public and the extended injection points — which is why
     * {@code testGetExtendedAgentCard} expects the extended card to carry the public card's name
     * and description. Declaring only the public qualifier leaves the extended provider empty and
     * the server answers {@code ExtendedAgentCardNotConfiguredError} instead of a card.
     */
    @Bean("publicAgentCard")
    @PublicAgentCard
    @ExtendedAgentCard
    public AgentCard publicAgentCard(
            AgentCardProducer producer,
            @Value("${a2a.test.advertise-v0-3:true}") boolean advertiseV03) {
        AgentCard card = advertiseEveryServedTransport(producer.agentCard());
        return advertiseV03 ? card : withoutV03Interfaces(card);
    }

    /**
     * Drops the v0.3 entries from a card whose deployment does not serve v0.3.
     *
     * <p>{@code AgentCardProducer} calls {@code Compat03Fields.addCompat03FieldsIfAvailable}, which
     * stamps {@code additionalInterfaces} onto the card whenever the compatibility classes are on
     * the classpath. In the source that was self-correcting: a module that did not serve v0.3 did
     * not depend on it either, so the call was a no-op. Here every suite shares one classpath, so
     * the card advertises v0.3 even from a v1.0-only context and a client that believes it picks
     * v0.3, then gets {@code VersionNotSupportedError} from a server that only registered 1.0.
     *
     * <p>Defaults to leaving the fields in place, so the deployments that do serve both versions
     * are unaffected; only the v1.0-only gRPC suite turns it off.
     */
    private static AgentCard withoutV03Interfaces(AgentCard card) {
        return AgentCard.builder(card).additionalInterfaces(List.of()).build();
    }

    /**
     * Adds an {@link AgentInterface} for each transport this application actually serves.
     *
     * <p>{@code AgentCardProducer} advertises exactly one, read from {@code preferred-transport} in
     * {@code /a2a-requesthandler-test.properties}. That was right for the source, where every
     * transport had its own Arquillian module, its own {@code ROOT.war} and its own copy of that
     * file — a deployment really did serve one transport.
     *
     * <p>Here all the transports live in one Spring context on one port, and a classpath resource
     * cannot hold a different value per suite. Advertising only one would leave
     * {@code AbstractA2AServerTest} unable to find an interface for whichever transport it drives,
     * failing {@code testGetExtendedAgentCard} and {@code testAgentToAgentDelegation} for every
     * suite but the one named in the file.
     *
     * <p>So the card is widened to describe what is genuinely reachable rather than narrowed to
     * fit one suite. {@code preferredTransport} is untouched, so the producer still decides
     * precedence.
     */
    private static AgentCard advertiseEveryServedTransport(AgentCard card) {
        List<AgentInterface> interfaces = new ArrayList<>(card.supportedInterfaces());
        for (TransportProtocol served : List.of(
                TransportProtocol.JSONRPC, TransportProtocol.HTTP_JSON, TransportProtocol.GRPC)) {
            String binding = served.asString();
            boolean alreadyThere = interfaces.stream()
                    .anyMatch(candidate -> binding.equals(candidate.protocolBinding()));
            if (!alreadyThere) {
                interfaces.add(new AgentInterface(binding, urlFor(served)));
            }
        }
        return AgentCard.builder(card).supportedInterfaces(interfaces).build();
    }

    /**
     * gRPC lives on its own port and is addressed without a URL scheme; the HTTP transports share
     * the servlet port. The source expressed the same split across modules — its gRPC module set
     * {@code test.agent.card.port=9555} while the others left it at the HTTP port — which a single
     * deployment has to express per interface instead.
     */
    private static String urlFor(TransportProtocol transport) {
        return transport == TransportProtocol.GRPC ? SuiteGrpcPort.target() : SuiteServerPort.baseUrl();
    }

    @Bean
    public AgentCardProducer agentCardProducer() {
        return new AgentCardProducer();
    }

    /**
     * Satisfies {@code AgentCardProducer.securityEnabled}, which the source populated with
     * MicroProfile {@code @ConfigProperty} and which carries a bare {@code @Inject} that Spring
     * does try to resolve — by type, against a {@code boolean}. Without a bean of that type the
     * context fails to start, so the value is published as one and sourced from a property instead
     * of a config extension.
     *
     * <p>It decides whether the advertised agent card carries a {@code basicAuth} security scheme,
     * which is what the authenticated suites assert on, so it must track whether Spring Security is
     * actually active — see {@code AuthSuiteConfiguration}.
     */
    @Bean
    public Boolean agentCardSecurityEnabled(
            @Value("${a2a.test.security-enabled:false}") boolean securityEnabled) {
        return securityEnabled;
    }

    @Bean
    public AgentExecutorProducer agentExecutorProducer() {
        return new AgentExecutorProducer();
    }

    /**
     * The source obtained this through the producer's {@code @Produces} method. Spring has no
     * producer-method concept, so the call is made explicitly and its result published.
     */
    @Bean
    public AgentExecutor agentExecutor(AgentExecutorProducer producer) {
        return producer.agentExecutor();
    }

    @Bean
    public TestUtilsBean testUtilsBean() {
        return new TestUtilsBean();
    }

    /**
     * {@code @RequestScoped} in the source. The scoped proxy is required because
     * {@code AgentExecutorProducer} is a singleton that injects this field once — without the
     * proxy it would capture the first request's instance and
     * {@code testRequestScopedBeanAvailableOnAgentExecutorThread} would pass for the wrong reason.
     */
    @Bean
    @Scope(scopeName = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public RequestScopedBean requestScopedBean() {
        return new RequestScopedBean();
    }
}
