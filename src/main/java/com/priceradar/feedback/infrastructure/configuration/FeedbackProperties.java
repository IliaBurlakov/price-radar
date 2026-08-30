package com.priceradar.feedback.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "priceradar.feedback")
public final class FeedbackProperties {
    private long recipientChatId;

    public long getRecipientChatId() { return recipientChatId; }
    public void setRecipientChatId(long recipientChatId) {
        if (recipientChatId < 0) throw new IllegalArgumentException("feedback recipient chat id must not be negative");
        this.recipientChatId = recipientChatId;
    }
}
