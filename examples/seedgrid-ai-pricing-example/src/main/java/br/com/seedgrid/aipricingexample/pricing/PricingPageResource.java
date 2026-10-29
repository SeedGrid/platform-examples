package br.com.seedgrid.aipricingexample.pricing;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Faz o papel da pagina de precos de um fornecedor: o robo da extension baixa esta pagina e le as linhas no formato
 * {@code [0,"modelo"],[0,entrada],[0,entrada em cache],[0,saida]} (precos por 1 milhao de tokens).
 */
@Path("/example/pricing-page")
@PermitAll
public class PricingPageResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String page() {
        return "<html><body><script>var rows = ["
                + "[0,\"acme-small\"],[0,0.15],[0,0.03],[0,0.60],"
                + "[0,\"acme-large\"],[0,2.50],[0,0.60],[0,10.00]"
                + "];</script></body></html>";
    }
}
