package com.github.taymindis.camel.helper;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.MDC;

/**
 * TODO This package for camel-log component, please move to other place
 *
 */
public class AccessLogFormatter extends DefaultExchangeFormatter {

    public static final String logBody = "access_body";

    @Override
    public String format(Exchange exchange) {
        Message in = exchange.getIn();

        String host = in.getHeader("host", String.class);

        if(ObjectHelper.isNotEmpty(host)) {
            MDC.put("httpHost", host.replaceAll(":", "_"));
        }

        Object body = in.getBody();

        return String.format(
                "%s | " +
                             "%s | " +
                             "%s | " +
                             "%s | " +
                             "%s | " +
                             "%s | " +
                             "%s | " +
                             "%s | " +
                             "%s | " +
                             "%s"
                ,
                in.getHeader("CamelNettyRemoteAddress"),
                in.getHeader("CamelNettyLocalAddress"),
                in.getHeader("Origin"),
                in.getHeader("Referer"),
                in.getHeader(Exchange.HTTP_METHOD),
                in.getHeader(Exchange.HTTP_URI),
                in.getHeader("Content-Length"),
                in.getHeader(Exchange.HTTP_RESPONSE_CODE),
                in.getHeader("User-Agent"),
                body instanceof String ? body : ""
        //                 exchange.getProperty(logBody, String.class)
        );
    }
}
