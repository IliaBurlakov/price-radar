package com.priceradar.telegram.application;

import java.util.Arrays;
import java.util.List;

public final class OutgoingTelegramMediaGroup {

    private static final int MIN_PHOTOS = 2;
    private static final int MAX_PHOTOS = 10;
    private static final int MAX_CAPTION_CODE_POINTS = 1024;

    private final long chatId;
    private final String caption;
    private final List<Photo> photos;

    public OutgoingTelegramMediaGroup(long chatId, String caption, List<Photo> photos) {
        if (chatId <= 0) {
            throw new IllegalArgumentException("chatId must be positive");
        }
        if (caption == null || caption.isBlank()) {
            throw new IllegalArgumentException("media group caption must not be blank");
        }
        String normalizedCaption = caption.trim();
        if (normalizedCaption.codePointCount(0, normalizedCaption.length())
                > MAX_CAPTION_CODE_POINTS) {
            throw new IllegalArgumentException("media group caption must fit Telegram limit");
        }
        if (photos == null || photos.size() < MIN_PHOTOS || photos.size() > MAX_PHOTOS
                || photos.stream().anyMatch(photo -> photo == null)) {
            throw new IllegalArgumentException("media group must contain between 2 and 10 photos");
        }
        this.chatId = chatId;
        this.caption = normalizedCaption;
        this.photos = List.copyOf(photos);
    }

    public long getChatId() {
        return chatId;
    }

    public String getCaption() {
        return caption;
    }

    public List<Photo> getPhotos() {
        return photos;
    }

    public static final class Photo {

        private final String filename;
        private final byte[] content;

        public Photo(String filename, byte[] content) {
            if (filename == null || !filename.matches("[A-Za-z0-9._-]+\\.png")) {
                throw new IllegalArgumentException("photo filename must be a safe PNG filename");
            }
            if (content == null || content.length == 0) {
                throw new IllegalArgumentException("photo content must not be empty");
            }
            this.filename = filename;
            this.content = Arrays.copyOf(content, content.length);
        }

        public String getFilename() {
            return filename;
        }

        public byte[] getContent() {
            return Arrays.copyOf(content, content.length);
        }
    }
}
