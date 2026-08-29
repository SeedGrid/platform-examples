package br.com.seedgrid.securityexample;

import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Sobe um Postgres real (Testcontainers) para os testes de integracao do seedgrid-security.
 * Aponta o datasource default E o datasource standalone do audit-JPA para o mesmo container.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    @SuppressWarnings("resource")
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("security_test")
            .withUsername("test")
            .withPassword("test");

    @Override
    public Map<String, String> start() {
        postgres.start();
        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", postgres.getJdbcUrl());
        config.put("quarkus.datasource.username", postgres.getUsername());
        config.put("quarkus.datasource.password", postgres.getPassword());
        // audit-JPA standalone usa o mesmo container
        config.put("seedgrid.audit.jpa.jdbc.url", postgres.getJdbcUrl());
        config.put("seedgrid.audit.jpa.username", postgres.getUsername());
        config.put("seedgrid.audit.jpa.password", postgres.getPassword());
        return config;
    }

    @Override
    public void stop() {
        postgres.stop();
    }
}
