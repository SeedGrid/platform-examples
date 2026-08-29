package br.com.seedgrid.logintenancyexample.service;

import br.com.seedgrid.commons.core.exceptions.EntityNotFoundException;
import br.com.seedgrid.logintenancyexample.dto.ProductDTO;
import br.com.seedgrid.logintenancyexample.entity.Product;
import br.com.seedgrid.logintenancyexample.repository.ProductRepository;
import br.com.seedgrid.security.services.CompanyRequestContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Login-tenancy isola por company_id (isolamento logico, mesma base/schema),
 * diferente do subdomain-tenancy (isolamento fisico por schema/DB). Toda
 * consulta/escrita aqui e escopada pela company corrente do token via
 * {@link CompanyRequestContext}.
 */
@ApplicationScoped
public class ProductService {

    @Inject
    ProductRepository productRepository;

    @Inject
    CompanyRequestContext companyRequestContext;

    public List<Product> listAll() {
        return productRepository.findByCompany(currentCompanyId());
    }

    public Product findById(Long id) {
        return productRepository.findByIdAndCompany(id, currentCompanyId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
    }

    @Transactional
    public Product create(ProductDTO dto) {
        Product product = new Product();
        product.setCompanyId(currentCompanyId());
        product.setName(dto.name());
        product.setSku(dto.sku());
        product.setPrice(dto.price());
        productRepository.persist(product);
        return product;
    }

    @Transactional
    public Product update(Long id, ProductDTO dto) {
        Product product = findById(id);
        product.setName(dto.name());
        product.setSku(dto.sku());
        product.setPrice(dto.price());
        return product;
    }

    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        productRepository.delete(product);
    }

    private Long currentCompanyId() {
        return companyRequestContext.getCurrentCompanyId();
    }
}
