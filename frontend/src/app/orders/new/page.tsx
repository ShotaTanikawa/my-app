"use client";

import { useAuth } from "@/components/auth-provider";
import { createOrder, getProducts } from "@/lib/api";
import type { Product } from "@/types/api";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";

type LineItem = {
  productId: string;
  quantity: string;
};

export default function NewOrderPage() {
  const { state } = useAuth();
  const router = useRouter();
  const credentials = state?.credentials;

  const [customerName, setCustomerName] = useState("");
  const [items, setItems] = useState<LineItem[]>([{ productId: "", quantity: "1" }]);
  const [products, setProducts] = useState<Product[]>([]);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const role = state?.user.role;
  const canOperate = role === "ADMIN" || role === "OPERATOR";

  useEffect(() => {
    const currentCredentials = credentials;
    if (!currentCredentials || !canOperate) {
      return;
    }
    let mounted = true;

    async function loadProducts() {
      try {
        const data = await getProducts(currentCredentials!);
        if (mounted) {
          setProducts(data);
        }
      } catch (err) {
        if (mounted) {
          setError(err instanceof Error ? err.message : "商品の取得に失敗しました。");
        }
      }
    }

    void loadProducts();

    return () => {
      mounted = false;
    };
  }, [credentials, canOperate]);

  if (!state || !credentials) {
    return null;
  }

  if (!canOperate) {
    return (
      <section className="card">
        <h2>権限不足</h2>
        <p style={{ marginTop: 8 }}>この操作は ADMIN / OPERATOR のみ利用できます。</p>
      </section>
    );
  }

  function addItemRow() {
    setItems((prev) => [...prev, { productId: "", quantity: "1" }]);
  }

  function removeItemRow(index: number) {
    setItems((prev) => prev.filter((_, itemIndex) => itemIndex !== index));
  }

  function updateItem(index: number, key: keyof LineItem, value: string) {
    setItems((prev) =>
      prev.map((item, itemIndex) => {
        if (itemIndex !== index) {
          return item;
        }

        return { ...item, [key]: value };
      }),
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    if (!customerName.trim()) {
      setError("顧客名を入力してください。");
      return;
    }

    const parsedItems = items
      .filter((item) => item.productId && item.quantity)
      .map((item) => ({
        productId: Number(item.productId),
        quantity: Number(item.quantity),
      }))
      .filter((item) => item.productId > 0 && item.quantity > 0);

    if (parsedItems.length === 0) {
      setError("明細を1件以上入力してください。");
      return;
    }

    setIsSubmitting(true);

    try {
      const created = await createOrder(credentials!, {
        customerName: customerName.trim(),
        items: parsedItems,
      });

      router.replace(`/orders/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "受注作成に失敗しました。");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="page">
      <section className="card">
        <h2 style={{ marginBottom: 10 }}>新規受注作成</h2>

        <form className="page" onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="customer-name">顧客名</label>
            <input
              id="customer-name"
              className="input"
              value={customerName}
              onChange={(event) => setCustomerName(event.target.value)}
              required
            />
          </div>

          <section className="card" style={{ boxShadow: "none" }}>
            <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
              <h3>明細</h3>
              <button className="button secondary" type="button" onClick={addItemRow}>
                明細追加
              </button>
            </div>

            <div className="page" style={{ gap: 10 }}>
              {items.map((item, index) => (
                <div className="form-grid" key={index}>
                  <div className="field">
                    <label htmlFor={`product-${index}`}>商品</label>
                    <select
                      id={`product-${index}`}
                      className="select"
                      value={item.productId}
                      onChange={(event) => updateItem(index, "productId", event.target.value)}
                      required
                    >
                      <option value="">選択してください</option>
                      {products.map((product) => (
                        <option key={product.id} value={product.id}>
                          {product.sku} / {product.name}（在庫: {product.availableQuantity}）
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="field">
                    <label htmlFor={`quantity-${index}`}>数量</label>
                    <input
                      id={`quantity-${index}`}
                      className="input"
                      type="number"
                      min={1}
                      step={1}
                      value={item.quantity}
                      onChange={(event) => updateItem(index, "quantity", event.target.value)}
                      required
                    />
                  </div>

                  <div className="button-row" style={{ alignItems: "end" }}>
                    <button
                      className="button danger"
                      type="button"
                      onClick={() => removeItemRow(index)}
                      disabled={items.length === 1}
                    >
                      削除
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {error && <p className="inline-error">{error}</p>}

          <div className="button-row">
            <button className="button primary" type="submit" disabled={isSubmitting}>
              {isSubmitting ? "作成中..." : "受注作成"}
            </button>
            <button className="button secondary" type="button" onClick={() => router.push("/orders")}> 
              キャンセル
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
