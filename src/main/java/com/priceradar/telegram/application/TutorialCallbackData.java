package com.priceradar.telegram.application;

import java.util.Optional;

public final class TutorialCallbackData {

    private static final String PREFIX = "TUTORIAL:";

    public enum Action {
        OPEN,
        SHOW
    }

    private final TutorialTopic topic;
    private final Action action;
    private final TutorialPlatform platform;

    private TutorialCallbackData(
            TutorialTopic topic,
            Action action,
            TutorialPlatform platform
    ) {
        this.topic = topic;
        this.action = action;
        this.platform = platform;
    }

    public static String open(TutorialTopic topic) {
        if (topic == null) {
            throw new IllegalArgumentException("tutorial topic must not be null");
        }
        return PREFIX + topic.name() + ":OPEN";
    }

    public static String show(TutorialTopic topic, TutorialPlatform platform) {
        if (topic == null || platform == null) {
            throw new IllegalArgumentException("tutorial topic and platform must not be null");
        }
        return PREFIX + topic.name() + ":" + platform.name();
    }

    public static Optional<TutorialCallbackData> parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !parts[0].equals("TUTORIAL")) {
            return Optional.empty();
        }
        try {
            TutorialTopic topic = TutorialTopic.valueOf(parts[1]);
            if (parts[2].equals("OPEN")) {
                return Optional.of(new TutorialCallbackData(topic, Action.OPEN, null));
            }
            TutorialPlatform platform = TutorialPlatform.valueOf(parts[2]);
            return Optional.of(new TutorialCallbackData(topic, Action.SHOW, platform));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public TutorialTopic getTopic() {
        return topic;
    }

    public Action getAction() {
        return action;
    }

    public Optional<TutorialPlatform> getPlatform() {
        return Optional.ofNullable(platform);
    }
}
