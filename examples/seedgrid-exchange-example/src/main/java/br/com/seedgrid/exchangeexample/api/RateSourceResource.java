package br.com.seedgrid.exchangeexample.api;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Faz o papel da fonte de cotacao (um Banco Central, uma API de cambio): o robo da extension baixa este JSON e le o
 * valor pelo caminho configurado na moeda ({@code rateJsonPath}).
 */
@Path("/example/rate-source")
@PermitAll
public class RateSourceResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String rate() {
        return "{\"value\":[{\"cotacaoVenda\":5.1234,\"dataHoraCotacao\":\"2026-10-29 13:00:00.000\"}]}";
    }
}
