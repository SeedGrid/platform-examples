package br.com.seedgrid.auditexample.repositories;

import java.util.Optional;

import br.com.seedgrid.auditexample.entities.LegacyProduct;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LegacyProductRepository implements PanacheRepository<LegacyProduct> {

    public Optional<LegacyProduct> findByExternalCode(String externalCode) {
        return find("externalCode", externalCode).firstResultOptional();
    }
}
