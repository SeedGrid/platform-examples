package br.com.seedgrid.filemanagementexample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * A extension mapeia a entidade {@code Attachment} sozinha (plano 111, T6.1/D7): o host so' tem a dependencia e
 * {@code seedgrid.filemanagement.registry-table.enabled=true}, sem {@code mapping-files}, {@code packages} nem
 * {@code index-dependency}. Com a chave desligada (provado rodando com {@code -D...enabled=false}) a unidade de
 * persistencia fica sem entidade nenhuma.
 */
@QuarkusTest
class EntidadeMapeadaSozinhaTest {

    @Inject
    EntityManager em;

    @Test
    void attachmentEstaNaUnidadeDePersistenciaSemNenhumaConfiguracaoDeMapeamento() {
        assertThat(em.getMetamodel().getEntities()).extracting(e -> e.getName()).containsExactly("Attachment");
    }
}
