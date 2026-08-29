import java.nio.file.*;
import java.security.*;
import java.util.Base64;

/**
 * Gera o par de chaves RSA que o seedgrid-security usa para assinar o JWT.
 *
 * Java puro, sem openssl e sem dependencia nenhuma: roda com
 * `java scripts/GerarChavesJwt.java <diretorio-destino>` (JDK 11+ executa
 * arquivo .java direto).
 *
 * O pom de cada exemplo chama isto automaticamente antes do `quarkus:dev`, entao
 * na pratica voce nunca precisa rodar na mao. Se as chaves ja existirem, nada
 * acontece — nao sobrescreve.
 *
 * As chaves NAO sao versionadas: chave privada em repositorio publico e' um
 * convite a copiar pra producao.
 */
public class GerarChavesJwt {

    public static void main(String[] args) throws Exception {
        Path destino = Path.of(args.length > 0 ? args[0] : "src/main/resources/keys");
        Path privada = destino.resolve("private.pem");
        Path publica = destino.resolve("public.pem");

        if (Files.exists(privada) && Files.exists(publica)) {
            System.out.println("[chaves-jwt] ja existem em " + destino + ", mantendo");
            return;
        }

        Files.createDirectories(destino);

        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        KeyPair par = gerador.generateKeyPair();

        Files.writeString(privada, pem("PRIVATE KEY", par.getPrivate().getEncoded()));
        Files.writeString(publica, pem("PUBLIC KEY", par.getPublic().getEncoded()));

        System.out.println("[chaves-jwt] geradas em " + destino);
    }

    /** Envelopa os bytes DER no formato PEM, quebrando em linhas de 64 colunas. */
    private static String pem(String tipo, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, System.lineSeparator().getBytes()).encodeToString(der);
        return "-----BEGIN " + tipo + "-----" + System.lineSeparator()
                + base64 + System.lineSeparator()
                + "-----END " + tipo + "-----" + System.lineSeparator();
    }
}
