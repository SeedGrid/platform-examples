package br.com.seedgrid.subdomaintenancyexample.repository;

import br.com.seedgrid.subdomaintenancyexample.entity.master.PaymentPlan;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for PaymentPlan (MASTER database)
 * Uses @PersistenceUnit("master") to access the master database
 */
@ApplicationScoped
@PersistenceUnit("master")
public class PaymentPlanRepository implements PanacheRepository<PaymentPlan> {

    public List<PaymentPlan> findByDescription(String description) {
        return list("LOWER(description) LIKE LOWER(?1)", "%" + description + "%");
    }

    public List<PaymentPlan> findByMaxValue(BigDecimal maxValue) {
        return list("value <= ?1", maxValue);
    }
}

