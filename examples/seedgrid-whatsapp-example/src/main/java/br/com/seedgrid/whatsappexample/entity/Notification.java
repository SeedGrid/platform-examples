package br.com.seedgrid.whatsappexample.entity;

import br.com.seedgrid.commons.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro local de cada tentativa de notificacao via WhatsApp. A propria extension
 * (seedgrid-whatsapp) nao persiste nada — quem quiser um historico de mensagens enviadas
 * precisa guardar isso na propria aplicacao, como este exemplo demonstra.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "channel_id", nullable = false, length = 100)
    private String channelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
