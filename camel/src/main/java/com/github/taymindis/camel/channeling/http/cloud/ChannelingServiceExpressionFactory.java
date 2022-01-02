package com.github.taymindis.camel.channeling.http.cloud;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.cloud.ServiceExpressionFactory;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.CloudServiceFactory;

@CloudServiceFactory("channeling-service-expression")
@Configurer
public class ChannelingServiceExpressionFactory implements ServiceExpressionFactory {
    @Override
    public Expression newInstance(CamelContext camelContext) throws Exception {
        return new ChannelingServiceExpression();
    }
}