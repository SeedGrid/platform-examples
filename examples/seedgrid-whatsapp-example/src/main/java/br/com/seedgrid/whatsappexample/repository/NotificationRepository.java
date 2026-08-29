package br.com.seedgrid.whatsappexample.repository;

import br.com.seedgrid.whatsappexample.entity.Notification;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificationRepository implements PanacheRepository<Notification> {
}
