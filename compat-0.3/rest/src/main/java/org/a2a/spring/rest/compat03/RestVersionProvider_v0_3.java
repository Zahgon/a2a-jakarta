package org.a2a.spring.rest.compat03;

import org.springframework.stereotype.Component;

import org.a2a.spring.common.A2AVersionProvider;

@Component
public class RestVersionProvider_v0_3 implements A2AVersionProvider {

    @Override
    public String getVersion() {
        return "0.3";
    }

    @Override
    public boolean isDefaultVersion() {
        return true;
    }

    @Override
    public String getInternalPathPrefix() {
        return "/a2a_rest_v0.3";
    }

    @Override
    public String getRestBasePath() {
        return "/v1";
    }
}
