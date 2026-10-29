package br.com.seedgrid.aipricingexample.pricing;

import br.com.seedgrid.aipricing.spis.AiPriceExtractor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extrator do host. Um produto de IA chamaria aqui o LLM dele (com o prompt {@code /ai-pricing/prompts/pricing-extract.md});
 * o exemplo devolve {@code []}, porque a pagina servida por {@link PricingPageResource} e' estruturada e o robo a le sozinho,
 * sem chamar o extrator.
 */
@ApplicationScoped
public class FakePriceExtractor implements AiPriceExtractor {

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String extract(String pricingPageContent) {
        return "[]";
    }
}
