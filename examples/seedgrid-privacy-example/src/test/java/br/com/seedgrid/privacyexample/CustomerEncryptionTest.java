package br.com.seedgrid.privacyexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

import br.com.seedgrid.privacy.HmacSha256SearchHashService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * Fluxo completo provando os quatro contratos da lib seedgrid-privacy:
 * <ol>
 *   <li>o valor GRAVADO no banco esta' cifrado ({@code v1:...}), nunca o CPF/e-mail cru;</li>
 *   <li>a LEITURA via JPA devolve o valor em claro (decifrado);</li>
 *   <li>a BUSCA por hash acha o registro — inclusive tolerando formatacao (CPF com pontos) e
 *       caixa (e-mail em maiuscula), gracas a normalizacao CANONICAL/EMAIL;</li>
 *   <li>o MASCARAMENTO (@SgPersonalData) aplica a regra de cada campo ao serializar a resposta.</li>
 * </ol>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CustomerEncryptionTest {

    // Mesma chave de busca do src/test/resources/application.properties — para recomputar o hash.
    private static final String SEARCH_KEY = "base64:YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXpBQkNERUY=";

    private static final String RAW_NAME = "Maria Silva";
    private static final String RAW_EMAIL_MIXED = "Maria.Silva@Teste.COM";
    private static final String RAW_EMAIL_NORMALIZED = "maria.silva@teste.com";
    private static final String RAW_CPF_FORMATTED = "111.222.333-44";
    private static final String RAW_CPF_DIGITS = "11122233344";
    private static final String RAW_PHONE = "11955554444";

    @Inject
    AgroalDataSource dataSource;

    @Test
    void encryptsAtRest_readsPlaintext_searchesByHash_andMasks() {
        // (0) cria via HTTP — a resposta ja' vem em claro (JPA decifra na leitura)
        long id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"%s","email":"%s","cpf":"%s","phone":"%s"}
                        """.formatted(RAW_NAME, RAW_EMAIL_MIXED, RAW_CPF_FORMATTED, RAW_PHONE))
                .when()
                .post("/api/customers")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("email", equalTo(RAW_EMAIL_MIXED))
                .body("cpf", equalTo(RAW_CPF_FORMATTED))
                .extract().jsonPath().getLong("id");

        // (1) o que esta' no banco esta' CIFRADO — lendo a coluna crua via JDBC
        RawRow raw = readRawRow(id);
        String expectedEmailHash = new HmacSha256SearchHashService(SEARCH_KEY).hash(RAW_EMAIL_NORMALIZED);

        assertAll("colunas de PII cifradas at-rest; hash de busca preenchido",
                () -> assertTrue(raw.name.startsWith("v1:"), "name cifrado: " + raw.name),
                () -> assertTrue(raw.email.startsWith("v1:"), "email cifrado: " + raw.email),
                () -> assertTrue(raw.cpf.startsWith("v1:"), "cpf cifrado: " + raw.cpf),
                () -> assertTrue(raw.phone.startsWith("v1:"), "phone cifrado: " + raw.phone),
                () -> assertNotEquals(RAW_CPF_FORMATTED, raw.cpf),
                () -> assertNotEquals(RAW_CPF_DIGITS, raw.cpf),
                () -> assertNotEquals(RAW_EMAIL_MIXED, raw.email),
                // o hash de busca NAO e' cifrado (e' o HMAC hex) e casa com o recomputado
                () -> assertNotNull(raw.emailHash),
                () -> assertEquals(64, raw.emailHash.length()),
                () -> assertEquals(expectedEmailHash, raw.emailHash),
                () -> assertNotNull(raw.cpfHash),
                () -> assertEquals(64, raw.cpfHash.length()));

        // (2) leitura via JPA (GET /{id}) devolve tudo em claro
        given().when().get("/api/customers/" + id)
                .then()
                .statusCode(200)
                .body("name", equalTo(RAW_NAME))
                .body("email", equalTo(RAW_EMAIL_MIXED))
                .body("cpf", equalTo(RAW_CPF_FORMATTED))
                .body("phone", equalTo(RAW_PHONE));

        // (3a) busca por e-mail em caixa diferente da gravada — normalizacao EMAIL acha
        given().when().get("/api/customers/search?email=" + RAW_EMAIL_NORMALIZED)
                .then()
                .statusCode(200)
                .body("id", equalTo((int) id));

        // (3b) busca por CPF so' com digitos (gravado com pontuacao) — normalizacao CANONICAL acha
        given().when().get("/api/customers/search?cpf=" + RAW_CPF_DIGITS)
                .then()
                .statusCode(200)
                .body("id", equalTo((int) id));

        // (3c) busca inexistente devolve 404
        given().when().get("/api/customers/search?email=naoexiste@x.com")
                .then().statusCode(404);

        // (4) mascaramento: /{id}/masked aplica a regra @SgPersonalData de cada campo
        given().when().get("/api/customers/" + id + "/masked")
                .then()
                .statusCode(200)
                .body("email", equalTo("***"))                 // FULL
                .body("cpf", equalTo("***.***.***-**"))        // FORMAT
                .body("name", equalTo("*******ilva"))          // PARTIAL 4 (revela "ilva")
                .body("phone", equalTo("*******4444"));        // PARTIAL 4 (revela "4444")
    }

    /**
     * {@code name} e {@code phone} usam {@code @SgEncryptedField} SEM {@code searchField}: desde a
     * lib 2026.07.08 o listener faz early-return — nao gera hash e nao exige campo-irmao
     * {@code <campo>Hash} (antes lancava). O persist sem erro ja' e' provado pelo POST do teste
     * principal; aqui provamos que NENHUMA coluna de hash existe (nem precisa existir) para eles.
     */
    @Test
    void encryptedFieldWithoutSearchField_persistsWithoutAnyHash() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Sem Hash","email":"sem.hash@teste.com","cpf":"99988877766","phone":"11911112222"}
                        """)
                .when()
                .post("/api/customers")
                .then()
                .statusCode(201);

        java.util.Set<String> columns = new java.util.HashSet<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'customers'");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler o schema da tabela customers", e);
        }

        assertAll("so' os campos COM searchField tem coluna de hash",
                () -> assertTrue(columns.contains("email_hash"), "email e' pesquisavel: " + columns),
                () -> assertTrue(columns.contains("cpf_hash"), "cpf e' pesquisavel: " + columns),
                () -> assertFalse(columns.contains("name_hash"), "name NAO deve ter hash: " + columns),
                () -> assertFalse(columns.contains("phone_hash"), "phone NAO deve ter hash: " + columns));
    }

    private RawRow readRawRow(long id) {
        String sql = "SELECT name, email, email_hash, cpf, cpf_hash, phone FROM customers WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "linha do customer deve existir no banco");
                RawRow row = new RawRow();
                row.name = rs.getString("name");
                row.email = rs.getString("email");
                row.emailHash = rs.getString("email_hash");
                row.cpf = rs.getString("cpf");
                row.cpfHash = rs.getString("cpf_hash");
                row.phone = rs.getString("phone");
                return row;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a linha crua do banco", e);
        }
    }

    private static final class RawRow {
        String name;
        String email;
        String emailHash;
        String cpf;
        String cpfHash;
        String phone;
    }
}
