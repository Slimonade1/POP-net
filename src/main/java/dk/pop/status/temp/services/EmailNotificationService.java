package dk.pop.status.temp.services;

import dk.pop.status.temp.utils.Log;
import org.slf4j.Logger;
import dk.pop.status.temp.domain.EmailRecipient;
import dk.pop.status.temp.repository.EmailRecipientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.StreamSupport;

@Service
public class EmailNotificationService {

    private static final Logger log = Log.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final EmailRecipientRepository recipientRepository;
    private final boolean enabled;

    public EmailNotificationService(
            JavaMailSender mailSender,
            EmailRecipientRepository recipientRepository,
            @Value("${notifications.email.enabled:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.recipientRepository = recipientRepository;
        this.enabled = enabled;
    }

    public void notifyStatusChange(String serviceName, String previousStatus, String currentStatus, String diagnosticMessage) {
        if (!enabled) {
            return;
        }

        List<String> recipients = StreamSupport.stream(recipientRepository.findAll().spliterator(), false)
                .map(EmailRecipient::email)
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipients.toArray(String[]::new));
        message.setSubject("POP-net: " + serviceName + " changed to " + currentStatus);
        message.setText("""
                The service "%s" changed status.

                Previous status: %s
                New status: %s
                Details: %s
                """.formatted(serviceName, previousStatus, currentStatus, diagnosticMessage));

        try {
            mailSender.send(message);
            log.atInfo().log("Status change email sent for service '" + serviceName
                    + "' to " + String.join(", ", recipients));
        } catch (RuntimeException e) {
            log.atError().log("Could not send status change email for service '" + serviceName + "': "
                    + e.getMessage());
        }
    }
}
