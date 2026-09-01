package com.priceradar.telegram.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class TutorialAssetCatalog {

    private static final String ROOT = "telegram/tutorial";
    private static final int STEP_COUNT = 3;

    private final ClassLoader classLoader;

    public TutorialAssetCatalog() {
        this(TutorialAssetCatalog.class.getClassLoader());
    }

    TutorialAssetCatalog(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader must not be null");
        }
        this.classLoader = classLoader;
    }

    public List<TutorialAsset> load(TutorialTopic topic, TutorialPlatform platform) {
        if (topic == null || platform == null) {
            throw new IllegalArgumentException("tutorial topic and platform must not be null");
        }
        List<TutorialAsset> assets = new ArrayList<>();
        for (int step = 1; step <= STEP_COUNT; step++) {
            String filename = "step-" + step + ".png";
            String path = ROOT + "/" + topic.getResourceDirectory() + "/"
                    + platform.getResourceDirectory() + "/" + filename;
            assets.add(load(path, filename));
        }
        return List.copyOf(assets);
    }

    private TutorialAsset load(String path, String filename) {
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("resource does not exist");
            }
            return new TutorialAsset(path, filename, input.readAllBytes());
        } catch (IOException exception) {
            throw new TutorialAssetException(path, exception);
        }
    }
}
