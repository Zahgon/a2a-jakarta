/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.stub.StreamObserver;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.AuthenticatedUser;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.grpc.context.GrpcContextKeys;
import org.a2aproject.sdk.transport.grpc.handler.CallContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Builds the {@link ServerCallContext} for gRPC calls — the port of the source's
 * {@code TestCallContextFactory} and {@code GrpcCallContextHelper} from {@code tests/common}.
 *
 * <p>Without a {@link CallContextFactory} bean the SDK's gRPC handler falls back to a default
 * context that does not carry the requested protocol version, and every call is answered with
 * {@code VersionNotSupportedError: Protocol version '0.3' is not supported}. The version the
 * client asked for arrives in gRPC metadata, not in the message, so reading
 * {@code GrpcContextKeys.VERSION_HEADER_KEY} is the only way the routing layer can see it.
 *
 * <p>{@link #AUTHENTICATED_USER} stands in for the source's
 * {@code MultiUserBasicAuthGrpcInterceptor} context key. gRPC does not go through the servlet
 * filter chain, so Spring Security never sees these calls and the {@code Principal} route the HTTP
 * transports use is unavailable; an interceptor has to put the identity into the gRPC context
 * instead. Unset, it resolves to {@code UnauthenticatedUser}, which is what the unauthenticated
 * suites expect.
 */
@Configuration(proxyBeanMethods = false)
@Profile(GrpcTestKitConfiguration.GRPC_PROFILE)
public class GrpcTestKitConfiguration {

    /**
     * Activated only by the gRPC suites, so the factory is absent from the contexts whose
     * transports build their call context from the servlet request instead.
     */
    public static final String GRPC_PROFILE = "grpc";

    /** Set by {@link BasicAuthGrpcInterceptor}; read back when building the call context. */
    static final Context.Key<String> AUTHENTICATED_USER = Context.key("a2a-test-authenticated-user");

    @Bean
    public CallContextFactory grpcCallContextFactory() {
        return new CallContextFactory() {
            @Override
            public <V> ServerCallContext create(StreamObserver<V> responseObserver) {
                return GrpcTestKitConfiguration.create(responseObserver);
            }
        };
    }

    private static <V> ServerCallContext create(StreamObserver<V> responseObserver) {
        String username = AUTHENTICATED_USER.get();
        User user = username != null ? new AuthenticatedUser(username) : UnauthenticatedUser.INSTANCE;

        Map<String, Object> state = buildState(responseObserver);

        String requestedVersion = null;
        try {
            requestedVersion = GrpcContextKeys.VERSION_HEADER_KEY.get();
        } catch (Exception e) {
            // Key not bound on this call; the resolver treats null as "no version requested".
        }

        Set<String> requestedExtensions = new HashSet<>();
        try {
            String extensionsHeader = GrpcContextKeys.EXTENSIONS_HEADER_KEY.get();
            if (extensionsHeader != null) {
                requestedExtensions = A2AExtensions.getRequestedExtensions(List.of(extensionsHeader));
            }
        } catch (Exception e) {
            // As above.
        }

        return new ServerCallContext(user, state, requestedExtensions, requestedVersion);
    }

    /** Verbatim port of the source helper's state map; the SDK reads these keys by name. */
    private static <V> Map<String, Object> buildState(StreamObserver<V> responseObserver) {
        Map<String, Object> state = new HashMap<>();
        state.put(TRANSPORT_KEY, TransportProtocol.GRPC);
        state.put("grpc_response_observer", responseObserver);

        Context currentContext = Context.current();
        state.put("grpc_context", currentContext);

        Metadata grpcMetadata = GrpcContextKeys.METADATA_KEY.get(currentContext);
        if (grpcMetadata != null) {
            state.put("grpc_metadata", grpcMetadata);
            Map<String, String> headers = new HashMap<>();
            for (String key : grpcMetadata.keys()) {
                if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                    continue;
                }
                headers.put(key, grpcMetadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
            }
            state.put("headers", headers);
        }

        String methodName = GrpcContextKeys.GRPC_METHOD_NAME_KEY.get(currentContext);
        if (methodName != null) {
            state.put("grpc_method_name", methodName);
        }

        String peerInfo = GrpcContextKeys.PEER_INFO_KEY.get(currentContext);
        if (peerInfo != null) {
            state.put("grpc_peer_info", peerInfo);
        }

        return state;
    }
}
