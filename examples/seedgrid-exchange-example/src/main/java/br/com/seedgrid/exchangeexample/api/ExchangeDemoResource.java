package br.com.seedgrid.exchangeexample.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.seedgrid.exchange.dtos.CurrencyUpsertRequest;
import br.com.seedgrid.exchange.entities.CurrencyEntity;
import br.com.seedgrid.exchange.repositories.CurrencyEntityRepository;
import br.com.seedgrid.exchange.services.AdminCurrencyService;
import br.com.seedgrid.exchange.services.CurrencyRateFetchService;
import br.com.seedgrid.exchange.services.CurrencyRateHistoryService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Este host NAO usa seguranca (a extension e agnostica): a REST administrativa da exchange nao existe sem
 * {@code quarkus-security}, entao o proprio host conduz o fluxo chamando os servicos e repositorios da extension.
 */
@Path("/demo/exchange")
@Produces(MediaType.APPLICATION_JSON)
public class ExchangeDemoResource {

    private final AdminCurrencyService currencies;
    private final CurrencyEntityRepository currencyRepository;
    private final CurrencyRateFetchService fetch;
    private final CurrencyRateHistoryService history;

    public ExchangeDemoResource(AdminCurrencyService currencies, CurrencyEntityRepository currencyRepository,
            CurrencyRateFetchService fetch, CurrencyRateHistoryService history) {
        this.currencies = currencies;
        this.currencyRepository = currencyRepository;
        this.fetch = fetch;
        this.history = history;
    }

    @POST
    @Path("/currencies")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createCurrency(CurrencyUpsertRequest request) {
        currencies.create(request);
        return Response.status(Response.Status.CREATED).build();
    }

    /** Busca a cotacao de hoje na fonte da moeda e grava a linha do dia. */
    @POST
    @Path("/currencies/{code}/fetch")
    public Response fetchToday(@PathParam("code") String code) {
        fetch.fetchToday(require(code).getId());
        return Response.ok().build();
    }

    /** Serie das cotacoes gravadas (linhas com falha ficam de fora). */
    @GET
    @Path("/currencies/{code}/series")
    public List<Map<String, Object>> series(@PathParam("code") String code,
            @QueryParam("days") @jakarta.ws.rs.DefaultValue("7") int days) {
        return history.series(require(code).getId(), days).stream().map(point -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("date", point.date().toString());
            out.put("rate", point.rate());
            return out;
        }).toList();
    }

    private CurrencyEntity require(String code) {
        return currencyRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Moeda nao cadastrada: " + code));
    }
}
