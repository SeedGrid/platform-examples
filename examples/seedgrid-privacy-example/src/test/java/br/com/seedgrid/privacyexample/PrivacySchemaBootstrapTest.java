package br.com.seedgrid.privacyexample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.seedgrid.privacy.CryptoProvider;
import br.com.seedgrid.privacyexample.dto.CustomerRequest;
import br.com.seedgrid.privacyexample.entity.Customer;
import br.com.seedgrid.privacyexample.repository.CustomerRepository;
import br.com.seedgrid.privacyexample.service.CustomerService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prova que a migration V1000 (tabela {@code customers}) roda e que o mapeamento JPA da entidade
 * {@link Customer} — incluindo os campos-irmao de hash preenchidos pelo EntityListener da lib —
 * funciona fim-a-fim contra um Postgres real. Tambem confirma que o modo de criptografia esta'
 * LIGADO (config wired), pre-condicao para os demais testes.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class PrivacySchemaBootstrapTest {

    @Inject
    CustomerService service;

    @Inject
    CustomerRepository repository;

    @Test
    void encryptionEnabledFlagIsWired() {
        assertTrue(CryptoProvider.isEncryptionEnabled(),
                "seedgrid.privacy.encryption.enabled deve estar true para este exemplo");
    }

    @Test
    void schemaExistsAndEntityPersists() {
        long before = repository.count();

        Customer customer = service.create(new CustomerRequest(
                "Bootstrap Tester", "bootstrap@teste.com", "11122233344", "11955554444"));

        assertAll("Customer persistido com id e hashes de busca preenchidos pelo listener",
                () -> assertEquals(before + 1, repository.count()),
                () -> assertNotNull(customer.getId()),
                () -> assertNotNull(customer.getEmailHash(), "emailHash preenchido no @PrePersist"),
                () -> assertNotNull(customer.getCpfHash(), "cpfHash preenchido no @PrePersist"));

        Customer reloaded = repository.findById(customer.getId());
        assertAll("Leitura via JPA devolve a PII em claro (decifrada)",
                () -> assertEquals("Bootstrap Tester", reloaded.getName()),
                () -> assertEquals("bootstrap@teste.com", reloaded.getEmail()),
                () -> assertEquals("11122233344", reloaded.getCpf()));
    }
}
