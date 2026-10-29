package br.com.seedgrid.citiesexample.resource;

import java.util.List;

import br.com.seedgrid.cities.dtos.CitiesDTOs.CityDto;
import br.com.seedgrid.cities.services.BrazilGeo;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Uso da fachada {@link BrazilGeo} pelo codigo do host (alem da REST pronta da extension). */
@Path("/api/geo")
@Produces(MediaType.APPLICATION_JSON)
public class GeoLookupResource {

    private final BrazilGeo geo;

    public GeoLookupResource(BrazilGeo geo) {
        this.geo = geo;
    }

    @GET
    @Path("/capital/{uf}")
    public CityDto capital(@PathParam("uf") String uf) {
        return geo.capitalOf(uf).orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/search")
    public List<CityDto> search(@QueryParam("q") String q, @QueryParam("uf") String uf) {
        return geo.searchByPrefix(q, uf, 10);
    }
}
