package br.com.seedgrid.subdomaintenancyexample;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Profile que sobe o app com a estrategia de audit MONGODB (a default dos testes e JPA).
 */
public class MongoAuditProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("seedgrid.audit.persistence.strategy", "MONGODB");
    }
}
