package br.com.seedgrid.subdomaintenancyexample.entity.master;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * PaymentPlan entity - belongs to MASTER database
 * Uses the "master" persistence unit (no multitenancy)
 *
 * This entity is accessible from seedgrid.com.br domain
 */
@Entity
@Table(name = "payment_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentPlan extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;
}

