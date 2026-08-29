package br.com.seedgrid.whatsappexample;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import br.com.seedgrid.whatsapp.dtos.WhatsAppAudioMessageFileDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppAudioMessageURLDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppContactMessageDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppLocationMessageDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppReactionMessageDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppStickerMessageDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppTemplateMessageDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppMediaMessageFileDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppMediaMessageURLDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppSendMessageResultDTO;
import br.com.seedgrid.whatsapp.dtos.WhatsAppTextMessageDTO;
import br.com.seedgrid.whatsapp.exceptions.WhatsAppException;
import br.com.seedgrid.whatsapp.services.WhatsAppApi;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Test double do {@link WhatsAppApi}, sem Mockito: a extension seedgrid-whatsapp e' um REST
 * client para um servico externo (Evolution API / WhatsApp Cloud) que nao temos como bater de
 * verdade num teste. Em vez disso, substituimos o bean inteiro por um CDI {@code @Alternative}
 * com prioridade global (padrao CDI puro, nao precisa de beans.xml) que fica em src/test/java —
 * assim os testes exercitam o NotificationService/NotificationResource reais, e so' a chamada
 * para o provider externo e' simulada.
 *
 * <p>{@link #failNext} controla se a proxima chamada a {@code sendTextMessage} deve simular uma
 * falha do provider (equivalente a uma {@link WhatsAppException} lancada pela extension real).
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class StubWhatsAppApi implements WhatsAppApi {

    public static volatile boolean failNext = false;

    public record Call(String channelId, WhatsAppTextMessageDTO message) {
    }

    public static final List<Call> calls = new CopyOnWriteArrayList<>();

    public static void reset() {
        failNext = false;
        calls.clear();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendTextMessage(String channelId, WhatsAppTextMessageDTO msg) {
        calls.add(new Call(channelId, msg));
        if (failNext) {
            throw new WhatsAppException("Simulated Evolution API outage");
        }
        return new WhatsAppSendMessageResultDTO("stub-message-id-123", Map.of("stub", true));
    }

    @Override
    public WhatsAppSendMessageResultDTO sendMediaFile(String channelId, WhatsAppMediaMessageFileDTO msg) {
        throw new UnsupportedOperationException("not used in this example");
        return WhatsAppSendMessageResultDTO.empty();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendMediaUrl(String channelId, WhatsAppMediaMessageURLDTO msg) {
        throw new UnsupportedOperationException("not used in this example");
        return WhatsAppSendMessageResultDTO.empty();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendAudioFile(String channelId, WhatsAppAudioMessageFileDTO msg) {
        throw new UnsupportedOperationException("not used in this example");
        return WhatsAppSendMessageResultDTO.empty();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendAudioUrl(String channelId, WhatsAppAudioMessageURLDTO msg) {
        throw new UnsupportedOperationException("not used in this example");
        return WhatsAppSendMessageResultDTO.empty();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendLocation(String channelId, WhatsAppLocationMessageDTO msg) {
        return WhatsAppSendMessageResultDTO.empty();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendContact(String channelId, WhatsAppContactMessageDTO msg) {
        return WhatsAppSendMessageResultDTO.empty();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendReaction(String channelId, WhatsAppReactionMessageDTO msg) {
        return WhatsAppSendMessageResultDTO.empty();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendSticker(String channelId, WhatsAppStickerMessageDTO msg) {
        return WhatsAppSendMessageResultDTO.empty();
    }

    @Override
    public WhatsAppSendMessageResultDTO sendTemplate(String channelId, WhatsAppTemplateMessageDTO msg) {
        return WhatsAppSendMessageResultDTO.empty();
    }
}
