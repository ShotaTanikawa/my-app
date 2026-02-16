"use client";

import { useAuth } from "@/features/auth";
import {
  activateSupplier,
  createSupplier,
  deactivateSupplier,
  getProductSuppliers,
  getProducts,
  getSuppliers,
  removeProductSupplier,
  updateSupplier,
  upsertProductSupplier,
} from "@/lib/api";
import { formatCurrency } from "@/lib/format";
import type { Product, ProductSupplierContract, Supplier } from "@/types/api";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { useToast } from "@/features/feedback";

export default function SuppliersPage() {
  const { state } = useAuth();
  const { showError, showSuccess } = useToast();
  const credentials = state?.credentials;
  const role = state?.user.role;
  const canManage = role === "ADMIN";

  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [contracts, setContracts] = useState<ProductSupplierContract[]>([]);
  const [selectedSupplierId, setSelectedSupplierId] = useState<number | null>(null);
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [createForm, setCreateForm] = useState({
    code: "",
    name: "",
    contactName: "",
    email: "",
    phone: "",
    note: "",
  });

  const [editForm, setEditForm] = useState({
    code: "",
    name: "",
    contactName: "",
    email: "",
    phone: "",
    note: "",
  });

  const [contractForm, setContractForm] = useState({
    supplierId: "",
    unitCost: "1",
    leadTimeDays: "0",
    moq: "1",
    lotSize: "1",
    primary: false,
  });

  const selectedSupplier = useMemo(
    () => suppliers.find((supplier) => supplier.id === selectedSupplierId) ?? null,
    [suppliers, selectedSupplierId],
  );

  useEffect(() => {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }
    let mounted = true;

    async function loadData() {
      setLoading(true);
      setError("");
      try {
        const [supplierData, productData] = await Promise.all([
          getSuppliers(currentCredentials!),
          getProducts(currentCredentials!),
        ]);
        if (!mounted) {
          return;
        }
        setSuppliers(supplierData);
        setProducts(productData);
        if (productData.length > 0) {
          setSelectedProductId(productData[0].id);
        }
      } catch (err) {
        if (!mounted) {
          return;
        }
        setError(err instanceof Error ? err.message : "仕入先情報の取得に失敗しました。");
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    void loadData();
    return () => {
      mounted = false;
    };
  }, [credentials]);

  useEffect(() => {
    const currentCredentials = credentials;
    const productId = selectedProductId;
    if (!currentCredentials || !productId) {
      setContracts([]);
      return;
    }
    let mounted = true;

    async function loadContracts() {
      setError("");
      try {
        const data = await getProductSuppliers(currentCredentials!, productId!);
        if (!mounted) {
          return;
        }
        setContracts(data);
      } catch (err) {
        if (mounted) {
          setError(err instanceof Error ? err.message : "契約条件の取得に失敗しました。");
        }
      }
    }

    void loadContracts();
    return () => {
      mounted = false;
    };
  }, [credentials, selectedProductId]);

  useEffect(() => {
    if (!selectedSupplier) {
      setEditForm({
        code: "",
        name: "",
        contactName: "",
        email: "",
        phone: "",
        note: "",
      });
      return;
    }
    setEditForm({
      code: selectedSupplier.code,
      name: selectedSupplier.name,
      contactName: selectedSupplier.contactName ?? "",
      email: selectedSupplier.email ?? "",
      phone: selectedSupplier.phone ?? "",
      note: selectedSupplier.note ?? "",
    });
  }, [selectedSupplier]);

  if (!state || !credentials) {
    return null;
  }

  async function refreshSuppliers() {
    const data = await getSuppliers(credentials!);
    setSuppliers(data);
  }

  async function refreshContracts() {
    const productId = selectedProductId;
    if (!productId) {
      return;
    }
    const data = await getProductSuppliers(credentials!, productId!);
    setContracts(data);
  }

  async function handleCreateSupplier(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");
    setIsMutating(true);

    try {
      await createSupplier(credentials!, {
        code: createForm.code.trim(),
        name: createForm.name.trim(),
        contactName: createForm.contactName.trim() || undefined,
        email: createForm.email.trim() || undefined,
        phone: createForm.phone.trim() || undefined,
        note: createForm.note.trim() || undefined,
      });
      setCreateForm({
        code: "",
        name: "",
        contactName: "",
        email: "",
        phone: "",
        note: "",
      });
      const message = "仕入先を作成しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshSuppliers();
    } catch (err) {
      const message = err instanceof Error ? err.message : "仕入先作成に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleUpdateSupplier(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSupplierId) {
      return;
    }
    setError("");
    setSuccess("");
    setIsMutating(true);

    try {
      await updateSupplier(credentials!, selectedSupplierId, {
        code: editForm.code.trim(),
        name: editForm.name.trim(),
        contactName: editForm.contactName.trim() || undefined,
        email: editForm.email.trim() || undefined,
        phone: editForm.phone.trim() || undefined,
        note: editForm.note.trim() || undefined,
      });
      const message = "仕入先を更新しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshSuppliers();
    } catch (err) {
      const message = err instanceof Error ? err.message : "仕入先更新に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleToggleSupplierActive(supplier: Supplier) {
    setError("");
    setSuccess("");
    setIsMutating(true);

    try {
      if (supplier.active) {
        await deactivateSupplier(credentials!, supplier.id);
      } else {
        await activateSupplier(credentials!, supplier.id);
      }
      const message = "仕入先の状態を更新しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshSuppliers();
    } catch (err) {
      const message = err instanceof Error ? err.message : "仕入先状態の更新に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleUpsertContract(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedProductId) {
      const message = "商品を選択してください。";
      setError(message);
      showError(message);
      return;
    }

    const supplierId = Number(contractForm.supplierId);
    if (!supplierId) {
      const message = "仕入先を選択してください。";
      setError(message);
      showError(message);
      return;
    }

    setError("");
    setSuccess("");
    setIsMutating(true);

    try {
      await upsertProductSupplier(credentials!, selectedProductId, {
        supplierId,
        unitCost: Number(contractForm.unitCost),
        leadTimeDays: Number(contractForm.leadTimeDays),
        moq: Number(contractForm.moq),
        lotSize: Number(contractForm.lotSize),
        primary: contractForm.primary,
      });
      setContractForm({
        supplierId: "",
        unitCost: "1",
        leadTimeDays: "0",
        moq: "1",
        lotSize: "1",
        primary: false,
      });
      const message = "契約条件を保存しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshContracts();
    } catch (err) {
      const message = err instanceof Error ? err.message : "契約条件の保存に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  async function handleRemoveContract(supplierId: number) {
    if (!selectedProductId) {
      return;
    }

    setError("");
    setSuccess("");
    setIsMutating(true);
    try {
      await removeProductSupplier(credentials!, selectedProductId, supplierId);
      const message = "契約条件を削除しました。";
      setSuccess(message);
      showSuccess(message);
      await refreshContracts();
    } catch (err) {
      const message = err instanceof Error ? err.message : "契約条件の削除に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsMutating(false);
    }
  }

  function fillContractForm(contract: ProductSupplierContract) {
    setContractForm({
      supplierId: String(contract.supplierId),
      unitCost: String(contract.unitCost),
      leadTimeDays: String(contract.leadTimeDays),
      moq: String(contract.moq),
      lotSize: String(contract.lotSize),
      primary: contract.primary,
    });
  }

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>仕入先マスタ</h2>
          <span style={{ color: "#607086", fontSize: 13 }}>{suppliers.length}件</span>
        </div>
        <p style={{ margin: "0 0 12px", color: "#607086" }}>
          仕入先を選択すると編集できます。契約条件は下段の「商品別 契約条件」で管理します。
        </p>

        {error && <p className="inline-error">{error}</p>}
        {success && <p style={{ color: "#137a49", marginTop: 6 }}>{success}</p>}

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>コード</th>
                <th>名称</th>
                <th>連絡先</th>
                <th>状態</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {suppliers.map((supplier) => (
                <tr key={supplier.id}>
                  <td>{supplier.code}</td>
                  <td>{supplier.name}</td>
                  <td>{supplier.contactName ?? "-"}</td>
                  <td>
                    <span className={`badge ${supplier.active ? "CONFIRMED" : "CANCELLED"}`}>
                      {supplier.active ? "有効" : "無効"}
                    </span>
                  </td>
                  <td>
                    <div className="button-row">
                      <button
                        className="button secondary"
                        type="button"
                        onClick={() => setSelectedSupplierId(supplier.id)}
                        disabled={isMutating}
                      >
                        選択
                      </button>
                      {canManage && (
                        <button
                          className="button secondary"
                          type="button"
                          onClick={() => handleToggleSupplierActive(supplier)}
                          disabled={isMutating}
                        >
                          {supplier.active ? "無効化" : "有効化"}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {!loading && suppliers.length === 0 && (
                <tr>
                  <td colSpan={5}>仕入先がありません。まず仕入先を作成してください。</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {canManage && (
        <div className="grid cols-2">
          <section className="card">
            <h2 style={{ marginBottom: 10 }}>仕入先作成（ADMIN）</h2>
            <form className="form-grid single" onSubmit={handleCreateSupplier}>
              <div className="field">
                <label htmlFor="create-supplier-code">コード</label>
                <input
                  id="create-supplier-code"
                  className="input"
                  value={createForm.code}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, code: event.target.value }))}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="create-supplier-name">名称</label>
                <input
                  id="create-supplier-name"
                  className="input"
                  value={createForm.name}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, name: event.target.value }))}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="create-supplier-contact">連絡先担当</label>
                <input
                  id="create-supplier-contact"
                  className="input"
                  value={createForm.contactName}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, contactName: event.target.value }))}
                />
              </div>
              <div className="field">
                <label htmlFor="create-supplier-email">メール</label>
                <input
                  id="create-supplier-email"
                  className="input"
                  value={createForm.email}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, email: event.target.value }))}
                />
              </div>
              <div className="field">
                <label htmlFor="create-supplier-phone">電話</label>
                <input
                  id="create-supplier-phone"
                  className="input"
                  value={createForm.phone}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, phone: event.target.value }))}
                />
              </div>
              <div className="field">
                <label htmlFor="create-supplier-note">備考</label>
                <textarea
                  id="create-supplier-note"
                  className="textarea"
                  value={createForm.note}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, note: event.target.value }))}
                />
              </div>
              <div className="button-row">
                <button className="button primary" type="submit" disabled={isMutating}>
                  作成
                </button>
              </div>
            </form>
          </section>

          <section className="card">
            <h2 style={{ marginBottom: 10 }}>仕入先編集（ADMIN）</h2>
            {!selectedSupplier && <p>仕入先を選択してください。</p>}
            {selectedSupplier && (
              <form className="form-grid single" onSubmit={handleUpdateSupplier}>
                <div className="field">
                  <label htmlFor="edit-supplier-code">コード</label>
                  <input
                    id="edit-supplier-code"
                    className="input"
                    value={editForm.code}
                    onChange={(event) => setEditForm((prev) => ({ ...prev, code: event.target.value }))}
                    required
                  />
                </div>
                <div className="field">
                  <label htmlFor="edit-supplier-name">名称</label>
                  <input
                    id="edit-supplier-name"
                    className="input"
                    value={editForm.name}
                    onChange={(event) => setEditForm((prev) => ({ ...prev, name: event.target.value }))}
                    required
                  />
                </div>
                <div className="field">
                  <label htmlFor="edit-supplier-contact">連絡先担当</label>
                  <input
                    id="edit-supplier-contact"
                    className="input"
                    value={editForm.contactName}
                    onChange={(event) => setEditForm((prev) => ({ ...prev, contactName: event.target.value }))}
                  />
                </div>
                <div className="field">
                  <label htmlFor="edit-supplier-email">メール</label>
                  <input
                    id="edit-supplier-email"
                    className="input"
                    value={editForm.email}
                    onChange={(event) => setEditForm((prev) => ({ ...prev, email: event.target.value }))}
                  />
                </div>
                <div className="field">
                  <label htmlFor="edit-supplier-phone">電話</label>
                  <input
                    id="edit-supplier-phone"
                    className="input"
                    value={editForm.phone}
                    onChange={(event) => setEditForm((prev) => ({ ...prev, phone: event.target.value }))}
                  />
                </div>
                <div className="field">
                  <label htmlFor="edit-supplier-note">備考</label>
                  <textarea
                    id="edit-supplier-note"
                    className="textarea"
                    value={editForm.note}
                    onChange={(event) => setEditForm((prev) => ({ ...prev, note: event.target.value }))}
                  />
                </div>
                <div className="button-row">
                  <button className="button primary" type="submit" disabled={isMutating}>
                    更新
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>
      )}

      <section className="card">
        <h2 style={{ marginBottom: 10 }}>商品別 契約条件</h2>

        <div className="form-grid">
          <div className="field">
            <label htmlFor="contract-product">商品</label>
            <select
              id="contract-product"
              className="select"
              value={selectedProductId ?? ""}
              onChange={(event) => setSelectedProductId(Number(event.target.value))}
            >
              <option value="">選択してください</option>
              {products.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.sku} / {product.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="table-wrap" style={{ marginTop: 12 }}>
          <table className="table">
            <thead>
              <tr>
                <th>仕入先</th>
                <th>単価</th>
                <th>LT(日)</th>
                <th>MOQ</th>
                <th>ロット</th>
                <th>主仕入先</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {contracts.map((contract) => (
                <tr key={contract.supplierId}>
                  <td>
                    {contract.supplierCode} / {contract.supplierName}
                  </td>
                  <td>{formatCurrency(contract.unitCost)}</td>
                  <td>{contract.leadTimeDays}</td>
                  <td>{contract.moq}</td>
                  <td>{contract.lotSize}</td>
                  <td>{contract.primary ? "YES" : "NO"}</td>
                  <td>
                    <div className="button-row">
                      <button className="button secondary" type="button" onClick={() => fillContractForm(contract)}>
                        編集
                      </button>
                      {canManage && (
                        <button
                          className="button danger"
                          type="button"
                          onClick={() => handleRemoveContract(contract.supplierId)}
                          disabled={isMutating}
                        >
                          削除
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {!loading && contracts.length === 0 && (
                <tr>
                  <td colSpan={7}>契約条件がありません。</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {canManage && (
          <form className="page" onSubmit={handleUpsertContract} style={{ marginTop: 12 }}>
            <div className="form-grid">
              <div className="field">
                <label htmlFor="contract-supplier">仕入先</label>
                <select
                  id="contract-supplier"
                  className="select"
                  value={contractForm.supplierId}
                  onChange={(event) => setContractForm((prev) => ({ ...prev, supplierId: event.target.value }))}
                  required
                >
                  <option value="">選択してください</option>
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
                <label htmlFor="contract-unit-cost">契約単価</label>
                <input
                  id="contract-unit-cost"
                  className="input"
                  type="number"
                  min={1}
                  step={1}
                  value={contractForm.unitCost}
                  onChange={(event) => setContractForm((prev) => ({ ...prev, unitCost: event.target.value }))}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="contract-lead-time">LT(日)</label>
                <input
                  id="contract-lead-time"
                  className="input"
                  type="number"
                  min={0}
                  step={1}
                  value={contractForm.leadTimeDays}
                  onChange={(event) => setContractForm((prev) => ({ ...prev, leadTimeDays: event.target.value }))}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="contract-moq">MOQ</label>
                <input
                  id="contract-moq"
                  className="input"
                  type="number"
                  min={1}
                  step={1}
                  value={contractForm.moq}
                  onChange={(event) => setContractForm((prev) => ({ ...prev, moq: event.target.value }))}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="contract-lot-size">ロットサイズ</label>
                <input
                  id="contract-lot-size"
                  className="input"
                  type="number"
                  min={1}
                  step={1}
                  value={contractForm.lotSize}
                  onChange={(event) => setContractForm((prev) => ({ ...prev, lotSize: event.target.value }))}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="contract-primary">主仕入先</label>
                <select
                  id="contract-primary"
                  className="select"
                  value={contractForm.primary ? "true" : "false"}
                  onChange={(event) =>
                    setContractForm((prev) => ({ ...prev, primary: event.target.value === "true" }))
                  }
                >
                  <option value="false">NO</option>
                  <option value="true">YES</option>
                </select>
              </div>
            </div>
            <div className="button-row">
              <button className="button primary" type="submit" disabled={isMutating}>
                契約条件を保存
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
}
