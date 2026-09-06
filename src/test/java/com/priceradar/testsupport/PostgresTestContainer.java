package com.priceradar.testsupport;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgresTestContainer {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17.11-alpine");

    private PostgresTestContainer() {
    }

    public static PostgreSQLContainer<?> create() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }
}
