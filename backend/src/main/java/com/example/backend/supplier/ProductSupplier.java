package com.example.backend.supplier;

import com.example.backend.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
/**
 * DBテーブルに対応する永続化エンティティ。
 */

@Entity
@Table(
        name = "product_suppliers",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_suppliers_product_supplier", columnNames = {"product_id", "supplier_id"})
)
public class ProductSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays;

    @Column(nullable = false)
    private Integer moq;

    @Column(name = "lot_size", nullable = false)
    private Integer lotSize;

    @Column(name = "is_primary", nullable = false)
    private Boolean primarySupplier;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.leadTimeDays == null) {
            this.leadTimeDays = 0;
        }
        if (this.moq == null || this.moq < 1) {
            this.moq = 1;
        }
        if (this.lotSize == null || this.lotSize < 1) {
            this.lotSize = 1;
        }
        if (this.primarySupplier == null) {
            this.primarySupplier = false;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public void setLeadTimeDays(Integer leadTimeDays) {
        this.leadTimeDays = leadTimeDays;
    }

    public Integer getMoq() {
        return moq;
    }

    public void setMoq(Integer moq) {
        this.moq = moq;
    }

    public Integer getLotSize() {
        return lotSize;
    }

    public void setLotSize(Integer lotSize) {
        this.lotSize = lotSize;
    }

    public Boolean getPrimarySupplier() {
        return primarySupplier;
    }

    public void setPrimarySupplier(Boolean primarySupplier) {
        this.primarySupplier = primarySupplier;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
