package br.com.seedgrid.businessdaysexample.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.seedgrid.businessdays.dtos.BusinessDaysDTOs.HolidayRequest;
import br.com.seedgrid.businessdays.services.BusinessDayService;
import br.com.seedgrid.businessdays.services.HolidayService;
import br.com.seedgrid.businessdays.utils.BusinessCalendar.DayInfo;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Este host NAO usa seguranca (a extension e agnostica): a REST administrativa da business-days nao existe sem
 * {@code quarkus-security}, entao o proprio host expoe o que quer, chamando os servicos da extension.
 */
@Path("/demo/business-days")
@Produces(MediaType.APPLICATION_JSON)
public class CalendarDemoResource {

    private final BusinessDayService businessDays;
    private final HolidayService holidays;

    public CalendarDemoResource(BusinessDayService businessDays, HolidayService holidays) {
        this.businessDays = businessDays;
        this.holidays = holidays;
    }

    @GET
    @Path("/check")
    public Map<String, Object> check(@QueryParam("date") LocalDate date) {
        DayInfo info = businessDays.calendar().info(date);
        return Map.of("date", info.date().toString(), "businessDay", info.businessDay(),
                "holiday", info.holidayName() != null, "holidayName", String.valueOf(info.holidayName()));
    }

    @GET
    @Path("/count")
    public Map<String, Object> count(@QueryParam("from") LocalDate from, @QueryParam("to") LocalDate to) {
        businessDays.requireRangeWithinLimit(from, to);
        return Map.of("from", from.toString(), "to", to.toString(),
                "businessDays", businessDays.calendar().countBusinessDays(from, to));
    }

    @GET
    @Path("/add")
    public Map<String, Object> add(@QueryParam("from") LocalDate from, @QueryParam("days") int days) {
        return Map.of("from", from.toString(), "days", days,
                "result", businessDays.calendar().addBusinessDays(from, days).toString());
    }

    @POST
    @Path("/holidays")
    public Response createHoliday(HolidayRequest request) {
        holidays.create(request);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/holidays")
    public List<String> holidayNames(@QueryParam("year") Integer year) {
        return holidays.list(year).stream().map(h -> h.getName()).toList();
    }
}
