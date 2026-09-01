package com.priceradar.telegram.application;

public enum TutorialTopic {
    PRODUCT("product"),
    BASKET("basket");

    private final String resourceDirectory;

    TutorialTopic(String resourceDirectory) {
        this.resourceDirectory = resourceDirectory;
    }

    public String getResourceDirectory() {
        return resourceDirectory;
    }
}
