package br.com.seedgrid.subdomaintenancyexample;

import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.MongoDBContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Sobe um MongoDB real (Testcontainers) para o teste de audit com estrategia MONGODB.
 * Aponta o cliente Mongo do Quarkus e a connection-string do audit-mongodb para o container.
 */
public class MongoTestResource implements QuarkusTestResourceLifecycleManager {

    @SuppressWarnings("resource")
    private final MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Override
    public Map<String, String> start() {
        mongo.start();
        Map<String, String> config = new HashMap<>();
        config.put("quarkus.mongodb.connection-string", mongo.getConnectionString());
        config.put("seedgrid.audit.mongodb.connection-string", mongo.getConnectionString());
        return config;
    }

    @Override
    public void stop() {
        mongo.stop();
    }
}
