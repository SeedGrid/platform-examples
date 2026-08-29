package br.com.seedgrid.subdomaintenancyexample;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Test to verify that JPA audit tables are created in the master database.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
public class JpaAuditTablesCreationTest {

    @Test
    void testAuditTablesExistInMasterDatabase() throws Exception {
        // Get the master datasource
        DataSource masterDataSource = Arc.container()
            .instance(DataSource.class, new DataSourceLiteral("master"))
            .get();

        // Get tables in master database
        Set<String> tables = getTablesInDatabase(masterDataSource);

        System.out.println("Tables found in MASTER database: " + tables);

        // Verify audit tables exist
        assertTrue(tables.contains("audit_log"),
            "Table 'audit_log' should exist in master database. Found tables: " + tables);
        assertTrue(tables.contains("audit_log_entities"),
            "Table 'audit_log_entities' should exist in master database. Found tables: " + tables);
        assertTrue(tables.contains("audit_log_users"),
            "Table 'audit_log_users' should exist in master database. Found tables: " + tables);
    }

    private Set<String> getTablesInDatabase(DataSource dataSource) throws Exception {
        Set<String> tables = new HashSet<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"});

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME").toLowerCase();
                tables.add(tableName);
            }
        }

        return tables;
    }
}
