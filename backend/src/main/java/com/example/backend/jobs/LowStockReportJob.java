package com.example.backend.jobs;

import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LowStockReportJob {

    private static final Logger log = LoggerFactory.getLogger(LowStockReportJob.class);

    private final InventoryRepository inventoryRepository;
    private final Integer lowStockThreshold;

    public LowStockReportJob(
            InventoryRepository inventoryRepository,
            @Value("${jobs.low-stock-threshold:10}") Integer lowStockThreshold
    ) {
        this.inventoryRepository = inventoryRepository;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Scheduled(cron = "${jobs.low-stock-report-cron:0 0 1 * * *}")
    public void runLowStockReport() {
        List<Inventory> lowStocks = inventoryRepository.findLowStockInventories(lowStockThreshold);
        if (lowStocks.isEmpty()) {
            log.info("Low-stock report: no products under threshold={}", lowStockThreshold);
            return;
        }

        String summary = lowStocks.stream()
                .map(inventory -> inventory.getProduct().getSku() + "(available=" + inventory.getAvailableQuantity() + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");

        log.warn("Low-stock report: {} products under threshold={}. {}", lowStocks.size(), lowStockThreshold, summary);
    }
}
