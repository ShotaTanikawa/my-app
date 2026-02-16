"use client";

import { useAuth } from "@/features/auth";
import {
  addStock,
  createProduct,
  createProductCategory,
  getNextProductSku,
  getProducts,
  getProductCategories,
  getProductsPage,
  importProductsCsv,
  updateProductCategorySkuRule,
  updateProduct,
} from "@/lib/api";
import { formatCategoryOptionLabel, resolveCategoryAndDescendantIds } from "@/features/category";
import { formatCurrency } from "@/lib/format";
import type { Product, ProductCategory, ProductImportResult } from "@/types/api";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useToast } from "@/features/feedback";

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

function applyClientFilters(
  products: Product[],
  categories: ProductCategory[],
  filters: ProductFilterState,
): Product[] {
  const q = filters.q.trim().toLowerCase();
  const categoryId = filters.categoryId ? Number(filters.categoryId) : null;
  const categoryIds = resolveCategoryAndDescendantIds(categories, categoryId);

  return products.filter((product) => {
    if (q) {
      const bySku = product.sku.toLowerCase().includes(q);
      const byName = product.name.toLowerCase().includes(q);
      if (!bySku && !byName) {
        return false;
      }
    }

    if (categoryId != null && (product.categoryId == null || !categoryIds.has(product.categoryId))) {
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
  const { showError, showInfo, showSuccess } = useToast();
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
  const hasInitializedCategorySelectionRef = useRef(false);

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
    parentId: "",
    skuPrefix: "",
    skuSequenceDigits: "4",
  });
  const [categoryRuleForm, setCategoryRuleForm] = useState({
    categoryId: "",
    skuPrefix: "",
    skuSequenceDigits: "4",
  });
  const [creatingSku, setCreatingSku] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
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
  const activeCategories = useMemo(
    () => categories.filter((category) => category.active),
    [categories],
  );
  const selectedCategory = useMemo(
    () => (filters.categoryId ? categories.find((category) => category.id === Number(filters.categoryId)) ?? null : null),
    [categories, filters.categoryId],
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

          const filtered = applyClientFilters(allProducts, categories, filters);
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
  }, [credentials, page, filters, reloadKey, categories]);

  useEffect(() => {
    // 画面表示時はカテゴリ起点の導線にするため、最初のカテゴリを自動選択する。
    if (hasInitializedCategorySelectionRef.current) {
      return;
    }
    if (activeCategories.length === 0) {
      return;
    }
    if (filters.categoryId || draftFilters.categoryId) {
      hasInitializedCategorySelectionRef.current = true;
      return;
    }
    const firstCategory = activeCategories.find((category) => category.depth === 0) ?? activeCategories[0];
    const categoryId = String(firstCategory.id);
    setDraftFilters((prev) => ({ ...prev, categoryId }));
    setFilters((prev) => ({ ...prev, categoryId }));
    setPage(0);
    hasInitializedCategorySelectionRef.current = true;
  }, [activeCategories, filters.categoryId, draftFilters.categoryId]);

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

    if (categoryRuleForm.categoryId) {
      const selected = data.find((item) => item.id === Number(categoryRuleForm.categoryId));
      if (selected) {
        setCategoryRuleForm({
          categoryId: String(selected.id),
          skuPrefix: selected.skuPrefix ?? "",
          skuSequenceDigits: String(selected.skuSequenceDigits ?? 4),
        });
      }
    }
  }

  async function handleCreateProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");
    setIsMutating(true);

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
      const message = "商品を作成しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshProducts();
    } catch (err) {
      const message = err instanceof Error ? err.message : "商品作成に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleUpdateProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedProductId) {
      return;
    }

    setError("");
    setSuccess("");
    setIsMutating(true);

    try {
      await updateProduct(credentials!, selectedProductId, {
        name: editForm.name.trim(),
        description: editForm.description.trim() || undefined,
        unitPrice: Number(editForm.unitPrice),
        categoryId: editForm.categoryId ? Number(editForm.categoryId) : undefined,
      });
      setImportResult(null);
      const message = "商品を更新しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshProducts();
    } catch (err) {
      const message = err instanceof Error ? err.message : "商品更新に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleAddStock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedProductId) {
      return;
    }

    setError("");
    setSuccess("");
    setIsMutating(true);

    try {
      // 選択中商品の販売可能在庫を加算する。
      await addStock(credentials!, selectedProductId, stockQuantity);
      setImportResult(null);
      const message = "在庫を追加しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshProducts();
    } catch (err) {
      const message = err instanceof Error ? err.message : "在庫追加に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleCreateCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");
    setIsMutating(true);

    try {
      await createProductCategory(credentials!, {
        code: categoryForm.code.trim(),
        name: categoryForm.name.trim(),
        parentId: categoryForm.parentId ? Number(categoryForm.parentId) : undefined,
        skuPrefix: categoryForm.skuPrefix.trim() || undefined,
        skuSequenceDigits: Number(categoryForm.skuSequenceDigits || "4"),
      });
      setCategoryForm({ code: "", name: "", parentId: "", skuPrefix: "", skuSequenceDigits: "4" });
      setImportResult(null);
      const message = "カテゴリを作成しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshCategories();
    } catch (err) {
      const message = err instanceof Error ? err.message : "カテゴリ作成に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setFilters(draftFilters);
  }

  function handleCategorySelect(categoryId: string) {
    setDraftFilters((prev) => ({ ...prev, categoryId }));
    setFilters((prev) => ({ ...prev, categoryId }));
    setPage(0);
  }

  function handleClearFilters() {
    setDraftFilters(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
    setPage(0);
  }

  function handleCategoryRuleTargetChange(categoryId: string) {
    if (!categoryId) {
      setCategoryRuleForm({ categoryId: "", skuPrefix: "", skuSequenceDigits: "4" });
      return;
    }

    const selected = categories.find((item) => item.id === Number(categoryId));
    setCategoryRuleForm({
      categoryId,
      skuPrefix: selected?.skuPrefix ?? "",
      skuSequenceDigits: String(selected?.skuSequenceDigits ?? 4),
    });
  }

  async function handleUpdateCategoryRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");
    setIsMutating(true);

    if (!categoryRuleForm.categoryId) {
      const message = "更新対象のカテゴリを選択してください。";
      setError(message);
      showError(message);
      setIsMutating(false);
      return;
    }

    try {
      await updateProductCategorySkuRule(credentials!, Number(categoryRuleForm.categoryId), {
        skuPrefix: categoryRuleForm.skuPrefix.trim() || undefined,
        skuSequenceDigits: Number(categoryRuleForm.skuSequenceDigits || "4"),
      });
      const message = "カテゴリのSKUルールを更新しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshCategories();
    } catch (err) {
      const message = err instanceof Error ? err.message : "カテゴリSKUルール更新に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleGenerateSku() {
    setError("");
    setSuccess("");
    setCreatingSku(true);

    try {
      const categoryId = createForm.categoryId ? Number(createForm.categoryId) : undefined;
      const result = await getNextProductSku(credentials!, categoryId);
      setCreateForm((prev) => ({ ...prev, sku: result.sku }));
      const message = `SKU候補を反映しました: ${result.sku}`;
      setSuccess(message);
      showInfo(message);
    } catch (err) {
      const message = err instanceof Error ? err.message : "SKU自動採番に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setCreatingSku(false);
    }
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
    showInfo("CSVテンプレートをダウンロードしました。");
  }

  async function handleImportProductsCsv(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");
    setIsMutating(true);

    if (!importFile) {
      const message = "CSVファイルを選択してください。";
      setError(message);
      showError(message);
      setIsMutating(false);
      return;
    }

    try {
      const result = await importProductsCsv(credentials!, importFile);
      setImportResult(result);
      const message = `CSV取込が完了しました。（成功 ${result.successRows}件 / 失敗 ${result.failedRows}件）`;
      setSuccess(message);
      showSuccess(message);
      setImportFile(null);
      event.currentTarget.reset();
      await refreshProducts();
    } catch (err) {
      const message = err instanceof Error ? err.message : "CSV取込に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
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
          まずカテゴリをクリックし、必要ならSKU・商品名検索や在庫フィルタで絞り込みます。
        </p>

        <div className="grid cols-2" style={{ marginBottom: 12 }}>
          <section className="card" style={{ boxShadow: "none" }}>
            <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 8 }}>
              <h3 style={{ fontSize: 18 }}>カテゴリから選択</h3>
              <span style={{ color: "#607086", fontSize: 12 }}>
                選択中: {selectedCategory ? selectedCategory.pathName : "すべて"}
              </span>
            </div>
            <div className="page" style={{ gap: 8, maxHeight: 260, overflowY: "auto" }}>
              <button
                className={`button ${!filters.categoryId ? "primary" : "secondary"}`}
                type="button"
                onClick={() => handleCategorySelect("")}
                style={{ width: "100%", textAlign: "left" }}
              >
                すべての商品
              </button>
              {activeCategories.map((category) => (
                <button
                  key={category.id}
                  className={`button ${filters.categoryId === String(category.id) ? "primary" : "secondary"}`}
                  type="button"
                  onClick={() => handleCategorySelect(String(category.id))}
                  style={{
                    width: "100%",
                    textAlign: "left",
                    paddingLeft: 12 + Math.max(category.depth, 0) * 16,
                  }}
                >
                  {category.name} ({category.code})
                </button>
              ))}
            </div>
          </section>

          <section className="card" style={{ boxShadow: "none" }}>
            <h3 style={{ marginBottom: 8, fontSize: 18 }}>検索・在庫フィルタ</h3>
            <form className="form-grid single" onSubmit={handleSearch}>
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
              <div className="button-row">
                <button className="button primary" type="submit" disabled={loading}>
                  検索
                </button>
                <button
                  className="button secondary"
                  type="button"
                  onClick={handleClearFilters}
                  disabled={loading}
                >
                  クリア
                </button>
              </div>
            </form>
          </section>
        </div>

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
                <div className="button-row">
                  <input
                    id="create-sku"
                    className="input"
                    value={createForm.sku}
                    onChange={(event) =>
                      setCreateForm((prev) => ({ ...prev, sku: event.target.value.toUpperCase() }))
                    }
                    required
                  />
                <button
                  className="button secondary"
                  type="button"
                  onClick={handleGenerateSku}
                  disabled={creatingSku || isMutating}
                >
                  {creatingSku ? "採番中..." : "自動採番"}
                </button>
                </div>
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
                  {activeCategories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {formatCategoryOptionLabel(category)}
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
                <button className="button primary" type="submit" disabled={isMutating}>
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
                      {activeCategories.map((category) => (
                        <option key={category.id} value={category.id}>
                          {formatCategoryOptionLabel(category)}
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
                    <button className="button primary" type="submit" disabled={isMutating}>
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
                    <button className="button secondary" type="submit" disabled={isMutating}>
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
            <div className="field">
              <label htmlFor="create-category-parent">親カテゴリ（任意）</label>
              <select
                id="create-category-parent"
                className="select"
                value={categoryForm.parentId}
                onChange={(event) =>
                  setCategoryForm((prev) => ({ ...prev, parentId: event.target.value }))
                }
              >
                <option value="">最上位カテゴリとして作成</option>
                {activeCategories.map((category) => (
                  <option
                    key={category.id}
                    value={category.id}
                    disabled={category.depth >= 2}
                  >
                    {formatCategoryOptionLabel(category)}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="create-category-sku-prefix">SKUプレフィックス（任意）</label>
              <input
                id="create-category-sku-prefix"
                className="input"
                value={categoryForm.skuPrefix}
                onChange={(event) =>
                  setCategoryForm((prev) => ({ ...prev, skuPrefix: event.target.value.toUpperCase() }))
                }
                placeholder="例: FIG"
              />
            </div>
            <div className="field">
              <label htmlFor="create-category-sku-digits">SKU連番桁数（3-6）</label>
              <input
                id="create-category-sku-digits"
                className="input"
                type="number"
                min={3}
                max={6}
                step={1}
                value={categoryForm.skuSequenceDigits}
                onChange={(event) =>
                  setCategoryForm((prev) => ({ ...prev, skuSequenceDigits: event.target.value }))
                }
                required
              />
            </div>
            <div className="button-row" style={{ alignItems: "end" }}>
              <button className="button primary" type="submit" disabled={isMutating}>
                カテゴリ追加
              </button>
            </div>
          </form>

          <div style={{ height: 1, background: "#e5e7eb", margin: "16px 0" }} />

          <h3 style={{ margin: "0 0 10px", fontSize: 18 }}>カテゴリSKUルール更新</h3>
          <form className="form-grid" onSubmit={handleUpdateCategoryRule}>
            <div className="field">
              <label htmlFor="category-rule-target">カテゴリ</label>
              <select
                id="category-rule-target"
                className="select"
                value={categoryRuleForm.categoryId}
                onChange={(event) => handleCategoryRuleTargetChange(event.target.value)}
                required
              >
                <option value="">選択してください</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {formatCategoryOptionLabel(category)}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="category-rule-prefix">SKUプレフィックス</label>
              <input
                id="category-rule-prefix"
                className="input"
                value={categoryRuleForm.skuPrefix}
                onChange={(event) =>
                  setCategoryRuleForm((prev) => ({ ...prev, skuPrefix: event.target.value.toUpperCase() }))
                }
                placeholder="未入力でカテゴリコードを利用"
              />
            </div>
            <div className="field">
              <label htmlFor="category-rule-digits">SKU連番桁数（3-6）</label>
              <input
                id="category-rule-digits"
                className="input"
                type="number"
                min={3}
                max={6}
                step={1}
                value={categoryRuleForm.skuSequenceDigits}
                onChange={(event) =>
                  setCategoryRuleForm((prev) => ({ ...prev, skuSequenceDigits: event.target.value }))
                }
                required
              />
            </div>
            <div className="button-row" style={{ alignItems: "end" }}>
              <button className="button secondary" type="submit" disabled={isMutating}>
                SKUルール更新
              </button>
            </div>
          </form>

          <div className="table-wrap" style={{ marginTop: 12 }}>
            <table className="table">
              <thead>
                <tr>
                  <th>階層パス</th>
                  <th>カテゴリコード</th>
                  <th>カテゴリ名</th>
                  <th>SKUプレフィックス</th>
                  <th>連番桁数</th>
                </tr>
              </thead>
              <tbody>
                {categories.map((category) => (
                  <tr key={category.id}>
                    <td>{category.pathName}</td>
                    <td>{category.code}</td>
                    <td>{formatCategoryOptionLabel(category)}</td>
                    <td>{category.skuPrefix ?? "-"}</td>
                    <td>{category.skuSequenceDigits}</td>
                  </tr>
                ))}
                {categories.length === 0 && (
                  <tr>
                    <td colSpan={5}>カテゴリがありません。</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
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
              <button className="button primary" type="submit" disabled={isMutating}>
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
