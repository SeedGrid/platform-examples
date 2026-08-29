package br.com.seedgrid.auditexample.repositories;

import java.util.Optional;

import br.com.seedgrid.auditexample.entities.BusinessUnit;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BusinessUnitRepository implements PanacheRepository<BusinessUnit> {

    public Optional<BusinessUnit> findByPublicId(String publicId) {
        return find("publicId", publicId).firstResultOptional();
    }
}
