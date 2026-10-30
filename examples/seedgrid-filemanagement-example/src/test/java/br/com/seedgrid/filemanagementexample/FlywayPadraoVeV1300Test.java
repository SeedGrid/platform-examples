package br.com.seedgrid.filemanagementexample;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * ACHADO A4, documentado com prova: um host que roda o Flyway do Quarkus com a location PADRAO ({@code db/migration})
 * enxerga a V1300 da lib como pendente, porque o Flyway varre as subpastas. Se ele tivesse
 * {@code quarkus.flyway.migrate-at-start=true} com essa location, aplicaria a V1300 e criaria {@code sg_attachments}
 * mesmo que o recurso estivesse desligado. Por isso o README manda listar as locations do host explicitamente.
 *
 * <p>Este teste so' LE ({@code info()}): usa um schema vazio proprio e nao migra nada.</p>
 */
@QuarkusTest
class FlywayPadraoVeV1300Test {

    @Inject
    DataSource dataSource;

    @Test
    void flywayComALocationPadraoVeAV1300ComoPendente() {
        Flyway padrao = Flyway.configure()
                .dataSource(dataSource)
                .schemas("a4_demonstracao")
                .defaultSchema("a4_demonstracao")
                .locations("classpath:db/migration") // a location padrao do Flyway do Quarkus
                .load();

        assertThat(padrao.info().pending()).extracting(MigrationInfo::getVersion)
                .extracting(Object::toString).contains("1300");
    }

    @Test
    void listandoAsLocationsDoHostAV1300NaoApareceEOHostFicaProtegido() {
        Flyway doHost = Flyway.configure()
                .dataSource(dataSource)
                .schemas("a4_demonstracao")
                .defaultSchema("a4_demonstracao")
                .locations("classpath:db/migration/app") // so' o que o host declarou
                .load();

        assertThat(doHost.info().pending()).extracting(MigrationInfo::getVersion)
                .extracting(Object::toString).doesNotContain("1300");
    }
}
