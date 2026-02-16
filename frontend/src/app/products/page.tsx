"use client";

import { useAuth } from "@/components/auth-provider";
import {
  addStock,
  createProduct,
  getProducts,
  updateProduct,
} from "@/lib/api";
import { formatCurrency } from "@/lib/format";
import type { Product } from "@/types/api";
import { FormEvent, useEffect, useMemo, useState } from "react";

export default function ProductsPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;

  const [products, setProducts] = useState<Product[]>([]);
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(true);

  const [stockQuantity, setStockQuantity] = useState(1);

  const [createForm, setCreateForm] = useState({
    sku: "",
    name: "",
    description: "",
    unitPrice: "",
    reorderPoint: "0",
    reorderQuantity: "0",
  });

  const [editForm, setEditForm] = useState({
    name: "",
    description: "",
    unitPrice: "",
    reorderPoint: "0",
    reorderQuantity: "0",
  });

  const role = state?.user.role;
  // 商品マスタ変更はADMIN限定にする。
  const canManageProducts = role === "ADMIN";
  // 在庫調整は現場オペレーションを考慮してOPERATORにも許可する。
  const canAdjustStock = role === "ADMIN" || role === "OPERATOR";

  const selectedProduct = useMemo(
    () => products.find((product) => product.id === selectedProductId) ?? null,
    [products, selectedProductId],
  );

  useEffect(() => {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }
    let mounted = true;

    async function load() {
      setLoading(true);
      setError("");

      try {
        // 初期表示時は商品と在庫を同時に取り込む。
        const data = await getProducts(currentCredentials!);
        if (mounted) {
          setProducts(data);
        }
      } catch (err) {
        if (!mounted) {
          return;
        }

        setError(err instanceof Error ? err.message : "商品の取得に失敗しました。");
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      mounted = false;
    };
  }, [credentials]);

  useEffect(() => {
    if (!selectedProduct) {
      setEditForm({ name: "", description: "", unitPrice: "", reorderPoint: "0", reorderQuantity: "0" });
      return;
    }

    setEditForm({
      name: selectedProduct.name,
      description: selectedProduct.description ?? "",
      unitPrice: String(selectedProduct.unitPrice),
      reorderPoint: String(selectedProduct.reorderPoint),
      reorderQuantity: String(selectedProduct.reorderQuantity),
    });
  }, [selectedProduct]);

  if (!state || !credentials) {
    return null;
  }

  async function refreshProducts() {
    // 作成/更新/在庫追加後に一覧を再取得して表示を同期する。
    const data = await getProducts(credentials!);
    setProducts(data);
  }

  async function handleCreateProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");

    try {
      // 入力値の余分な空白を除去してAPIへ送る。
      await createProduct(credentials!, {
        sku: createForm.sku.trim(),
        name: createForm.name.trim(),
        description: createForm.description.trim() || undefined,
        unitPrice: Number(createForm.unitPrice),
        reorderPoint: Number(createForm.reorderPoint || "0"),
        reorderQuantity: Number(createForm.reorderQuantity || "0"),
      });
      setCreateForm({
        sku: "",
        name: "",
        description: "",
        unitPrice: "",
        reorderPoint: "0",
        reorderQuantity: "0",
      });
      setSuccess("商品を作成しました。");
      await refreshProducts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "商品作成に失敗しました。");
    }
  }

  async function handleUpdateProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedProductId) {
      return;
    }

    setError("");
    setSuccess("");

    try {
      await updateProduct(credentials!, selectedProductId, {
        name: editForm.name.trim(),
        description: editForm.description.trim() || undefined,
        unitPrice: Number(editForm.unitPrice),
        reorderPoint: Number(editForm.reorderPoint || "0"),
        reorderQuantity: Number(editForm.reorderQuantity || "0"),
      });
      setSuccess("商品を更新しました。");
      await refreshProducts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "商品更新に失敗しました。");
    }
  }

  async function handleAddStock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedProductId) {
      return;
    }

    setError("");
    setSuccess("");

    try {
      // 選択中商品の販売可能在庫を加算する。
      await addStock(credentials!, selectedProductId, stockQuantity);
      setSuccess("在庫を追加しました。");
      await refreshProducts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "在庫追加に失敗しました。");
    }
  }

  return (
    <div className="page">
      <section className="card">
        <h2 style={{ marginBottom: 10 }}>商品一覧</h2>
        <p style={{ margin: "0 0 12px", color: "#607086" }}>
          商品を選択すると、下段で在庫追加や商品情報の編集ができます。
        </p>

        {error && <p className="inline-error">{error}</p>}
        {success && <p style={{ color: "#137a49", marginTop: 6 }}>{success}</p>}

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>SKU</th>
                <th>商品名</th>
                <th>単価</th>
                <th>再発注点</th>
                <th>発注ロット</th>
                <th>販売可能</th>
                <th>引当済</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr key={product.id}>
                  <td>{product.sku}</td>
                  <td>{product.name}</td>
                  <td>{formatCurrency(product.unitPrice)}</td>
                  <td>{product.reorderPoint}</td>
                  <td>{product.reorderQuantity}</td>
                  <td>{product.availableQuantity}</td>
                  <td>{product.reservedQuantity}</td>
                  <td>
                    <button
                      className="button secondary"
                      type="button"
                      onClick={() => setSelectedProductId(product.id)}
                    >
                      選択
                    </button>
                  </td>
                </tr>
              ))}
              {!loading && products.length === 0 && (
                <tr>
                  <td colSpan={8}>
                    商品がありません。{canManageProducts ? "下段の「商品作成（ADMIN）」から登録してください。" : ""}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <div className="grid cols-2">
        {canManageProducts && (
          <section className="card">
            <h2 style={{ marginBottom: 10 }}>商品作成（ADMIN）</h2>
            <form className="form-grid single" onSubmit={handleCreateProduct}>
              <div className="field">
                <label htmlFor="create-sku">SKU</label>
                <input
                  id="create-sku"
                  className="input"
                  value={createForm.sku}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, sku: event.target.value }))}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="create-name">商品名</label>
                <input
                  id="create-name"
                  className="input"
                  value={createForm.name}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, name: event.target.value }))}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="create-unit-price">単価</label>
                <input
                  id="create-unit-price"
                  className="input"
                  type="number"
                  min={1}
                  step={1}
                  value={createForm.unitPrice}
                  onChange={(event) =>
                    setCreateForm((prev) => ({ ...prev, unitPrice: event.target.value }))
                  }
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="create-reorder-point">再発注点</label>
                <input
                  id="create-reorder-point"
                  className="input"
                  type="number"
                  min={0}
                  step={1}
                  value={createForm.reorderPoint}
                  onChange={(event) =>
                    setCreateForm((prev) => ({ ...prev, reorderPoint: event.target.value }))
                  }
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="create-reorder-quantity">発注ロット</label>
                <input
                  id="create-reorder-quantity"
                  className="input"
                  type="number"
                  min={0}
                  step={1}
                  value={createForm.reorderQuantity}
                  onChange={(event) =>
                    setCreateForm((prev) => ({ ...prev, reorderQuantity: event.target.value }))
                  }
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="create-description">説明</label>
                <textarea
                  id="create-description"
                  className="textarea"
                  value={createForm.description}
                  onChange={(event) =>
                    setCreateForm((prev) => ({ ...prev, description: event.target.value }))
                  }
                />
              </div>
              <div className="button-row">
                <button className="button primary" type="submit">
                  作成
                </button>
              </div>
            </form>
          </section>
        )}

        <section className="card">
          <h2 style={{ marginBottom: 10 }}>選択中の商品</h2>
          {!selectedProduct && <p>商品を選択してください。</p>}
          {selectedProduct && (
            <div className="page" style={{ gap: 14 }}>
              <div>
                <strong>{selectedProduct.name}</strong>
                <div style={{ color: "#607086", marginTop: 4 }}>SKU: {selectedProduct.sku}</div>
              </div>

              {canManageProducts && (
                <form className="form-grid single" onSubmit={handleUpdateProduct}>
                  <div className="field">
                    <label htmlFor="edit-name">商品名</label>
                    <input
                      id="edit-name"
                      className="input"
                      value={editForm.name}
                      onChange={(event) =>
                        setEditForm((prev) => ({ ...prev, name: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div className="field">
                    <label htmlFor="edit-price">単価</label>
                    <input
                      id="edit-price"
                      className="input"
                      type="number"
                      min={1}
                      step={1}
                      value={editForm.unitPrice}
                      onChange={(event) =>
                        setEditForm((prev) => ({ ...prev, unitPrice: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div className="field">
                    <label htmlFor="edit-reorder-point">再発注点</label>
                    <input
                      id="edit-reorder-point"
                      className="input"
                      type="number"
                      min={0}
                      step={1}
                      value={editForm.reorderPoint}
                      onChange={(event) =>
                        setEditForm((prev) => ({ ...prev, reorderPoint: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div className="field">
                    <label htmlFor="edit-reorder-quantity">発注ロット</label>
                    <input
                      id="edit-reorder-quantity"
                      className="input"
                      type="number"
                      min={0}
                      step={1}
                      value={editForm.reorderQuantity}
                      onChange={(event) =>
                        setEditForm((prev) => ({ ...prev, reorderQuantity: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div className="field">
                    <label htmlFor="edit-description">説明</label>
                    <textarea
                      id="edit-description"
                      className="textarea"
                      value={editForm.description}
                      onChange={(event) =>
                        setEditForm((prev) => ({ ...prev, description: event.target.value }))
                      }
                    />
                  </div>
                  <div className="button-row">
                    <button className="button primary" type="submit">
                      更新
                    </button>
                  </div>
                </form>
              )}

              {canAdjustStock && (
                <form className="form-grid single" onSubmit={handleAddStock}>
                  <div className="field">
                    <label htmlFor="stock-quantity">在庫追加数</label>
                    <input
                      id="stock-quantity"
                      className="input"
                      type="number"
                      min={1}
                      step={1}
                      value={stockQuantity}
                      onChange={(event) => setStockQuantity(Number(event.target.value))}
                      required
                    />
                  </div>
                  <div className="button-row">
                    <button className="button secondary" type="submit">
                      在庫追加
                    </button>
                  </div>
                </form>
              )}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
