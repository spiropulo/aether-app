package com.aether.app.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SmsConfig {

    @Configuration
    @ConditionalOnProperty(name = "aether.sms.enabled", havingValue = "false", matchIfMissing = true)
    static class SmsDisabled {
        @Bean
        @Primary
        public SmsService smsService() {
            return new NoOpSmsService();
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "aether.sms.enabled", havingValue = "true")
    static class SmsEnabled {
        @Bean
        @Primary
        public SmsService smsService(
                WebClient.Builder webClientBuilder,
                @Value("${aether.sms.twilio-api-base-url:https://api.twilio.com}") String twilioApiBaseUrl,
                @Value("${aether.sms.twilio-account-sid}") String accountSid,
                @Value("${aether.sms.twilio-auth-token}") String authToken,
                @Value("${aether.sms.twilio-from-number}") String fromNumber) {
            String base = twilioApiBaseUrl != null ? twilioApiBaseUrl.trim() : "https://api.twilio.com";
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            WebClient client = webClientBuilder.baseUrl(base).build();
            return new TwilioSmsService(client, accountSid, authToken, fromNumber);
        }
    }
}
