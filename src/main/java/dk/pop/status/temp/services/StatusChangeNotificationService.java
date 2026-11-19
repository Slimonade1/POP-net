package dk.pop.status.temp.services;

import org.springframework.stereotype.Service;
import dk.pop.status.temp.utils.Log;
import org.slf4j.Logger;

@Service
public class StatusChangeNotificationService {
    private static final Logger log = Log.getLogger(StatusChangeNotificationService.class);
    private final EmailNotificationService emailNotifications;
    private final WebhookNotificationService webhookNotifications;

    public StatusChangeNotificationService(EmailNotificationService emailNotifications,
                                           WebhookNotificationService webhookNotifications) {
        this.emailNotifications = emailNotifications;
        this.webhookNotifications = webhookNotifications;
    }

    public void notifyStatusChange(String serviceName, String previousStatus,
                                   String currentStatus, String diagnosticMessage) {
        // The first observation establishes a baseline. Routine checks do not alert.
        if (previousStatus == null || previousStatus.equalsIgnoreCase(currentStatus)) {
            return;
        }

        try {
            emailNotifications.notifyStatusChange(serviceName, previousStatus, currentStatus, diagnosticMessage);
        } catch (RuntimeException e) {
            log.atError().log("Email notification failed for service '{}' ({})",
                    serviceName, e.getClass().getSimpleName());
        }
        try {
            webhookNotifications.notifyStatusChange(serviceName, previousStatus, currentStatus, diagnosticMessage);
        } catch (RuntimeException e) {
            log.atError().log("Webhook notifications failed for service '{}' ({})",
                    serviceName, e.getClass().getSimpleName());
        }
    }
}
