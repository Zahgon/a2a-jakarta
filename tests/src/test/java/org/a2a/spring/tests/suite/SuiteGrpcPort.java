/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests.suite;

/**
 * The gRPC port the ported gRPC suites bind, and the property that turns the server on.
 *
 * <p>9555 is the source's number: its Arquillian modules provisioned WildFly with the gRPC
 * subsystem on that port while HTTP stayed on 8080, and each suite hardcoded
 * {@code localhost:9555} in {@code getTransportUrl()}. The split matters — the abstract suites
 * reach the {@code /test} control surface over HTTP and the A2A calls over gRPC, so both have to
 * be live at once.
 */
final class SuiteGrpcPort {

    static final String PORT_STRING = "9555";

    /** Overrides the {@code grpc.server.port=-1} default that keeps the server off elsewhere. */
    static final String ENABLE_PROPERTY = "grpc.server.port=" + PORT_STRING;

    private SuiteGrpcPort() {
    }

    static String target() {
        return "localhost:" + PORT_STRING;
    }
}
