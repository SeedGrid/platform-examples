package br.com.seedgrid.notificationexample.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import br.com.seedgrid.notification.api.EventDefinition;
import br.com.seedgrid.notification.api.NotificationCatalogContributor;
import jakarta.enterprise.context.ApplicationScoped;

/** Os eventos de negocio do host: entram no catalogo da extension, que cuida de regras, canais e entrega. */
@ApplicationScoped
public class OrderEvents implements NotificationCatalogContributor {

    public static final String ORDER_CREATED = "order.created";

    @Override
    public Collection<EventDefinition> events() {
        return List.of(new EventDefinition(
                ORDER_CREATED,
                "Pedidos",
                "Pedido criado",
                "Avisa quando um pedido e' criado",
                Set.of("customer"),
                "Novo pedido de {customer}",
                "O pedido de {customer} foi criado."));
    }
}
