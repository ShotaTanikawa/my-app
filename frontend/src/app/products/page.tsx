"use client";

import { useAuth } from "@/components/auth-provider";
import {
  addStock,
  createProduct,
  createProductCategory,
  getProducts,
  getProductCategories,
  getProductsPage,
  importProductsCsv,
  updateProduct,
} from "@/lib/api";
import { formatCurrency } from "@/lib/format";
import type { Product, ProductCategory, ProductImportResult } from "@/types/api";
import { FormEvent, useEffect, useMemo, useState } from "react";

const PAGE_SIZE = 20;
const DEFAULT_REORDER_POINT = 5;
const DEFAULT_REORDER_QUANTITY = 10;
const PRODUCT_IMPORT_TEMPLATE_CSV = [
  "sku,name,categoryCode,unitPrice,availableQuantity,description",
  "IH-STICK-001,ホッケースティック,SKATE_GEAR,18000,12,エントリーモデル",
  "FS-BOOT-001,フィギュアスケートブーツ,FIGURE_GEAR,42000,5,初中級向け",
].join("\n");

type ProductFilterState = {
  q: string;
  categoryId: string;
  lowStockOnly: boolean;
};

const EMPTY_FILTERS: ProductFilterState = {
  q: "",
  categoryId: "",
  lowStockOnly: false,
};

function applyClientFilters(products: Product[], filters: ProductFilterState): Product[] {
  const q = filters.q.trim().toLowerCase();
  const categoryId = filters.categoryId ? Number(filters.categoryId) : null;

  return products.filter((product) => {
    if (q) {
      const bySku = product.sku.toLowerCase().includes(q);
      const byName = product.name.toLowerCase().includes(q);
      if (!bySku && !byName) {
        return false;
      }
    }

    if (categoryId != null && product.categoryId !== categoryId) {
      return false;
    }

    if (filters.lowStockOnly && product.availableQuantity > product.reorderPoint) {
      return false;
    }

    return true;
  });
}

