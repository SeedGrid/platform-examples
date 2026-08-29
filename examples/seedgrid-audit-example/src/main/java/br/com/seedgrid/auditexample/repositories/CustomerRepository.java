package br.com.seedgrid.auditexample.repositories;

import java.util.Optional;

import br.com.seedgrid.auditexample.entities.Customer;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerRepository implements PanacheRepository<Customer> {

    public Optional<Customer> findByPublicId(String publicId) {
        return find("publicId", publicId).firstResultOptional();
    }

    public Optional<Customer> findByCpf(String cpf) {
        return find("cpf", cpf).firstResultOptional();
    }
}
