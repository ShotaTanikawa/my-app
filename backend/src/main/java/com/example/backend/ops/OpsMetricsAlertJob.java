package com.example.backend.ops;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 運用メトリクスを定期評価してしきい値超過時に通知するジョブ。
 */
@Component
public class OpsMetricsAlertJob {

    private final MeterRegistry meterRegistry;
    private final AlertNotificationService alertNotificationService;

    private final double latencyThresholdMs;
    private final double errorRateThreshold;
    private final double dbActiveConnectionThreshold;
    private final long minSampleCount;

    private long previousRequestCount;
    private long previousErrorCount;
    private double previousTotalLatencyMs;

    public OpsMetricsAlertJob(
            MeterRegistry meterRegistry,
            AlertNotificationService alertNotificationService,
            @Value("${app.alerts.api-latency-threshold-ms:1200}") double latencyThresholdMs,
            @Value("${app.alerts.api-error-rate-threshold:0.05}") double errorRateThreshold,
            @Value("${app.alerts.db-active-connection-threshold:20}") double dbActiveConnectionThreshold,
            @Value("${app.alerts.min-sample-count:20}") long minSampleCount
    ) {
        this.meterRegistry = meterRegistry;
        this.alertNotificationService = alertNotificationService;
        this.latencyThresholdMs = Math.max(50, latencyThresholdMs);
        this.errorRateThreshold = Math.max(0.001, errorRateThreshold);
        this.dbActiveConnectionThreshold = Math.max(1, dbActiveConnectionThreshold);
        this.minSampleCount = Math.max(1, minSampleCount);
    }

    @Scheduled(fixedDelayString = "${jobs.ops-metrics-alert-interval-ms:300000}")
    public void evaluate() {
        MetricsSnapshot snapshot = collectSnapshot();
        if (snapshot.requestCount == 0) {
            return;
        }

        long deltaRequests = snapshot.requestCount - previousRequestCount;
        long deltaErrors = snapshot.errorCount - previousErrorCount;
        double deltaLatencyMs = snapshot.totalLatencyMs - previousTotalLatencyMs;

        previousRequestCount = snapshot.requestCount;
        previousErrorCount = snapshot.errorCount;
        previousTotalLatencyMs = snapshot.totalLatencyMs;

        if (deltaRequests < minSampleCount) {
            return;
        }

        double avgLatencyMs = deltaLatencyMs / deltaRequests;
        double errorRate = (double) deltaErrors / deltaRequests;

        if (avgLatencyMs >= latencyThresholdMs) {
            alertNotificationService.notifyOperationalAlert(
                    "api_latency",
                    "API latency threshold exceeded",
                    "avgLatencyMs=" + Math.round(avgLatencyMs)
                            + ", thresholdMs=" + Math.round(latencyThresholdMs)
                            + ", sampleRequests=" + deltaRequests
            );
        }

        if (errorRate >= errorRateThreshold) {
            alertNotificationService.notifyOperationalAlert(
                    "api_error_rate",
                    "API error rate threshold exceeded",
                    "errorRate=" + String.format("%.4f", errorRate)
                            + ", threshold=" + String.format("%.4f", errorRateThreshold)
                            + ", sampleRequests=" + deltaRequests
                            + ", errorCount=" + deltaErrors
            );
        }

        if (snapshot.activeDbConnections >= dbActiveConnectionThreshold) {
            alertNotificationService.notifyOperationalAlert(
                    "db_active_connections",
                    "DB active connections threshold exceeded",
                    "activeConnections=" + Math.round(snapshot.activeDbConnections)
                            + ", threshold=" + Math.round(dbActiveConnectionThreshold)
            );
        }
    }

    private MetricsSnapshot collectSnapshot() {
        long requestCount = 0;
        long errorCount = 0;
        double totalLatencyMs = 0;

        for (Timer timer : meterRegistry.find("http.server.requests").timers()) {
            long count = timer.count();
            requestCount += count;
            totalLatencyMs += timer.totalTime(TimeUnit.MILLISECONDS);

            String status = timer.getId().getTag("status");
            if (status != null && status.startsWith("5")) {
                errorCount += count;
            }
        }

        double activeDbConnections = 0;
        Gauge gauge = meterRegistry.find("hikaricp.connections.active").gauge();
        if (gauge != null && gauge.value() > 0) {
            activeDbConnections = gauge.value();
        }

        return new MetricsSnapshot(requestCount, errorCount, totalLatencyMs, activeDbConnections);
    }

    private record MetricsSnapshot(
            long requestCount,
            long errorCount,
            double totalLatencyMs,
            double activeDbConnections
    ) {
    }
}