export default function ProductsPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;

  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const [filters, setFilters] = useState<ProductFilterState>(EMPTY_FILTERS);
  const [draftFilters, setDraftFilters] = useState<ProductFilterState>(EMPTY_FILTERS);

  const [stockQuantity, setStockQuantity] = useState(1);

  const [createForm, setCreateForm] = useState({
    sku: "",
    name: "",
    description: "",
    unitPrice: "",
    categoryId: "",
  });

  const [editForm, setEditForm] = useState({
    name: "",
    description: "",
    unitPrice: "",
    categoryId: "",
  });

  const [categoryForm, setCategoryForm] = useState({
    code: "",
    name: "",
  });
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importResult, setImportResult] = useState<ProductImportResult | null>(null);

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

    async function loadCategories() {
      try {
        const data = await getProductCategories(currentCredentials!);
        if (mounted) {
          setCategories(data);
        }
      } catch (err) {
        if (!mounted) {
          return;
        }
        setError(err instanceof Error ? err.message : "カテゴリの取得に失敗しました。");
      }
    }

    void loadCategories();
    return () => {
      mounted = false;
    };
  }, [credentials]);

  useEffect(() => {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }
    let mounted = true;

    async function loadProducts() {
      setLoading(true);
      setError("");

      try {
        const data = await getProductsPage(currentCredentials!, {
          page,
          size: PAGE_SIZE,
          q: filters.q.trim() || undefined,
          categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
          lowStockOnly: filters.lowStockOnly ? true : undefined,
        });
        if (!mounted) {
          return;
        }
        setProducts(data.items);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
        setHasNext(data.hasNext);
        setHasPrevious(data.hasPrevious);
      } catch (err) {
        try {
          // ページングAPI失敗時は全件APIで代替して画面継続性を優先する。
          const allProducts = await getProducts(currentCredentials!);
          if (!mounted) {
            return;
          }

          const filtered = applyClientFilters(allProducts, filters);
          const total = filtered.length;
          const size = PAGE_SIZE;
          const totalPageCount = total === 0 ? 0 : Math.ceil(total / size);
          const safePage = Math.min(page, Math.max(totalPageCount - 1, 0));
          const start = safePage * size;
          const end = start + size;

          setProducts(filtered.slice(start, end));
          setTotalPages(totalPageCount);
          setTotalElements(total);
          setHasPrevious(safePage > 0);
          setHasNext(safePage + 1 < totalPageCount);
          setPage(safePage);
        } catch (fallbackErr) {
          if (!mounted) {
            return;
          }
          const fallbackMessage =
            fallbackErr instanceof Error ? fallbackErr.message : "商品の取得に失敗しました。";
          const originalMessage = err instanceof Error ? err.message : "商品の取得に失敗しました。";
          setError(`${fallbackMessage} (${originalMessage})`);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    void loadProducts();
    return () => {
      mounted = false;
    };
  }, [credentials, page, filters, reloadKey]);

  useEffect(() => {
    if (!selectedProduct) {
      setEditForm({
        name: "",
        description: "",
        unitPrice: "",
        categoryId: "",
      });
      return;
    }

    setEditForm({
      name: selectedProduct.name,
      description: selectedProduct.description ?? "",
      unitPrice: String(selectedProduct.unitPrice),
      categoryId: selectedProduct.categoryId == null ? "" : String(selectedProduct.categoryId),
    });
  }, [selectedProduct]);

  if (!state || !credentials) {
    return null;
  }

  async function refreshProducts() {
    // 作成/更新/在庫追加後に先頭ページから一覧を再取得する。
    setPage(0);
    setReloadKey((prev) => prev + 1);
  }

  async function refreshCategories() {
    const data = await getProductCategories(credentials!);
    setCategories(data);
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
        reorderPoint: DEFAULT_REORDER_POINT,
        reorderQuantity: DEFAULT_REORDER_QUANTITY,
        categoryId: createForm.categoryId ? Number(createForm.categoryId) : undefined,
      });
      setCreateForm({
        sku: "",
        name: "",
        description: "",
        unitPrice: "",
        categoryId: "",
      });
      setImportResult(null);
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
        categoryId: editForm.categoryId ? Number(editForm.categoryId) : undefined,
      });
      setImportResult(null);
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
      setImportResult(null);
      setSuccess("在庫を追加しました。");
      await refreshProducts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "在庫追加に失敗しました。");
    }
  }

  async function handleCreateCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");

    try {
      await createProductCategory(credentials!, {
        code: categoryForm.code.trim(),
        name: categoryForm.name.trim(),
      });
      setCategoryForm({ code: "", name: "" });
      setImportResult(null);
      setSuccess("カテゴリを作成しました。");
      await refreshCategories();
    } catch (err) {
      setError(err instanceof Error ? err.message : "カテゴリ作成に失敗しました。");
    }
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setFilters(draftFilters);
  }

  function handleClearFilters() {
    setDraftFilters(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
    setPage(0);
  }

  function handleDownloadImportTemplate() {
    const blob = new Blob([PRODUCT_IMPORT_TEMPLATE_CSV], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "products-import-template.csv";
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  async function handleImportProductsCsv(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");

    if (!importFile) {
      setError("CSVファイルを選択してください。");
      return;
    }

    try {
      const result = await importProductsCsv(credentials!, importFile);
      setImportResult(result);
      setSuccess(
        `CSV取込が完了しました。（成功 ${result.successRows}件 / 失敗 ${result.failedRows}件）`,
      );
      setImportFile(null);
      event.currentTarget.reset();
      await refreshProducts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "CSV取込に失敗しました。");
    }
  }

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>商品一覧</h2>
          <span style={{ color: "#607086", fontSize: 13 }}>
            全{totalElements}件 / {page + 1}ページ目
          </span>
        </div>
        <p style={{ margin: "0 0 12px", color: "#607086" }}>
          SKU・商品名検索、カテゴリ絞り込み、在庫注意フィルタに対応しています。
        </p>

        <form className="form-grid" onSubmit={handleSearch}>
          <div className="field">
            <label htmlFor="product-q">SKU / 商品名</label>
            <input
              id="product-q"
              className="input"
              value={draftFilters.q}
              onChange={(event) => setDraftFilters((prev) => ({ ...prev, q: event.target.value }))}
              placeholder="部分一致で検索"
            />
          </div>
          <div className="field">
            <label htmlFor="product-category-filter">カテゴリ</label>
            <select
              id="product-category-filter"
              className="select"
              value={draftFilters.categoryId}
              onChange={(event) =>
                setDraftFilters((prev) => ({ ...prev, categoryId: event.target.value }))
              }
            >
              <option value="">すべて</option>
              {categories
                .filter((category) => category.active)
                .map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.code} / {category.name}
                  </option>
                ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="product-low-stock-filter">在庫フィルタ</label>
            <select
              id="product-low-stock-filter"
              className="select"
              value={draftFilters.lowStockOnly ? "true" : "false"}
              onChange={(event) =>
                setDraftFilters((prev) => ({
                  ...prev,
                  lowStockOnly: event.target.value === "true",
                }))
              }
            >
              <option value="false">すべて</option>
              <option value="true">在庫注意のみ</option>
            </select>
          </div>
          <div className="button-row" style={{ alignItems: "end" }}>
            <button className="button primary" type="submit" disabled={loading}>
              検索
            </button>
            <button className="button secondary" type="button" onClick={handleClearFilters} disabled={loading}>
              クリア
            </button>
          </div>
        </form>

        {error && <p className="inline-error">{error}</p>}
        {success && <p style={{ color: "#137a49", marginTop: 6 }}>{success}</p>}

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>SKU</th>
                <th>商品名</th>
                <th>カテゴリ</th>
                <th>単価</th>
                <th>販売可能</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr key={product.id}>
                  <td>{product.sku}</td>
                  <td>{product.name}</td>
                  <td>{product.categoryName ?? "-"}</td>
                  <td>{formatCurrency(product.unitPrice)}</td>
                  <td>{product.availableQuantity}</td>
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
                  <td colSpan={6}>
                    条件に一致する商品がありません。
                    {canManageProducts ? " フィルタを変更するか、下段の「商品作成（ADMIN）」から登録してください。" : ""}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="button-row" style={{ justifyContent: "space-between", marginTop: 10 }}>
          <span style={{ color: "#607086", fontSize: 13 }}>
            {Math.max(totalPages, 1)}ページ中 {page + 1}ページ目
          </span>
          <div className="button-row">
            <button
              className="button secondary"
              type="button"
              onClick={() => setPage((prev) => Math.max(prev - 1, 0))}
              disabled={!hasPrevious || loading}
            >
              前へ
            </button>
            <button
              className="button secondary"
              type="button"
              onClick={() => setPage((prev) => prev + 1)}
              disabled={!hasNext || loading}
            >
              次へ
            </button>
          </div>
        </div>
      </section>

      <div className="grid cols-2">
        {canManageProducts && (
          <section className="card">
            <h2 style={{ marginBottom: 10 }}>商品作成（ADMIN）</h2>
            <p style={{ margin: "0 0 12px", color: "#607086" }}>
              再発注設定は標準値で自動登録されます。
            </p>
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
                <label htmlFor="create-category">カテゴリ</label>
                <select
                  id="create-category"
                  className="select"
                  value={createForm.categoryId}
                  onChange={(event) =>
                    setCreateForm((prev) => ({ ...prev, categoryId: event.target.value }))
                  }
                >
                  <option value="">未分類</option>
                  {categories
                    .filter((category) => category.active)
                    .map((category) => (
                      <option key={category.id} value={category.id}>
                        {category.code} / {category.name}
                      </option>
                    ))}
                </select>
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
                    <label htmlFor="edit-category">カテゴリ</label>
                    <select
                      id="edit-category"
                      className="select"
                      value={editForm.categoryId}
                      onChange={(event) =>
                        setEditForm((prev) => ({ ...prev, categoryId: event.target.value }))
                      }
                    >
                      <option value="">未分類</option>
                      {categories
                        .filter((category) => category.active)
                        .map((category) => (
                          <option key={category.id} value={category.id}>
                            {category.code} / {category.name}
                          </option>
                        ))}
                    </select>
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

      {canManageProducts && (
        <section className="card">
          <h2 style={{ marginBottom: 10 }}>カテゴリ作成（ADMIN）</h2>
          <form className="form-grid" onSubmit={handleCreateCategory}>
            <div className="field">
              <label htmlFor="create-category-code">カテゴリコード</label>
              <input
                id="create-category-code"
                className="input"
                value={categoryForm.code}
                onChange={(event) =>
                  setCategoryForm((prev) => ({ ...prev, code: event.target.value }))
                }
                required
              />
            </div>
            <div className="field">
              <label htmlFor="create-category-name">カテゴリ名</label>
              <input
                id="create-category-name"
                className="input"
                value={categoryForm.name}
                onChange={(event) =>
                  setCategoryForm((prev) => ({ ...prev, name: event.target.value }))
                }
                required
              />
            </div>
            <div className="button-row" style={{ alignItems: "end" }}>
              <button className="button primary" type="submit">
                カテゴリ追加
              </button>
            </div>
          </form>
        </section>
      )}

      {canManageProducts && (
        <section className="card">
          <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
            <h2>商品CSV一括取込（ADMIN）</h2>
            <button className="button secondary" type="button" onClick={handleDownloadImportTemplate}>
              テンプレートDL
            </button>
          </div>
          <p style={{ margin: "0 0 12px", color: "#607086" }}>
            必須列: <code>sku,name,unitPrice,availableQuantity</code>。任意列:{" "}
            <code>categoryCode,description</code>。SKU一致時は更新、未登録SKUは新規作成します。
          </p>

          <form className="form-grid" onSubmit={handleImportProductsCsv}>
            <div className="field">
              <label htmlFor="product-import-file">CSVファイル</label>
              <input
                id="product-import-file"
                className="input"
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => setImportFile(event.target.files?.[0] ?? null)}
                required
              />
            </div>
            <div className="button-row" style={{ alignItems: "end" }}>
              <button className="button primary" type="submit">
                取込実行
              </button>
            </div>
          </form>

          {importResult && (
            <div style={{ marginTop: 14 }}>
              <p style={{ margin: "0 0 8px", color: "#1f2937" }}>
                対象{importResult.totalRows}件 / 成功{importResult.successRows}件 / 新規
                {importResult.createdRows}件 / 更新{importResult.updatedRows}件 / 失敗
                {importResult.failedRows}件
              </p>
              {importResult.errors.length > 0 && (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>行番号</th>
                        <th>エラー内容</th>
                      </tr>
                    </thead>
                    <tbody>
                      {importResult.errors.map((item) => (
                        <tr key={`${item.rowNumber}-${item.message}`}>
                          <td>{item.rowNumber}</td>
                          <td>{item.message}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
