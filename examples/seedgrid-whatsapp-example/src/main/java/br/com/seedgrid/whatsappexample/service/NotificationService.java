package br.com.seedgrid.whatsappexample.service;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.seedgrid.whatsapp.dtos.WhatsAppSendMessageResultDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppTextMessageDTO;
import br.com.seedgrid.whatsapp.exceptions.WhatsAppException;
import br.com.seedgrid.whatsapp.services.WhatsAppApi;
import br.com.seedgrid.whatsappexample.entity.Notification;
import br.com.seedgrid.whatsappexample.entity.NotificationStatus;
import br.com.seedgrid.whatsappexample.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Demonstra o uso DIRETO da extension seedgrid-whatsapp: injeta {@link WhatsAppApi} (o bean
 * produzido pela extension conforme {@code seedgrid.whatsapp.api}) e envia uma mensagem de texto.
 * O app host e' responsavel por decidir qual "channelId" usar (na Evolution API, o nome da
 * instancia) e por guardar seu proprio historico de envios — a extension em si nao persiste nada.
 */
@ApplicationScoped
public class NotificationService {

    private final WhatsAppApi whatsAppApi;
    private final NotificationRepository repository;
    private final String channelId;

    public NotificationService(
            WhatsAppApi whatsAppApi,
            NotificationRepository repository,
            @ConfigProperty(name = "app.notifications.whatsapp-channel-id") String channelId) {
        this.whatsAppApi = whatsAppApi;
        this.repository = repository;
        this.channelId = channelId;
    }

    @Transactional
    public Notification send(String phoneNumber, String message) {
        Notification notification = new Notification();
        notification.setPhoneNumber(phoneNumber);
        notification.setMessage(message);
        notification.setChannelId(channelId);

        try {
            WhatsAppSendMessageResultDTO result = whatsAppApi.sendTextMessage(
                    channelId, new WhatsAppTextMessageDTO(phoneNumber, message));
            notification.setStatus(NotificationStatus.SENT);
            notification.setProviderMessageId(result.messageId());
        } catch (WhatsAppException e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }

        repository.persist(notification);
        return notification;
    }

    public List<Notification> listAll() {
        return repository.listAll();
    }
}
