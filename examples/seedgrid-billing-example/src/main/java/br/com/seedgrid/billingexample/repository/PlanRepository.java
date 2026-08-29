package br.com.seedgrid.billingexample.repository;

import java.util.Optional;

import br.com.seedgrid.billingexample.entity.Plan;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PlanRepository implements PanacheRepository<Plan> {

    public Optional<Plan> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }
}
