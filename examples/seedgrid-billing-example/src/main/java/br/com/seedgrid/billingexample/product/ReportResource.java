package br.com.seedgrid.billingexample.product;

import java.time.Instant;
import java.util.Map;

import br.com.seedgrid.billing.annotations.SgConsumesUsage;
import br.com.seedgrid.billing.annotations.SgRequiresPlanFeature;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * O "produto" do exemplo. Todo o billing dele sao as duas anotacoes: nenhuma chamada, nenhum service.
 *
 * <ul>
 * <li>{@code POST /api/reports} gasta 1 unidade do contador {@code reports} (declarado em
 * {@link ReportUsageMeters}). Sem assinatura vigente, trial vencido, contador fora do plano ou franquia
 * esgotada: 402 com o {@code code} do motivo. Se o metodo falhar, nao computa.</li>
 * <li>{@code GET /api/reports/export} so' abre se o plano liberar a permissao {@value #EXPORT}: 402
 * {@code FEATURE_NOT_IN_PLAN} caso contrario.</li>
 * </ul>
 */
@Path("/api/reports")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ReportResource {

    public static final String EXPORT = "REPORT_EXPORT";

    @POST
    @SgConsumesUsage(meter = ReportUsageMeters.REPORTS)
    public Map<String, Object> generate() {
        return Map.of("status", "GENERATED", "generatedAt", Instant.now());
    }

    @GET
    @Path("/export")
    @SgRequiresPlanFeature(value = EXPORT, message = "Exportar relatorios faz parte do plano Pro.")
    public Map<String, Object> export() {
        return Map.of("status", "EXPORTED", "format", "pdf");
    }
}
