package br.com.hubregistro.auth.infrastructure.mail;

import br.com.hubregistro.auth.application.port.AccountMailer;
import br.com.hubregistro.auth.domain.reset.TokenPurpose;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Envio por SMTP (Mailpit em desenvolvimento). O e-mail sai só depois do commit: se a transação desfizer,
 * nenhum link para um token inexistente é enviado. Falha de envio não desfaz nada; o usuário pode pedir de novo.
 *
 * <p>Na Etapa 5 o envio passa a ser feito pelo notification-service a partir de eventos.
 */
@Component
class SmtpAccountMailer implements AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpAccountMailer.class);

    private final JavaMailSender sender;
    private final AuthProperties properties;

    SmtpAccountMailer(JavaMailSender sender, AuthProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void sendPasswordLink(User user, TokenPurpose purpose, String rawToken) {
        SimpleMailMessage message = build(user, purpose, rawToken);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(message, user);
                }
            });
        } else {
            send(message, user);
        }
    }

    private SimpleMailMessage build(User user, TokenPurpose purpose, String rawToken) {
        String token = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(user.email().value());
        if (purpose == TokenPurpose.ACTIVATION) {
            message.setSubject("Ative seu acesso ao Hub de Registro");
            message.setText("""
                    Olá, %s.

                    Foi criado um acesso para você no Hub de Registro de Contratos Imobiliários.
                    Para definir sua senha e ativar a conta, acesse:

                    %s/ativar-conta?token=%s

                    O link vale por 72 horas e pode ser usado uma única vez.
                    """.formatted(user.fullName(), properties.getPortalUrl(), token));
        } else {
            message.setSubject("Redefinição de senha do Hub de Registro");
            message.setText("""
                    Olá, %s.

                    Recebemos um pedido para redefinir a sua senha. Para escolher uma nova, acesse:

                    %s/redefinir-senha?token=%s

                    O link vale por 30 minutos e pode ser usado uma única vez.
                    Se você não fez este pedido, ignore este e-mail: sua senha atual continua valendo.
                    """.formatted(user.fullName(), properties.getPortalUrl(), token));
        }
        return message;
    }

    private void send(SimpleMailMessage message, User user) {
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("Falha ao enviar e-mail de acesso para o usuário {}", user.id(), e);
        }
    }
}
