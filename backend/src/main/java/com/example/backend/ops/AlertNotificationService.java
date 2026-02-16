package com.example.backend.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slack/メールへの運用通知を送信するサービス。
 */
@Service
public class AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationService.class);

    private final boolean enabled;
    private final String slackWebhookUrl;
    private final boolean emailEnabled;
    private final List<String> emailRecipients;
    private final String fromAddress;
    private final Duration minInterval;

    private final JavaMailSender mailSender;
    private final HttpClient httpClient;
    private final Map<String, Instant> lastSentAtByKey = new ConcurrentHashMap<>();

    public AlertNotificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.alerts.enabled:false}") boolean enabled,
            @Value("${app.alerts.slack-webhook-url:}") String slackWebhookUrl,
            @Value("${app.alerts.email.enabled:false}") boolean emailEnabled,
            @Value("${app.alerts.email.to:}") String emailTo,
            @Value("${app.alerts.email.from:noreply@example.com}") String fromAddress,
            @Value("${app.alerts.min-interval-seconds:900}") long minIntervalSeconds
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.enabled = enabled;
        this.slackWebhookUrl = slackWebhookUrl == null ? "" : slackWebhookUrl.trim();
        this.emailEnabled = emailEnabled;
        this.emailRecipients = Arrays.stream(emailTo.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        this.fromAddress = fromAddress;
        this.minInterval = Duration.ofSeconds(Math.max(60, minIntervalSeconds));
        this.httpClient = HttpClient.newHttpClient();
    }

    public void notifySecurityEvent(String key, String subject, String body) {
        notify("SECURITY_" + key, "[Security] " + subject, body);
    }

    public void notifyOperationalAlert(String key, String subject, String body) {
        notify("OPS_" + key, "[Ops] " + subject, body);
    }

    public void notifyRunbookEvent(String key, String subject, String body) {
        notify("RUNBOOK_" + key, "[Runbook] " + subject, body);
    }

    private void notify(String key, String subject, String body) {
        if (!enabled) {
            return;
        }
        if (!canSendNow(key)) {
            return;
        }

        sendToSlack(subject, body);
        sendToEmail(subject, body);
    }

    private boolean canSendNow(String key) {
        Instant now = Instant.now();
        Instant last = lastSentAtByKey.get(key);
        if (last != null && last.plus(minInterval).isAfter(now)) {
            return false;
        }
        lastSentAtByKey.put(key, now);
        return true;
    }

    private void sendToSlack(String subject, String body) {
        if (slackWebhookUrl.isBlank()) {
            return;
        }

        String payload = "{\"text\":\"" + escapeJson(subject + "\\n" + body) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(slackWebhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Slack alert failed: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.warn("Slack alert failed", ex);
        }
    }

    private void sendToEmail(String subject, String body) {
        if (!emailEnabled || emailRecipients.isEmpty() || mailSender == null) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(emailRecipients.toArray(String[]::new));
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Email alert failed", ex);
        }
    }

    private String escapeJson(String value) {
        String escaped = value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return escaped;
    }
}
