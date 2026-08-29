package br.com.seedgrid.subdomaintenancyexample.service;

import java.util.List;
import java.util.stream.Collectors;

import br.com.seedgrid.audit.core.annotations.Audit;
import br.com.seedgrid.privacy.masking.MaskRule;
import br.com.seedgrid.audit.core.enums.AuditOperationEnum;
import br.com.seedgrid.privacy.masking.MaskingStrategy;
import br.com.seedgrid.subdomaintenancyexample.dto.PersonDTO;
import br.com.seedgrid.subdomaintenancyexample.entity.tenant.Person;
import br.com.seedgrid.subdomaintenancyexample.repository.PersonRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/**
 * Service for Person (TENANT database)
 * The tenant is automatically resolved from the request context
 */
@ApplicationScoped
public class PersonService {

    @Inject
    PersonRepository repository;

    public List<PersonDTO> findAll() {
        return repository.listAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public PersonDTO findById(Long id) {
        Person entity = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Person not found with id: " + id));
        return toDTO(entity);
    }

    public List<PersonDTO> findByName(String name) {
        return repository.findByName(name).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Audit(operation = AuditOperationEnum.CREATE, entityFqcn = "br.com.seedgrid.subdomaintenancyexample.entity.tenant.Person", description = "Create person", options = {
        @MaskRule(field = "cpf", type = MaskingStrategy.FULL),
    })
    public PersonDTO create(PersonDTO dto) {
        Person entity = Person.builder()
                .name(dto.getName())
                .cpf(dto.getCpf())
                .build();
        repository.persist(entity);
        return toDTO(entity);
    }

    @Transactional
    @Audit(operation = AuditOperationEnum.UPDATE, entityFqcn  = "br.com.seedgrid.subdomaintenancyexample.entity.tenant.Person", description = "Update person",
     options = {
        @MaskRule(field = "cpf", type = MaskingStrategy.TEMPLATE, maskWith = "**#.***.###-*#")
    })
    public PersonDTO update(Long id, PersonDTO dto) {
        Person entity = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Person not found with id: " + id));
        entity.setName(dto.getName());
        entity.setCpf(dto.getCpf());
        return toDTO(entity);
    }

    @Transactional
    @Audit(operation = AuditOperationEnum.DELETE, entityFqcn = "br.com.seedgrid.subdomaintenancyexample.entity.tenant.Person", description = "Delete person")
    public void delete(Long id) {
        Person entity = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Person not found with id: " + id));
        repository.delete(entity);
    }

    private PersonDTO toDTO(Person entity) {
        return new PersonDTO(entity.getId(), entity.getName(), entity.getCpf());
    }
}

