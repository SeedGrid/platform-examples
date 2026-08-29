package br.com.seedgrid.auditexample.entities;

import java.time.LocalDate;

import br.com.seedgrid.audit.core.annotations.Audit;
import br.com.seedgrid.privacy.masking.MaskRule;
import br.com.seedgrid.privacy.masking.PersonalData;
import br.com.seedgrid.privacy.masking.MaskingStrategy;
import br.com.seedgrid.commons.persistence.BaseEntityPanache;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
@Audit(
        entityFqcn = "br.com.seedgrid.auditexample.entities.Customer",
        description = "Customer entity audit",
        options = {
                @MaskRule(field = "cpf", type = MaskingStrategy.FORMAT, maskWith = "***.***.***-**"),
                @MaskRule(field = "name", type = MaskingStrategy.TEMPLATE, maskWith = "##**************"),
                @MaskRule(field = "occupation", type = MaskingStrategy.FULL, maskWith = "[OCCUPATION]"),
                @MaskRule(field = "job", type = MaskingStrategy.PARTIAL, maskWith = "3"),
                @MaskRule(field = "birthDate", type = MaskingStrategy.HASH),
                @MaskRule(field = "address", remove = true),
                @MaskRule(field = "postalCode", type = MaskingStrategy.REMOVE),
                @MaskRule(field = "city", type = MaskingStrategy.FULL, maskWith = "[CITY]"),
                @MaskRule(field = "comment", type = MaskingStrategy.TEMPLATE, maskWith = "####************"),
                @MaskRule(field = "internalCode", type = MaskingStrategy.HASH)
        })
public class Customer extends BaseEntityPanache {

    @Column(name = "cpf", nullable = false, unique = true, length = 20)
    @PersonalData(type = MaskingStrategy.FORMAT, maskWith = "***.***.***-**")
    public String cpf;

    @Column(name = "name", nullable = false)
    @PersonalData(type = MaskingStrategy.TEMPLATE, maskWith = "##**************")
    public String name;

    @Column(name = "occupation")
    @PersonalData(type = MaskingStrategy.FULL, maskWith = "[OCCUPATION]")
    public String occupation;

    @Column(name = "job")
    @PersonalData(type = MaskingStrategy.PARTIAL, maskWith = "3")
    public String job;

    @Column(name = "birth_date")
    @PersonalData(type = MaskingStrategy.HASH)
    public LocalDate birthDate;

    @Column(name = "address", length = 500)
    @PersonalData(type = MaskingStrategy.REMOVE)
    public String address;

    @Column(name = "postal_code", length = 20)
    @PersonalData(type = MaskingStrategy.FULL, maskWith = "[POSTAL_CODE]")
    public String postalCode;

    @Column(name = "city")
    @PersonalData(type = MaskingStrategy.TEMPLATE, maskWith = "#********")
    public String city;

    @Column(name = "comment", columnDefinition = "TEXT")
    @PersonalData(type = MaskingStrategy.TEMPLATE, maskWith = "####************")
    public String comment;

    @Column(name = "internal_code", length = 100)
    @PersonalData(type = MaskingStrategy.HASH)
    public String internalCode;
}
