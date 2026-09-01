package com.priceradar.telegram.application;

public enum TutorialPlatform {
    IPHONE("iphone"),
    ANDROID("android"),
    WINDOWS("windows");

    private final String resourceDirectory;

    TutorialPlatform(String resourceDirectory) {
        this.resourceDirectory = resourceDirectory;
    }

    public String getResourceDirectory() {
        return resourceDirectory;
    }
}
