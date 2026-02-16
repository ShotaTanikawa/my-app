package com.example.backend.purchase;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
/**
 * 仕入発注に対する入荷イベント（1回の入荷登録）を保持するエンティティ。
 */

@Entity
@Table(name = "purchase_order_receipts")
public class PurchaseOrderReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PurchaseOrderReceiptItem> items = new LinkedHashSet<>();

    @PrePersist
    void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = OffsetDateTime.now();
        }
    }

    public void addItem(PurchaseOrderReceiptItem item) {
        item.setReceipt(this);
        this.items.add(item);
    }

    public Long getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public void setPurchaseOrder(PurchaseOrder purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Set<PurchaseOrderReceiptItem> getItems() {
        return items;
    }
}
