package br.com.seedgrid.auditexample.entities;

import br.com.seedgrid.audit.core.annotations.SgAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "legacy_products")
@SgAudit(
        entityFqcn = "br.com.seedgrid.auditexample.entities.LegacyProduct",
        description = "Legacy product entity audit")
public class LegacyProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "external_code", nullable = false, unique = true, length = 80)
    public String externalCode;

    @Column(name = "name", nullable = false)
    public String name;
}
