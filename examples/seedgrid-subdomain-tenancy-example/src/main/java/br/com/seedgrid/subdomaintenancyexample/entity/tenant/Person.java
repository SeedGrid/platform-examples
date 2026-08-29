package br.com.seedgrid.subdomaintenancyexample.entity.tenant;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import br.com.seedgrid.privacy.masking.PersonalData;
import br.com.seedgrid.privacy.masking.MaskingStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Person entity - belongs to TENANT database
 * Uses the default persistence unit (with DATABASE multitenancy)
 * 
 * This entity is accessible from {tenant}.seedgrid.com.br domains
 */
@Entity
@Table(name = "persons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Person  {

    @Column(name = "id", nullable = false, updatable = false)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    @PersonalData(type = MaskingStrategy.TEMPLATE, maskWith = "####****(LGPD)****########")
    private String name;

    @Column(nullable = true, length = 20)
    private String cpf;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

