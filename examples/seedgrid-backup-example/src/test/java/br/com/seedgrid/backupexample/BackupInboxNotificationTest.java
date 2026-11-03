package br.com.seedgrid.backupexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.seedgrid.security.dtos.UserDTOReq;
import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.services.UserCreateOptions;
import br.com.seedgrid.security.services.UserService;
import br.com.seedgrid.security.repositories.UserRepository;
import br.com.seedgrid.usernotifications.services.UserNotificationsCleanupJob;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Host com security + backup + user-notifications (so as dependencias): quando o export termina, o usuario que o pediu
 * encontra o aviso na caixa dele ({@code GET /user-notifications}), com o {@code exportPublicId} no payload. O download
 * continua pela rota do proprio backup. Prova tambem que a caixa so exige estar logado (sem permissao propria,
 * {@code @Authenticated}): sem token a rota responde 401.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class BackupInboxNotificationTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Inject
    UserRepository userRepository;
    @Inject
    UserService userService;
    @Inject
    br.com.seedgrid.usernotifications.services.UserNotifier notifier;
    @Inject
    EntityManager em;
    @Inject
    Scheduler scheduler;

    @BeforeEach
    void confirmBootstrapAdminEmail() {
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    @Test
    void finishedExportLeavesANotificationInTheRequesterInbox() throws InterruptedException {
        String token = login();

        String jobId = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("format", "CSV_ZIP", "scope", "RAW"))
                .when().post("/exports").then().statusCode(201).extract().path("jobId");
        assertNotNull(jobId);

        List<Map<String, Object>> items = awaitInbox(token, jobId);

        Map<String, Object> notification = items.get(0);
        assertEquals("backup.finished", notification.get("type"));
        assertEquals(Boolean.FALSE, notification.get("read"));

        given().header("Authorization", "Bearer " + token)
                .when().post("/user-notifications/" + notification.get("publicId") + "/read")
                .then().statusCode(204);
        given().header("Authorization", "Bearer " + token)
                .when().get("/user-notifications/unread-count").then().statusCode(200).body("count", equalTo(0));
    }

    @Test
    void inboxRoutesRequireALoggedInUser() {
        given().when().get("/user-notifications").then().statusCode(401);
        given().when().get("/user-notifications/unread-count").then().statusCode(401);
        given().when().post("/user-notifications/read-all").then().statusCode(401);
        given().when().delete("/user-notifications").then().statusCode(401);
    }

    private static final String PLAIN_PASSWORD = "Plain@12345x";

    /** Cria usuario numa role SEM nenhuma permissao e devolve {publicId, token}. */
    private String[] plainUser() {
        String email = "plain-" + java.util.UUID.randomUUID() + "@test.com";
        String[] publicId = new String[1];
        QuarkusTransaction.requiringNew().run(() -> {
            String rolePublicId = java.util.UUID.randomUUID().toString();
            em.createNativeQuery("INSERT INTO sg_roles (name, description, public_id) VALUES ('EMPTY_ROLE_" + rolePublicId.substring(0, 8)
                    + "', 'sem permissoes', '" + rolePublicId + "')").executeUpdate();
            UserDTOReq dto = new UserDTOReq();
            dto.setEmail(email);
            dto.setName("Usuario Comum");
            dto.setPassword(PLAIN_PASSWORD);
            dto.setWhatsApp("+5511987654321");
            dto.setRolePublicIds(List.of(rolePublicId));
            User user = userService.createUser(dto, UserCreateOptions.defaults().withoutDelivery());
            user.confirmEmail();
            user.clearChangePasswordRequired();
            publicId[0] = user.getPublicId();
        });
        String token = given().contentType(ContentType.JSON)
                .body("{\"login\":\"" + email + "\",\"password\":\"" + PLAIN_PASSWORD + "\"}")
                .when().post("/public/auth/login").then().statusCode(200).body("status", equalTo("OK"))
                .extract().path("token");
        return new String[] { publicId[0], token };
    }

    /** Usuario so' logado, numa role SEM nenhuma permissao: ainda assim usa a propria caixa (basta estar logado). */
    @Test
    void loggedInUserWithoutAnyPermissionUsesTheOwnInbox() {
        String token = plainUser()[1];

        given().header("Authorization", "Bearer " + token).when().get("/user-notifications").then().statusCode(200)
                .body("total", equalTo(0));
        given().header("Authorization", "Bearer " + token).when().get("/user-notifications/unread-count")
                .then().statusCode(200).body("count", equalTo(0));
        // a rota protegida por permissao continua negando a esse mesmo usuario (prova que ele nao tem nenhuma)
        given().header("Authorization", "Bearer " + token).when().get("/exports").then().statusCode(403);
    }

    /** Regra do dono: um usuario NUNCA ve nem mexe na notificacao de outro (com security, tokens reais). */
    @Test
    void oneUserNeverSeesOrTouchesAnotherUsersNotifications() {
        String[] a = plainUser();
        String[] b = plainUser();
        String bobsNotification = notifier.notify(b[0], "t.b", "Do B", null, null);
        notifier.notify(a[0], "t.a", "Do A", null, null);
        String authA = "Bearer " + a[1];
        String authB = "Bearer " + b[1];

        given().header("Authorization", authA).when().get("/user-notifications").then().statusCode(200)
                .body("total", equalTo(1)).body("items[0].title", equalTo("Do A"));
        given().header("Authorization", authA).when().get("/user-notifications/unread-count").then().body("count", equalTo(1));
        given().header("Authorization", authA).when().post("/user-notifications/" + bobsNotification + "/read")
                .then().statusCode(404);
        given().header("Authorization", authA).when().delete("/user-notifications/" + bobsNotification)
                .then().statusCode(404);
        // o cliente nao escolhe o usuario
        given().header("Authorization", authA).queryParam("userPublicId", b[0]).when().get("/user-notifications")
                .then().statusCode(200).body("items[0].title", equalTo("Do A"));
        given().header("Authorization", authA).when().post("/user-notifications/read-all").then().statusCode(200);
        given().header("Authorization", authA).when().delete("/user-notifications").then().statusCode(200);

        given().header("Authorization", authB).when().get("/user-notifications/unread-count").then().body("count", equalTo(1));
        given().header("Authorization", authB).when().get("/user-notifications").then().statusCode(200)
                .body("items[0].title", equalTo("Do B"));
    }

    /** Host COM quarkus-scheduler (o backup traz): a varredura diaria das vencidas fica agendada. */
    @Test
    void dailyCleanupJobIsScheduled() {
        org.junit.jupiter.api.Assertions.assertNotNull(scheduler.getScheduledJob(UserNotificationsCleanupJob.IDENTITY),
                "varredura diaria da user-notifications nao foi agendada");
    }

    private List<Map<String, Object>> awaitInbox(String token, String jobId) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            List<Map<String, Object>> items = given().header("Authorization", "Bearer " + token)
                    .when().get("/user-notifications?type=backup.finished").then().statusCode(200)
                    .extract().path("items.findAll { it.payload.exportPublicId == '" + jobId + "' }");
            if (items != null && !items.isEmpty()) {
                return items;
            }
            Thread.sleep(250);
        }
        return fail("Nenhuma notificacao backup.finished para o job " + jobId + " em " + TIMEOUT);
    }

    private String login() {
        return given().contentType(ContentType.JSON)
                .body("{\"login\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .when().post("/public/auth/login").then().statusCode(200).body("status", equalTo("OK"))
                .extract().path("token");
    }
}
