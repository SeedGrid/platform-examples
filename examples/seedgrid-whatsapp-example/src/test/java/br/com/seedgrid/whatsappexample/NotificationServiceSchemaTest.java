package br.com.seedgrid.whatsappexample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.seedgrid.whatsappexample.entity.Notification;
import br.com.seedgrid.whatsappexample.entity.NotificationStatus;
import br.com.seedgrid.whatsappexample.repository.NotificationRepository;
import br.com.seedgrid.whatsappexample.service.NotificationService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prova que a migration V1000 (notifications) roda corretamente junto com o schema da propria
 * extension (que neste caso nao contribui nenhuma tabela, so' beans CDI) e que o mapeamento
 * JPA de {@link Notification} (incluindo os campos herdados de BaseEntity: publicId, active,
 * createdAt) funciona fim-a-fim contra um Postgres real. Testa o NotificationService diretamente
 * (sem passar pela camada HTTP), usando o mesmo {@link StubWhatsAppApi} da extension.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class NotificationServiceSchemaTest {

    @Inject
    NotificationService service;

    @Inject
    NotificationRepository repository;

    @BeforeEach
    void resetStub() {
        StubWhatsAppApi.reset();
    }

    @Test
    void send_success_mapsBaseEntityFieldsAndSentStatus() {
        long before = repository.count();

        Notification notification = service.send("11977777777", "Teste de schema");

        assertAll("Notification persistida com campos de BaseEntity preenchidos",
            () -> assertEquals(before + 1, repository.count()),
            () -> assertNotNull(notification.getId()),
            () -> assertNotNull(notification.getPublicId()),
            () -> assertTrue(notification.isActive()),
            () -> assertNotNull(notification.getCreatedAt()),
            () -> assertEquals(NotificationStatus.SENT, notification.getStatus()),
            () -> assertEquals("stub-message-id-123", notification.getProviderMessageId()),
            () -> assertEquals("example-instance", notification.getChannelId()),
            () -> assertNull(notification.getErrorMessage())
        );

        Notification reloaded = repository.findById(notification.getId());
        assertEquals("11977777777", reloaded.getPhoneNumber());
        assertEquals("Teste de schema", reloaded.getMessage());
    }

    @Test
    void send_providerFailure_persistsFailedStatusWithoutProviderMessageId() {
        StubWhatsAppApi.failNext = true;

        Notification notification = service.send("11966666666", "Vai falhar");

        assertAll("Notification persistida com status FAILED",
            () -> assertEquals(NotificationStatus.FAILED, notification.getStatus()),
            () -> assertNull(notification.getProviderMessageId()),
            () -> assertEquals("Simulated Evolution API outage", notification.getErrorMessage())
        );
    }
}
