package br.com.seedgrid.subdomaintenancyexample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;

import br.com.seedgrid.audit.core.entities.AuditLog;
import br.com.seedgrid.audit.core.enums.AuditOperationEnum;
import br.com.seedgrid.audit.core.repositories.AuditLogRepository;
import br.com.seedgrid.subdomaintenancy.configs.TenantContext;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Test to verify that MongoDB databases are created automatically
 * when audit operations are performed.
 */
@QuarkusTest
@TestProfile(MongoAuditProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
@QuarkusTestResource(MongoTestResource.class)
public class MongoAuditCreationTest {

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    TenantContext tenantContext;

    @Inject
    MongoClient mongoClient;

    @Test
    void testMongoDatabaseCreationForMasterTenant() {
        // Set master tenant
        tenantContext.setCurrentTenant("master");

        // List databases before operation
        List<String> databasesBefore = new ArrayList<>();
        mongoClient.listDatabaseNames().into(databasesBefore);
        System.out.println("MongoDB databases BEFORE operation: " + databasesBefore);

        // Create an audit log directly
        AuditLog auditLog = new AuditLog();
        auditLog.setEntity("TestEntity");
        auditLog.setOperation(AuditOperationEnum.CREATE);
        auditLog.setDescription("Test audit for master tenant");
        auditLog.setUserName("test.user");
        auditLog.setOperationDateTime(Instant.now());
        auditLog.setAfterState("{\"test\":\"data\"}");

        auditLogRepository.persist(auditLog);

        // List databases after operation
        List<String> databasesAfter = new ArrayList<>();
        mongoClient.listDatabaseNames().into(databasesAfter);
        System.out.println("MongoDB databases AFTER operation: " + databasesAfter);

        // Verify master audit database was created
        // Convencao nova do audit-mongodb: kb_{tenant}_audit_db
        String expectedMasterDb = "kb_master_audit_db";
        assertTrue(databasesAfter.contains(expectedMasterDb),
            "Master audit database '" + expectedMasterDb + "' should be created. Found: " + databasesAfter);

        // Verify audit log exists
        long auditCount = auditLogRepository.count();
        assertTrue(auditCount > 0, "Audit log should contain at least one entry");
    }

    @Test
    void testMongoDatabaseCreationForTenant() {
        // Set a tenant
        String tenantId = "testcompany";
        tenantContext.setCurrentTenant(tenantId);

        // List databases before operation
        List<String> databasesBefore = new ArrayList<>();
        mongoClient.listDatabaseNames().into(databasesBefore);
        System.out.println("MongoDB databases BEFORE tenant operation: " + databasesBefore);

        // Create an audit log directly
        AuditLog auditLog = new AuditLog();
        auditLog.setEntity("TestEntity");
        auditLog.setOperation(AuditOperationEnum.CREATE);
        auditLog.setDescription("Test audit for tenant");
        auditLog.setUserName("test.user");
        auditLog.setOperationDateTime(Instant.now());
        auditLog.setAfterState("{\"test\":\"data\"}");

        auditLogRepository.persist(auditLog);

        // List databases after operation
        List<String> databasesAfter = new ArrayList<>();
        mongoClient.listDatabaseNames().into(databasesAfter);
        System.out.println("MongoDB databases AFTER tenant operation: " + databasesAfter);

        // Verify tenant audit database was created
        String expectedTenantDb = "kb_testcompany_audit_db";
        assertTrue(databasesAfter.contains(expectedTenantDb),
            "Tenant audit database '" + expectedTenantDb + "' should be created. Found: " + databasesAfter);

        // Verify audit log exists
        long auditCount = auditLogRepository.count();
        assertTrue(auditCount > 0, "Audit log should contain at least one entry for tenant");
    }
}
