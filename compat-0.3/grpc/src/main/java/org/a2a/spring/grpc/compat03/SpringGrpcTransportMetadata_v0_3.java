package org.a2a.spring.grpc.compat03;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;

public class SpringGrpcTransportMetadata_v0_3 implements TransportMetadata {
    @Override
    public String getTransportProtocol() {
        return TransportProtocol_v0_3.GRPC.asString();
    }
}
