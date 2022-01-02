package com.github.taymindis.camel.channeling.http.cloud;

import org.apache.camel.impl.cloud.DefaultServiceCallExpression;
import org.apache.camel.util.ObjectHelper;

public final class ChannelingServiceExpression extends DefaultServiceCallExpression {
    public ChannelingServiceExpression() {
    }

    public ChannelingServiceExpression(String hostHeader, String portHeader) {
        super(hostHeader, portHeader);
    }

    @Override
    protected String doBuildCamelEndpointUri(String host, Integer port, String contextPath, String scheme) {
        if (!ObjectHelper.equal(scheme, "channeling")) {
            return super.doBuildCamelEndpointUri(host, port, contextPath, scheme);
        }

        String answer = scheme + ":http://" + host;
        if (port != null) {
            answer = answer + ":" + port;
        }

        if (contextPath != null) {
            if (!contextPath.startsWith("/")) {
                contextPath = "/" + contextPath;
            }

            answer += contextPath;
        }

        return answer;
    }
}
