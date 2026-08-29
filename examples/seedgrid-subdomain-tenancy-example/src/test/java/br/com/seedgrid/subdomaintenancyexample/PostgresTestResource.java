package br.com.seedgrid.subdomaintenancyexample;

import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Sobe um Postgres real (Testcontainers) para os testes de integracao do app multitenancy.
 * Aponta o datasource master (Quarkus + bridge seedgrid) e o pattern de tenant para o mesmo
 * container — o provisionamento faz CREATE DATABASE por tenant nele.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    @SuppressWarnings("resource")
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("master")
            .withUsername("test")
            .withPassword("test");

    @Override
    public Map<String, String> start() {
        postgres.start();
        String host = postgres.getHost();
        Integer port = postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);

        Map<String, String> config = new HashMap<>();
        // datasource master (Quarkus nomeado)
        config.put("quarkus.datasource.master.jdbc.url", postgres.getJdbcUrl());
        config.put("quarkus.datasource.master.username", postgres.getUsername());
        config.put("quarkus.datasource.master.password", postgres.getPassword());
        // bridge seedgrid do master
        config.put("seedgrid.subdomain-tenancy.master.datasource.jdbc.url", postgres.getJdbcUrl());
        config.put("seedgrid.subdomain-tenancy.master.datasource.username", postgres.getUsername());
        config.put("seedgrid.subdomain-tenancy.master.datasource.password", postgres.getPassword());
        // tenant datasource: base URL (o {tenant} e adicionado automaticamente) -> mesmo container
        config.put("seedgrid.subdomain-tenancy.tenant.datasource.jdbc.url", "jdbc:postgresql://" + host + ":" + port);
        config.put("seedgrid.subdomain-tenancy.tenant.datasource.username", postgres.getUsername());
        config.put("seedgrid.subdomain-tenancy.tenant.datasource.password", postgres.getPassword());
        return config;
    }

    @Override
    public void stop() {
        postgres.stop();
    }
}
