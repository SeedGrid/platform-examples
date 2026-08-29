package br.com.seedgrid.subdomaintenancyexample.repository;

import br.com.seedgrid.subdomaintenancyexample.entity.tenant.Person;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for Person (TENANT database)
 * Uses the default persistence unit (with multitenancy)
 * The tenant is automatically resolved from the request context
 */
@ApplicationScoped
public class PersonRepository implements PanacheRepository<Person> {

    public List<Person> findByName(String name) {
        return list("LOWER(name) LIKE LOWER(?1)", "%" + name + "%");
    }
}

