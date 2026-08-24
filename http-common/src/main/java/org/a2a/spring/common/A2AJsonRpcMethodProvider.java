package org.a2a.spring.common;

import java.util.Set;

public interface A2AJsonRpcMethodProvider {

    Set<String> getStreamingMethodNames();

    Set<String> getNonStreamingMethodNames();
}
