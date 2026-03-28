package com.aether.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentOutboundAuthConfiguration {

    @Bean
    public AgentGoogleIdTokenFilter agentGoogleIdTokenFilter(
            @org.springframework.beans.factory.annotation.Value("${aether.agent.id-token.audience:}") String audience) {
        return new AgentGoogleIdTokenFilter(audience != null ? audience : "");
    }
}
