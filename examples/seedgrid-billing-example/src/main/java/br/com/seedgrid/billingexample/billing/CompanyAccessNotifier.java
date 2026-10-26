package br.com.seedgrid.billingexample.billing;

import org.jboss.logging.Logger;

import br.com.seedgrid.billing.dtos.events.CompanyAccessEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

/**
 * O billing-core muda o estado das empresas da conta (limite do plano no downgrade, inadimplencia,
 * pagamento restaurado) e dispara {@link CompanyAccessEvent}, mas NAO envia e-mail: avisar o
 * responsavel e' do host. Este exemplo so' registra no log; um host real enviaria o e-mail aqui.
 */
@ApplicationScoped
public class CompanyAccessNotifier {

    private static final Logger LOG = Logger.getLogger(CompanyAccessNotifier.class);

    void onCompanyAccess(@Observes(during = TransactionPhase.AFTER_SUCCESS) CompanyAccessEvent event) {
        LOG.infof("[billing-example] empresa %s (%s): %s -> %s (%s/%s) plano=%s efetivo=%s",
                event.companyName(), event.companyPublicId(), event.previousMode(), event.newMode(),
                event.type(), event.cause(), event.planName(), event.effectiveAt());
    }
}
