package com.priceradar.feedback.application;

import com.priceradar.feedback.domain.FeedbackMessage;
import com.priceradar.user.application.UserProfile;
import com.priceradar.user.domain.UserPricePreferences;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T03:00:00Z");

    @Test
    void validatesInputRetainsPendingAndPersistsValidUnicodeFeedback() {
        MemoryPendingStore pending = new MemoryPendingStore();
        MemoryFeedbackStore feedback = new MemoryFeedbackStore();
        FeedbackService service = service(pending, feedback, 4);
        UserProfile user = user();
        service.begin(user);

        assertThat(service.submit(user, "   ").getStatus()).isEqualTo(FeedbackSubmissionResult.Status.BLANK);
        assertThat(service.submit(user, "четыре").getStatus()).isEqualTo(FeedbackSubmissionResult.Status.TOO_LONG);
        assertThat(pending.value).isNotNull();

        FeedbackSubmissionResult result = service.submit(user, "  👍 ок  ");

        assertThat(result.getStatus()).isEqualTo(FeedbackSubmissionResult.Status.SUBMITTED);
        assertThat(feedback.value.getMessage()).isEqualTo("👍 ок");
        assertThat(feedback.saveCount).isEqualTo(1);
        assertThat(pending.value).isNull();
    }

    @Test
    void expiredPendingIsRemovedWithoutSavingFeedback() {
        MemoryPendingStore pending = new MemoryPendingStore();
        MemoryFeedbackStore feedback = new MemoryFeedbackStore();
        UserProfile user = user();
        pending.value = new PendingFeedbackInput(user.getId(), user.getTelegramUserId(),
                user.getTelegramChatId(), NOW.minusSeconds(20), NOW.minusSeconds(1));

        FeedbackSubmissionResult result = service(pending, feedback, 3000).submit(user, "текст");

        assertThat(result.getStatus()).isEqualTo(FeedbackSubmissionResult.Status.EXPIRED);
        assertThat(feedback.value).isNull();
        assertThat(pending.value).isNull();

        assertThat(service(pending, feedback, 3000).submit(user, "ещё раз").getStatus())
                .isEqualTo(FeedbackSubmissionResult.Status.NOT_PENDING);
        assertThat(feedback.value).isNull();
    }

    private FeedbackService service(MemoryPendingStore pending, MemoryFeedbackStore feedback, int max) {
        return new FeedbackService(feedback, pending, Duration.ofMinutes(15), max,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UserProfile user() {
        return new UserProfile(UUID.randomUUID(), 7001L, 7001L, null,
                UserPricePreferences.defaults());
    }

    private static final class MemoryFeedbackStore implements FeedbackStore {
        private FeedbackMessage value;
        private int saveCount;
        @Override public void save(FeedbackMessage feedback) { value = feedback; saveCount++; }
    }

    private static final class MemoryPendingStore implements PendingFeedbackInputStore {
        private PendingFeedbackInput value;
        @Override public void replace(PendingFeedbackInput pending) { value = pending; }
        @Override public Optional<PendingFeedbackInput> find(long userId, long chatId) {
            return value != null && value.getTelegramUserId() == userId && value.getChatId() == chatId
                    ? Optional.of(value) : Optional.empty();
        }
        @Override public void remove(long userId, long chatId) { value = null; }
    }
}
