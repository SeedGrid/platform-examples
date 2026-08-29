package br.com.seedgrid.logintenancyexample.repository;

import br.com.seedgrid.logintenancyexample.entity.Product;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProductRepository implements PanacheRepository<Product> {

    public List<Product> findByCompany(Long companyId) {
        return list("companyId", companyId);
    }

    public Optional<Product> findByIdAndCompany(Long id, Long companyId) {
        return find("id = ?1 and companyId = ?2", id, companyId).firstResultOptional();
    }
}
