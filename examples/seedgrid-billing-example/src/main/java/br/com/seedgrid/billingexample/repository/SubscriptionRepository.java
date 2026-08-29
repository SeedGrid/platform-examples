package br.com.seedgrid.billingexample.repository;

import java.util.Optional;

import br.com.seedgrid.billingexample.entity.Subscription;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SubscriptionRepository implements PanacheRepository<Subscription> {

    public Optional<Subscription> findByCompanyId(Long companyId) {
        return find("companyId", companyId).firstResultOptional();
    }

    public Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId) {
        return find("providerSubscriptionId", providerSubscriptionId).firstResultOptional();
    }
}
