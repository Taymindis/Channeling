package com.github.taymindis.springboot.channeling.http.camel;

import com.github.taymindis.camel.channeling.http.component.ChannelingHttpComponent;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration(proxyBeanMethods = false)
public class ChannelingHttpComponentAutoRegistration {


    @Autowired
    private CamelContext camelContext;


    @PostConstruct
    public void init() {
        if (camelContext.getComponent("channeling") == null) {
            camelContext.addComponent("channeling", new ChannelingHttpComponent());
        }
    }


}
