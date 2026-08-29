package br.com.seedgrid.billingexample.service;

import java.util.Optional;

import br.com.seedgrid.billingexample.dto.SubscriptionDTO;
import br.com.seedgrid.billingexample.entity.Plan;
import br.com.seedgrid.billingexample.entity.Subscription;
import br.com.seedgrid.billingexample.repository.PlanRepository;
import br.com.seedgrid.billingexample.repository.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SubscriptionService {

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    PlanRepository planRepository;

    public Optional<SubscriptionDTO> findForCompany(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId).map(this::toDTO);
    }

    private SubscriptionDTO toDTO(Subscription subscription) {
        Plan plan = planRepository.findById(subscription.getPlanId());
        return new SubscriptionDTO(
                plan == null ? null : plan.getCode(),
                plan == null ? null : plan.getName(),
                subscription.getStatus().name(),
                subscription.getProviderSubscriptionId(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCanceledAt());
    }
}
