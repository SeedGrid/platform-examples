package br.com.seedgrid.citiesexample;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Postgres para os testes: Testcontainers por padrao, ou {@code -Dcities.test.jdbc-url=jdbc:postgresql://...}
 * (com {@code cities.test.username}/{@code cities.test.password}, padrao {@code test}) para um Postgres ja de pe.
 * Nesse modo o schema {@code public} e recriado a cada inicio.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> postgres;

    @Override
    @SuppressWarnings("resource")
    public Map<String, String> start() {
        String url = System.getProperty("cities.test.jdbc-url");
        String user = System.getProperty("cities.test.username", "test");
        String password = System.getProperty("cities.test.password", "test");
        if (url == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cities_example_test").withUsername("test").withPassword("test");
            postgres.start();
            url = postgres.getJdbcUrl();
            user = postgres.getUsername();
            password = postgres.getPassword();
        }
        try (Connection c = DriverManager.getConnection(url, user, password); Statement s = c.createStatement()) {
            s.execute("drop schema public cascade");
            s.execute("create schema public");
        } catch (Exception e) {
            throw new IllegalStateException("Could not reset the test schema", e);
        }
        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", url);
        config.put("quarkus.datasource.username", user);
        config.put("quarkus.datasource.password", password);
        return config;
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
