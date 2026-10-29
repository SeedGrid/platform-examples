package br.com.seedgrid.aipricingexample.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.seedgrid.aipricing.entities.AiModelEntity;
import br.com.seedgrid.aipricing.entities.AiModelPriceEntity;
import br.com.seedgrid.aipricing.entities.AiPricingConfigEntity;
import br.com.seedgrid.aipricing.repositories.AiModelEntityRepository;
import br.com.seedgrid.aipricing.repositories.AiModelPriceEntityRepository;
import br.com.seedgrid.aipricing.repositories.AiModelPriceToApproveEntityRepository;
import br.com.seedgrid.aipricing.repositories.AiPricingConfigEntityRepository;
import br.com.seedgrid.aipricing.services.AiPriceApprovalService;
import br.com.seedgrid.aipricing.services.AiPricingCheckService;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Este host NAO usa seguranca (a extension e agnostica): a REST administrativa da ai-pricing nao existe sem
 * {@code quarkus-security}, entao o proprio host conduz o fluxo chamando os servicos e repositorios da extension.
 */
@Path("/demo/ai-pricing")
@Produces(MediaType.APPLICATION_JSON)
public class PriceFlowDemoResource {

    public record ModelRequest(String provider, String model) {
    }

    public record SourceRequest(String sourceUrl) {
    }

    private final AiModelEntityRepository models;
    private final AiModelPriceEntityRepository prices;
    private final AiPricingConfigEntityRepository config;
    private final AiModelPriceToApproveEntityRepository proposals;
    private final AiPricingCheckService check;
    private final AiPriceApprovalService approval;

    public PriceFlowDemoResource(AiModelEntityRepository models, AiModelPriceEntityRepository prices,
            AiPricingConfigEntityRepository config, AiModelPriceToApproveEntityRepository proposals,
            AiPricingCheckService check, AiPriceApprovalService approval) {
        this.models = models;
        this.prices = prices;
        this.config = config;
        this.proposals = proposals;
        this.check = check;
        this.approval = approval;
    }

    @POST
    @Path("/models")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createModel(ModelRequest body) {
        AiModelEntity entity = new AiModelEntity();
        entity.setProvider(body.provider());
        entity.setModel(body.model());
        entity.setCurrency("USD");
        entity.setActive(true);
        models.persist(entity);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/models")
    public List<Map<String, Object>> listModels() {
        return models.listAll().stream().map(m -> {
            AiModelPriceEntity current = prices.current(m.getId());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("model", m.getModel());
            out.put("inputPricePerMillion", current == null ? null : current.getInputPricePerMillion());
            out.put("outputPricePerMillion", current == null ? null : current.getOutputPricePerMillion());
            return out;
        }).toList();
    }

    @PUT
    @Path("/source")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response saveSource(SourceRequest body) {
        AiPricingConfigEntity cfg = config.current();
        if (cfg == null) {
            cfg = new AiPricingConfigEntity();
            config.persist(cfg);
        }
        cfg.setSourceUrl(body.sourceUrl());
        return Response.ok().build();
    }

    /** O robo le a pagina de precos e PROPOE; o preco vigente do modelo nao muda. */
    @POST
    @Path("/check")
    public Map<String, Object> checkNow() {
        AiPricingCheckService.CheckOutcome outcome = check.runCheck();
        return Map.of("status", outcome.status(), "changedCount", outcome.changedCount());
    }

    @GET
    @Path("/proposals")
    public List<Map<String, Object>> listProposals() {
        return proposals.listAllNewestFirst().stream()
                .map(p -> Map.<String, Object>of("id", p.getPublicId(), "status", String.valueOf(p.getStatus())))
                .toList();
    }

    @POST
    @Path("/proposals/{id}/approve")
    public Response approve(@PathParam("id") String id) {
        approval.approve(id, "demo");
        return Response.ok().build();
    }
}
