package com.example.backend.purchase;

import com.example.backend.product.Product;
import jakarta.persistence.*;
/**
 * 入荷イベントの明細行（商品ごとの入荷数量）を表すエンティティ。
 */

@Entity
@Table(name = "purchase_order_receipt_items")
public class PurchaseOrderReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private PurchaseOrderReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    public Long getId() {
        return id;
    }

    public PurchaseOrderReceipt getReceipt() {
        return receipt;
    }

    public void setReceipt(PurchaseOrderReceipt receipt) {
        this.receipt = receipt;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
