package com.priceradar.feedback.infrastructure.configuration;

import com.priceradar.configuration.PriceRadarPolicyProperties;
import com.priceradar.feedback.application.FeedbackService;
import com.priceradar.feedback.application.FeedbackStore;
import com.priceradar.feedback.application.PendingFeedbackInputStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FeedbackProperties.class)
public class FeedbackConfiguration {
    @Bean
    public FeedbackService feedbackService(FeedbackStore feedbackStore,
                                           PendingFeedbackInputStore pendingStore,
                                           PriceRadarPolicyProperties policy,
                                           Clock providerClock) {
        return new FeedbackService(feedbackStore, pendingStore, policy.getPendingActionTtl(),
                policy.getFeedbackMaxLength(), providerClock);
    }
}
