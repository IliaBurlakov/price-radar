package com.priceradar.telegram.application;

public final class TutorialAssetException extends RuntimeException {

    private final String resourcePath;

    public TutorialAssetException(String resourcePath, Throwable cause) {
        super("Could not load tutorial resource " + resourcePath, cause);
        this.resourcePath = resourcePath;
    }

    public String getResourcePath() {
        return resourcePath;
    }
}
