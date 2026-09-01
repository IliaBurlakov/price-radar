package com.priceradar.telegram.application;

import java.util.Arrays;

public final class TutorialAsset {

    private final String resourcePath;
    private final String filename;
    private final byte[] content;

    public TutorialAsset(String resourcePath, String filename, byte[] content) {
        this.resourcePath = resourcePath;
        this.filename = filename;
        this.content = Arrays.copyOf(content, content.length);
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }
}
