package br.com.seedgrid.billingexample.entity;

import br.com.seedgrid.commons.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plano de assinatura da app host (o seedgrid-billing-core NAO modela planos — so eventos de
 * billing/idempotencia de webhook). Cada plano tem um {@link #code} estavel usado no
 * {@code CheckoutRequest.planCode} e ecoado de volta no payload do webhook fake.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
public class Plan extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @Column(nullable = false, length = 3)
    private String currency;
}
