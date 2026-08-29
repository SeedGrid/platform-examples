package br.com.seedgrid.auditexample.services;

import br.com.seedgrid.auditexample.entities.LegacyProduct;
import br.com.seedgrid.auditexample.repositories.LegacyProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class LegacyProductService {

    private final LegacyProductRepository legacyProductRepository;

    public LegacyProductService(LegacyProductRepository legacyProductRepository) {
        this.legacyProductRepository = legacyProductRepository;
    }

    public LegacyProduct findByExternalCode(String externalCode) {
        return legacyProductRepository.findByExternalCode(externalCode)
                .orElseThrow(() -> new EntityNotFoundException("LegacyProduct not found: " + externalCode));
    }

    @Transactional
    public LegacyProduct create(LegacyProduct legacyProduct) {
        legacyProductRepository.persist(legacyProduct);
        return legacyProduct;
    }

    @Transactional
    public void deleteByExternalCode(String externalCode) {
        legacyProductRepository.delete(findByExternalCode(externalCode));
    }
}
