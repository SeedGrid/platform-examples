package br.com.seedgrid.subdomaintenancyexample;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifica que as tabelas de audit em JPA (audit_log, audit_log_entities, audit_log_users) sao
 * criadas no banco master.
 *
 * <p><b>DESLIGADO em 2026-09-10</b> — este teste nao pode passar com o pom atual, e nao passava
 * antes tampouco: ele quebrava no boot, por falta de config, e a asserção nunca chegava a rodar.
 * Com a config corrigida (encryption.key, admin-user.*, mailers nomeados) o boot passou e a causa
 * de verdade apareceu.</p>
 *
 * <p><b>Por que nao pode passar:</b> o pom deste exemplo traz {@code seedgrid-audit-mongodb}, nao o
 * {@code seedgrid-audit-postgresql} — e' um exemplo de audit em MongoDB (ver o comentario no
 * application.properties: "Para JPA e preciso mexer no pom (audit-postgresql), nao basta a env
 * var"). O DDL {@code db/migration/audit} mora no jar do postgresql; sem ele o Flyway roda com zero
 * scripts, cria so' o {@code flyway_schema_history_audit} e loga "Migrations de audit executadas com
 * sucesso". Nenhuma tabela de audit aparece.</p>
 *
 * <p><b>Para reativar:</b> trocar {@code seedgrid-audit-mongodb} por
 * {@code seedgrid-audit-postgresql} no pom e por {@code seedgrid.audit.persistence.strategy=JPA} na
 * config — que e' outro exemplo, nao este.</p>
 */
@Disabled("Exemplo usa audit-mongodb; o DDL de audit em JPA esta no jar do audit-postgresql. Ver javadoc.")
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
        assertTrue(tables.contains("sg_audit_log"),
            "Table 'sg_audit_log' should exist in master database. Found tables: " + tables);
        assertTrue(tables.contains("sg_audit_log_entities"),
            "Table 'sg_audit_log_entities' should exist in master database. Found tables: " + tables);
        assertTrue(tables.contains("sg_audit_log_users"),
            "Table 'sg_audit_log_users' should exist in master database. Found tables: " + tables);
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
