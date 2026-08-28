package com.priceradar.feedback.application;

import com.priceradar.feedback.domain.FeedbackMessage;
import com.priceradar.user.application.UserProfile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class FeedbackService {

    private final FeedbackStore feedbackStore;
    private final PendingFeedbackInputStore pendingStore;
    private final Duration pendingTtl;
    private final int maxLength;
    private final Clock clock;

    public FeedbackService(FeedbackStore feedbackStore, PendingFeedbackInputStore pendingStore,
                           Duration pendingTtl, int maxLength, Clock clock) {
        if (feedbackStore == null || pendingStore == null || pendingTtl == null || clock == null
                || pendingTtl.isNegative() || pendingTtl.isZero() || maxLength <= 0) {
            throw new IllegalArgumentException("feedback service configuration is invalid");
        }
        this.feedbackStore = feedbackStore;
        this.pendingStore = pendingStore;
        this.pendingTtl = pendingTtl;
        this.maxLength = maxLength;
        this.clock = clock;
    }

    public void begin(UserProfile profile) {
        Instant now = clock.instant();
        pendingStore.replace(new PendingFeedbackInput(profile.getId(), profile.getTelegramUserId(),
                profile.getTelegramChatId(), now, now.plus(pendingTtl)));
    }

    public int getMaxLength() {
        return maxLength;
    }

    public Duration getPendingTtl() {
        return pendingTtl;
    }

    public Optional<PendingFeedbackInput> findPending(long telegramUserId, long chatId) {
        return pendingStore.find(telegramUserId, chatId);
    }

    public void cancel(long telegramUserId, long chatId) {
        pendingStore.remove(telegramUserId, chatId);
    }

    @Transactional
    public FeedbackSubmissionResult submit(UserProfile profile, String rawMessage) {
        Optional<PendingFeedbackInput> found = pendingStore.find(
                profile.getTelegramUserId(), profile.getTelegramChatId()
        );
        if (found.isEmpty()) return FeedbackSubmissionResult.of(FeedbackSubmissionResult.Status.NOT_PENDING);
        PendingFeedbackInput pending = found.orElseThrow();
        if (!pending.getUserId().equals(profile.getId())) {
            pendingStore.remove(profile.getTelegramUserId(), profile.getTelegramChatId());
            return FeedbackSubmissionResult.of(FeedbackSubmissionResult.Status.NOT_PENDING);
        }
        if (pending.isExpired(clock.instant())) {
            pendingStore.remove(profile.getTelegramUserId(), profile.getTelegramChatId());
            return FeedbackSubmissionResult.of(FeedbackSubmissionResult.Status.EXPIRED);
        }
        String normalized = rawMessage == null ? "" : rawMessage.trim();
        if (normalized.isBlank()) return FeedbackSubmissionResult.of(FeedbackSubmissionResult.Status.BLANK);
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            return FeedbackSubmissionResult.of(FeedbackSubmissionResult.Status.TOO_LONG);
        }
        FeedbackMessage feedback = new FeedbackMessage(UUID.randomUUID(), profile.getId(), normalized, clock.instant());
        feedbackStore.save(feedback);
        pendingStore.remove(profile.getTelegramUserId(), profile.getTelegramChatId());
        return FeedbackSubmissionResult.submitted(feedback);
    }
}
