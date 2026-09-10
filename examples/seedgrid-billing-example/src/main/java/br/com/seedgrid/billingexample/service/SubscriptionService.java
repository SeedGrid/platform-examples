package br.com.seedgrid.billingexample.service;

import java.util.Optional;

import br.com.seedgrid.billing.entities.PlanEntity;
import br.com.seedgrid.billingexample.dto.SubscriptionDTO;
import br.com.seedgrid.billingexample.entity.Subscription;
import br.com.seedgrid.billingexample.repository.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SubscriptionService {

    @Inject
    SubscriptionRepository subscriptionRepository;

    public Optional<SubscriptionDTO> findForCompany(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId).map(this::toDTO);
    }

    private SubscriptionDTO toDTO(Subscription subscription) {
        // O plano vem do seedgrid-billing-core desde 2026.09.10 -- este exemplo nao mantem mais
        // tabela nem repositorio proprios de plano. PlanEntity e' Panache active record, entao a
        // busca e' estatica e nao ha nada a injetar.
        PlanEntity plan = PlanEntity.findById(subscription.getPlanId());
        return new SubscriptionDTO(
                plan == null ? null : plan.code,
                plan == null ? null : plan.name,
                subscription.getStatus().name(),
                subscription.getProviderSubscriptionId(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCanceledAt());
    }
}
