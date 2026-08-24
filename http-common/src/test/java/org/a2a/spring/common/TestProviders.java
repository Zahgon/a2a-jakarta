/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import java.util.Set;

class TestProviders {

    static A2AVersionProvider provider(String version, boolean isDefault, String internalPrefix, String restBasePath) {
        return provider(version, isDefault, internalPrefix, restBasePath, Set.of());
    }

    static A2AVersionProvider provider(String version, boolean isDefault, String internalPrefix, String restBasePath,
            Set<String> restPathPrefixes) {
        return new A2AVersionProvider() {
            @Override public String getVersion() { return version; }
            @Override public boolean isDefaultVersion() { return isDefault; }
            @Override public String getInternalPathPrefix() { return internalPrefix; }
            @Override public String getRestBasePath() { return restBasePath; }
            @Override public Set<String> getRestPathPrefixes() { return restPathPrefixes; }
        };
    }

    static A2AVersionProvider provider(String version, String internalPrefix, String restBasePath) {
        return provider(version, false, internalPrefix, restBasePath);
    }

    static A2AVersionProvider jsonRpcProvider(String version, boolean isDefault) {
        return provider(version, isDefault, "/a2a_jsonrpc_v" + version, null);
    }

    static A2AJsonRpcMethodProvider methodProvider(Set<String> streaming, Set<String> nonStreaming) {
        return new A2AJsonRpcMethodProvider() {
            @Override public Set<String> getStreamingMethodNames() { return streaming; }
            @Override public Set<String> getNonStreamingMethodNames() { return nonStreaming; }
        };
    }

    private TestProviders() {
    }
}
