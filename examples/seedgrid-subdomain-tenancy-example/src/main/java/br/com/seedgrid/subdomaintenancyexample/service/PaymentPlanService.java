package br.com.seedgrid.subdomaintenancyexample.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import br.com.seedgrid.audit.core.annotations.Audit;
import br.com.seedgrid.audit.core.enums.AuditOperationEnum;
import br.com.seedgrid.subdomaintenancyexample.dto.PaymentPlanDTO;
import br.com.seedgrid.subdomaintenancyexample.entity.master.PaymentPlan;
import br.com.seedgrid.subdomaintenancyexample.repository.PaymentPlanRepository;
import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/**
 * Service for PaymentPlan (MASTER database)
 */
@ApplicationScoped
public class PaymentPlanService {

    @Inject
    @PersistenceUnit("master")
    PaymentPlanRepository repository;

    @Audit(entityFqcn = "br.com.seedgrid.subdomaintenancyexample.entity.master.PaymentPlan", operation = AuditOperationEnum.OTHER, description = "Read all payment plans")
    public List<PaymentPlanDTO> findAll() {
        return repository.listAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Audit(entityFqcn = "br.com.seedgrid.subdomaintenancyexample.entity.master.PaymentPlan", operation = AuditOperationEnum.OTHER, description = "find by id  payment plans")
    public PaymentPlanDTO findById(Long id) {
        PaymentPlan entity = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("PaymentPlan not found with id: " + id));
        return toDTO(entity);
    }

    @Audit(entityFqcn = "br.com.seedgrid.subdomaintenancyexample.entity.master.PaymentPlan", operation = AuditOperationEnum.OTHER, description = "find by description  payment plans")
    public List<PaymentPlanDTO> findByDescription(String description) {
        return repository.findByDescription(description).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PaymentPlanDTO> findByMaxValue(BigDecimal maxValue) {
        return repository.findByMaxValue(maxValue).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Audit(operation = AuditOperationEnum.CREATE, entityFqcn = "br.com.seedgrid.subdomaintenancyexample.entity.master.PaymentPlan", description = "Create payment plan")
    public PaymentPlanDTO create(PaymentPlanDTO dto) {
        PaymentPlan entity = PaymentPlan.builder()
                .description(dto.getDescription())
                .value(dto.getValue())
                .build();
        repository.persist(entity);
        return toDTO(entity);
    }

    @Transactional
    @Audit(operation = AuditOperationEnum.UPDATE, entityFqcn = "br.com.seedgrid.subdomaintenancyexample.entity.master.PaymentPlan", description = "Update payment plan")
    public PaymentPlanDTO update(Long id, PaymentPlanDTO dto) {
        PaymentPlan entity = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("PaymentPlan not found with id: " + id));
        entity.setDescription(dto.getDescription());
        entity.setValue(dto.getValue());
        return toDTO(entity);
    }

    @Transactional
    @Audit(operation = AuditOperationEnum.DELETE, entityFqcn = "br.com.seedgrid.subdomaintenancyexample.entity.master.PaymentPlan", description = "delete payment plan")
    public void delete(Long id) {
        if (!repository.deleteById(id)) {
            throw new NotFoundException("PaymentPlan not found with id: " + id);
        }
    }

    private PaymentPlanDTO toDTO(PaymentPlan entity) {
        return new PaymentPlanDTO(entity.id, entity.getDescription(), entity.getValue());
    }
}
