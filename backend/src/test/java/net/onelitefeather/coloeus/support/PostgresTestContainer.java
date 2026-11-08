package net.onelitefeather.coloeus.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a fresh Postgres 18 container, the same major version as docker/docker-compose.yml.
 */
public final class PostgresTestContainer {

    public static final DockerImageName IMAGE = DockerImageName.parse("postgres:18");

    private PostgresTestContainer() {
    }

    public static PostgreSQLContainer start() {
        PostgreSQLContainer container = new PostgreSQLContainer(IMAGE);
        container.start();
        return container;
    }
}
