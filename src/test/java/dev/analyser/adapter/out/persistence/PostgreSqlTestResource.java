package dev.analyser.adapter.out.persistence;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgreSqlTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> postgresql;

    @Override
    public Map<String, String> start() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException("Docker environment is not available! Tests require a running Docker daemon to spin up the PostgreSQL Testcontainer.");
        }

        System.out.println("Docker is available. Starting pgvector PostgreSQL Testcontainer...");
        postgresql = new PostgreSQLContainer<>("pgvector/pgvector:pg17");
        postgresql.withDatabaseName("analyser");
        postgresql.withUsername("analyser");
        postgresql.withPassword("analyser");
        postgresql.start();

        return Map.of(
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.datasource.jdbc.url", postgresql.getJdbcUrl(),
                "quarkus.datasource.username", postgresql.getUsername(),
                "quarkus.datasource.password", postgresql.getPassword());
    }

    @Override
    public void stop() {
        if (postgresql != null) {
            try {
                postgresql.stop();
            } catch (Throwable t) {
                // ignore
            }
        }
    }
}
