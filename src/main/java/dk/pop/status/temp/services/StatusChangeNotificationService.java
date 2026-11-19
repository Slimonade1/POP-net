package dk.pop.status.temp.services;

import org.springframework.stereotype.Service;

@Service
public class StatusChangeNotificationService {
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

        emailNotifications.notifyStatusChange(serviceName, previousStatus, currentStatus, diagnosticMessage);
        webhookNotifications.notifyStatusChange(serviceName, previousStatus, currentStatus, diagnosticMessage);
    }
}
