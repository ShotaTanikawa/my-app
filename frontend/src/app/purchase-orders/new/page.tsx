"use client";

import { useAuth } from "@/components/auth-provider";
import { createPurchaseOrder, getProducts, getReplenishmentSuggestions, getSuppliers } from "@/lib/api";
import type { Product, ReplenishmentSuggestion, Supplier } from "@/types/api";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";

type LineItem = {
  productId: string;
  quantity: string;
  unitCost: string;
};

export default function NewPurchaseOrderPage() {
  const { state } = useAuth();
  const router = useRouter();
  const credentials = state?.credentials;

  const [supplierName, setSupplierName] = useState("");
  const [selectedSupplierId, setSelectedSupplierId] = useState("");
  const [note, setNote] = useState("");
  const [items, setItems] = useState<LineItem[]>([{ productId: "", quantity: "1", unitCost: "1" }]);
  const [products, setProducts] = useState<Product[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [suggestions, setSuggestions] = useState<ReplenishmentSuggestion[]>([]);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const role = state?.user.role;
  const canOperate = role === "ADMIN" || role === "OPERATOR";
  const productMap = useMemo(
    () => new Map(products.map((product) => [String(product.id), product])),
    [products],
  );
  const supplierMap = useMemo(
    () => new Map(suppliers.map((supplier) => [String(supplier.id), supplier])),
    [suppliers],
  );

  useEffect(() => {
    const currentCredentials = credentials;
    if (!currentCredentials || !canOperate) {
      return;
    }

    let mounted = true;
    async function loadData() {
      try {
        // 発注入力に必要な商品マスタと補充提案を同時取得する。
        const [productData, suggestionData, supplierData] = await Promise.all([
          getProducts(currentCredentials!),
          getReplenishmentSuggestions(currentCredentials!),
          getSuppliers(currentCredentials!),
        ]);

        if (!mounted) {
          return;
        }

        setProducts(productData);
        setSuggestions(suggestionData);
        setSuppliers(supplierData);
      } catch (err) {
        if (!mounted) {
          return;
        }
        setError(err instanceof Error ? err.message : "初期データの取得に失敗しました。");
      }
    }

    void loadData();
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
    setItems((prev) => [...prev, { productId: "", quantity: "1", unitCost: "1" }]);
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

  function loadFromSuggestions() {
    // 補充提案をそのまま発注明細へ反映し、入力工数を減らす。
    const suggestedItems = suggestions
      .filter((suggestion) => suggestion.suggestedQuantity > 0)
      .map((suggestion) => {
        const product = productMap.get(String(suggestion.productId));
        const fallbackUnitCost =
          suggestion.suggestedUnitCost != null
            ? String(suggestion.suggestedUnitCost)
            : product
              ? String(product.unitPrice)
              : "1";
        return {
          productId: String(suggestion.productId),
          quantity: String(suggestion.suggestedQuantity),
          unitCost: fallbackUnitCost,
        };
      });

    if (suggestedItems.length === 0) {
      setError("取り込める補充提案がありません。");
      return;
    }

    setError("");
    setItems(suggestedItems);

    // 提案に紐づく仕入先があれば、最多出現の候補を初期値として選ぶ。
    const counts = new Map<string, number>();
    for (const suggestion of suggestions) {
      if (suggestion.suggestedSupplierId == null) {
        continue;
      }
      const key = String(suggestion.suggestedSupplierId);
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }

    const preferredSupplierId = [...counts.entries()]
      .sort((left, right) => right[1] - left[1])
      .at(0)?.[0];

    if (preferredSupplierId) {
      setSelectedSupplierId(preferredSupplierId);
      const supplier = supplierMap.get(preferredSupplierId);
      if (supplier && !supplierName.trim()) {
        setSupplierName(supplier.name);
      }
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    const normalizedSupplierName = supplierName.trim();
    if (!selectedSupplierId && !normalizedSupplierName) {
      setError("仕入先を選択するか、仕入先名を入力してください。");
      return;
    }

    const parsedItems = items
      .filter((item) => item.productId && item.quantity && item.unitCost)
      .map((item) => ({
        productId: Number(item.productId),
        quantity: Number(item.quantity),
        unitCost: Number(item.unitCost),
      }))
      .filter((item) => item.productId > 0 && item.quantity > 0 && item.unitCost > 0);

    if (parsedItems.length === 0) {
      setError("明細を1件以上入力してください。");
      return;
    }

    setIsSubmitting(true);
    try {
      const created = await createPurchaseOrder(credentials!, {
        supplierId: selectedSupplierId ? Number(selectedSupplierId) : undefined,
        supplierName: normalizedSupplierName || undefined,
        note: note.trim() || undefined,
        items: parsedItems,
      });

      router.replace(`/purchase-orders/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "仕入発注の作成に失敗しました。");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="page">
      <section className="card">
        <h2 style={{ marginBottom: 10 }}>新規仕入発注</h2>
        <p style={{ margin: "0 0 12px", color: "#607086" }}>
          仕入先を選択し、明細を入力して発注します。「提案を取り込む」で不足在庫の候補を自動反映できます。
        </p>

        <form className="page" onSubmit={handleSubmit}>
          <div className="form-grid">
            <div className="field">
              <label htmlFor="supplier-id">仕入先マスタ</label>
              <select
                id="supplier-id"
                className="select"
                value={selectedSupplierId}
                onChange={(event) => {
                  const nextSupplierId = event.target.value;
                  setSelectedSupplierId(nextSupplierId);
                  if (!nextSupplierId) {
                    return;
                  }
                  const supplier = supplierMap.get(nextSupplierId);
                  if (supplier) {
                    setSupplierName(supplier.name);
                  }
                }}
              >
                <option value="">選択しない（名称手入力）</option>
                {suppliers
                  .filter((supplier) => supplier.active)
                  .map((supplier) => (
                    <option key={supplier.id} value={supplier.id}>
                      {supplier.code} / {supplier.name}
                    </option>
                  ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="supplier-name">仕入先名</label>
              <input
                id="supplier-name"
                className="input"
                value={supplierName}
                onChange={(event) => setSupplierName(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="po-note">備考</label>
              <input
                id="po-note"
                className="input"
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
            </div>
          </div>

          <section className="card" style={{ boxShadow: "none" }}>
            <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
              <h3>明細</h3>
              <div className="button-row">
                <button className="button secondary" type="button" onClick={loadFromSuggestions}>
                  提案を取り込む
                </button>
                <button className="button secondary" type="button" onClick={addItemRow}>
                  明細追加
                </button>
              </div>
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
                          {product.sku} / {product.name}
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

                  <div className="field">
                    <label htmlFor={`unit-cost-${index}`}>仕入単価</label>
                    <input
                      id={`unit-cost-${index}`}
                      className="input"
                      type="number"
                      min={1}
                      step={1}
                      value={item.unitCost}
                      onChange={(event) => updateItem(index, "unitCost", event.target.value)}
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
              {isSubmitting ? "作成中..." : "仕入発注を作成"}
            </button>
            <button className="button secondary" type="button" onClick={() => router.push("/purchase-orders")}>
              キャンセル
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
