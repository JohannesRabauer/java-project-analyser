package dev.analyser.adapter.out.persistence;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgreSqlTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> postgresql;

    @Override
    public Map<String, String> start() {
        postgresql = new PostgreSQLContainer<>("postgres:17");
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
            postgresql.stop();
        }
    }
}
