package br.com.seedgrid.auditexample.entities;

import java.time.LocalDate;

import br.com.seedgrid.audit.core.annotations.SgAudit;
import br.com.seedgrid.privacy.masking.SgMaskRule;
import br.com.seedgrid.privacy.masking.SgPersonalData;
import br.com.seedgrid.privacy.masking.MaskingStrategy;
import br.com.seedgrid.commons.persistence.BaseEntityPanache;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
@SgAudit(
        entityFqcn = "br.com.seedgrid.auditexample.entities.Customer",
        description = "Customer entity audit",
        options = {
                @SgMaskRule(field = "cpf", type = MaskingStrategy.FORMAT, maskWith = "***.***.***-**"),
                @SgMaskRule(field = "name", type = MaskingStrategy.TEMPLATE, maskWith = "##**************"),
                @SgMaskRule(field = "occupation", type = MaskingStrategy.FULL, maskWith = "[OCCUPATION]"),
                @SgMaskRule(field = "job", type = MaskingStrategy.PARTIAL, maskWith = "3"),
                @SgMaskRule(field = "birthDate", type = MaskingStrategy.HASH),
                @SgMaskRule(field = "address", remove = true),
                @SgMaskRule(field = "postalCode", type = MaskingStrategy.REMOVE),
                @SgMaskRule(field = "city", type = MaskingStrategy.FULL, maskWith = "[CITY]"),
                @SgMaskRule(field = "comment", type = MaskingStrategy.TEMPLATE, maskWith = "####************"),
                @SgMaskRule(field = "internalCode", type = MaskingStrategy.HASH)
        })
public class Customer extends BaseEntityPanache {

    @Column(name = "cpf", nullable = false, unique = true, length = 20)
    @SgPersonalData(type = MaskingStrategy.FORMAT, maskWith = "***.***.***-**")
    public String cpf;

    @Column(name = "name", nullable = false)
    @SgPersonalData(type = MaskingStrategy.TEMPLATE, maskWith = "##**************")
    public String name;

    @Column(name = "occupation")
    @SgPersonalData(type = MaskingStrategy.FULL, maskWith = "[OCCUPATION]")
    public String occupation;

    @Column(name = "job")
    @SgPersonalData(type = MaskingStrategy.PARTIAL, maskWith = "3")
    public String job;

    @Column(name = "birth_date")
    @SgPersonalData(type = MaskingStrategy.HASH)
    public LocalDate birthDate;

    @Column(name = "address", length = 500)
    @SgPersonalData(type = MaskingStrategy.REMOVE)
    public String address;

    @Column(name = "postal_code", length = 20)
    @SgPersonalData(type = MaskingStrategy.FULL, maskWith = "[POSTAL_CODE]")
    public String postalCode;

    @Column(name = "city")
    @SgPersonalData(type = MaskingStrategy.TEMPLATE, maskWith = "#********")
    public String city;

    @Column(name = "comment", columnDefinition = "TEXT")
    @SgPersonalData(type = MaskingStrategy.TEMPLATE, maskWith = "####************")
    public String comment;

    @Column(name = "internal_code", length = 100)
    @SgPersonalData(type = MaskingStrategy.HASH)
    public String internalCode;
}
