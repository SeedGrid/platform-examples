package br.com.seedgrid.billingexample.product;

import java.util.List;

import br.com.seedgrid.billing.spis.UsageMeterContributor;
import br.com.seedgrid.billing.spis.UsageMeterDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * O que ESTE produto conta: relatorios gerados. E' a unica declaracao de contador de uso que o host faz; o
 * billing guarda a franquia e o preco do excedente de cada contador por plano, e faz ciclo, bloqueio e
 * cobranca sozinho.
 *
 * <p>Outro contador (ex.: MB armazenados) e' so' mais uma linha aqui, contada com
 * {@code @SgConsumesUsage(meter = "...")} ou com {@code UsageService.consume(...)}. A demonstracao com dois
 * contadores esta nos boilerplates.</p>
 */
@ApplicationScoped
public class ReportUsageMeters implements UsageMeterContributor {

    public static final String REPORTS = "reports";

    @Override
    public List<UsageMeterDefinition> meters() {
        return List.of(new UsageMeterDefinition(REPORTS, "Relatorios"));
    }
}
