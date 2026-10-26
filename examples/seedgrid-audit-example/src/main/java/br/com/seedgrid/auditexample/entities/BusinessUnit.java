package br.com.seedgrid.auditexample.entities;

import br.com.seedgrid.audit.core.annotations.SgAudit;
import br.com.seedgrid.commons.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_units")
@SgAudit(
        entityFqcn = "br.com.seedgrid.auditexample.entities.BusinessUnit",
        description = "Business unit entity audit")
public class BusinessUnit extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    public String code;

    @Column(name = "name", nullable = false)
    public String name;
}
