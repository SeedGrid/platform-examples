package br.com.seedgrid.auditexample.services;

import br.com.seedgrid.auditexample.entities.BusinessUnit;
import br.com.seedgrid.auditexample.repositories.BusinessUnitRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BusinessUnitService {

    private final BusinessUnitRepository businessUnitRepository;

    public BusinessUnitService(BusinessUnitRepository businessUnitRepository) {
        this.businessUnitRepository = businessUnitRepository;
    }

    public BusinessUnit findByPublicId(String publicId) {
        return businessUnitRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("BusinessUnit not found: " + publicId));
    }

    @Transactional
    public BusinessUnit create(BusinessUnit businessUnit) {
        businessUnitRepository.persist(businessUnit);
        return businessUnit;
    }

    @Transactional
    public void delete(String publicId) {
        businessUnitRepository.delete(findByPublicId(publicId));
    }
}
