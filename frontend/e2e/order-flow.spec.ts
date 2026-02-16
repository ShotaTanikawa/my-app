import { expect, test, type Page } from "@playwright/test";

async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: "ログイン" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

async function logout(page: Page) {
  await page.getByRole("button", { name: "Logout" }).click();
  await expect(page).toHaveURL(/\/login$/);
}

async function selectProductBySku(page: Page, sku: string) {
  const option = page.locator("#product-0 option", { hasText: sku }).first();
  await expect(page.locator("#product-0 option", { hasText: sku })).toHaveCount(1);
  const productId = await option.getAttribute("value");
  expect(productId).not.toBeNull();
  await page.locator("#product-0").selectOption(productId!);
}

async function getOrderNumber(page: Page): Promise<string> {
  const orderNumberText = await page
    .locator(".stat-label", { hasText: "受注番号" })
    .locator("xpath=following-sibling::div[1]")
    .textContent();
  const orderNumber = orderNumberText?.trim() ?? "";
  expect(orderNumber.length).toBeGreaterThan(0);
  return orderNumber;
}

async function expectAuditLogRow(
  page: Page,
  action: string,
  expectedTexts: string[],
): Promise<void> {
  await expect
    .poll(
      async () => {
        const rows = page.locator("tbody tr");
        const count = await rows.count();

        for (let index = 0; index < count; index += 1) {
          const text = (await rows.nth(index).innerText()).replace(/\s+/g, " ").trim();
          const includesAction = text.includes(action);
          const includesAllExpectedTexts = expectedTexts.every((value) => text.includes(value));
          if (includesAction && includesAllExpectedTexts) {
            return true;
          }
        }

        return false;
      },
      {
        timeout: 20_000,
        message: `Expected audit log row for action ${action}`,
      },
    )
    .toBe(true);
}

test.describe("Order operation flow", () => {
  test("admin creates product and stock, operator creates and confirms order", async ({ page }) => {
    const token = Date.now().toString();
    const sku = `E2E-${token}`;
    const productName = `E2E Product ${token}`;

    await login(page, "admin", "admin123");

    await page.goto("/products");
    await expect(page.getByRole("heading", { name: "商品一覧" })).toBeVisible();

    await page.locator("#create-sku").fill(sku);
    await page.locator("#create-name").fill(productName);
    await page.locator("#create-unit-price").fill("1200");
    await page.locator("#create-description").fill("Playwright E2E test product");
    await page.getByRole("button", { name: "作成" }).click();

    await expect(page.getByText("商品を作成しました。", { exact: true })).toBeVisible();

    const targetRow = page.locator("tr", { hasText: sku }).first();
    await expect(targetRow).toBeVisible();
    await targetRow.getByRole("button", { name: "選択" }).click();

    await page.locator("#stock-quantity").fill("30");
    await page.getByRole("button", { name: "在庫追加" }).click();
    await expect(page.getByText("在庫を追加しました。", { exact: true })).toBeVisible();

    await logout(page);
    await login(page, "operator", "operator123");

    await page.goto("/orders/new");
    await expect(page.getByRole("heading", { name: "新規受注作成" })).toBeVisible();

    await page.locator("#customer-name").fill("Playwright Confirm Customer");
    await selectProductBySku(page, sku);
    await page.locator("#quantity-0").fill("2");

    await page.getByRole("button", { name: "受注作成" }).click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    await expect(page.getByText("RESERVED", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: "受注確定" }).click();
    await expect(page.getByText("CONFIRMED", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "受注確定" })).toHaveCount(0);
  });

  test("operator creates and cancels order", async ({ page }) => {
    const token = (Date.now() + 1).toString();
    const sku = `E2E-CANCEL-${token}`;
    const productName = `E2E Cancel Product ${token}`;

    await login(page, "admin", "admin123");
    await page.goto("/products");

    await page.locator("#create-sku").fill(sku);
    await page.locator("#create-name").fill(productName);
    await page.locator("#create-unit-price").fill("900");
    await page.locator("#create-description").fill("Playwright cancel flow product");
    await page.getByRole("button", { name: "作成" }).click();

    await expect(page.getByText("商品を作成しました。", { exact: true })).toBeVisible();

    const targetRow = page.locator("tr", { hasText: sku }).first();
    await targetRow.getByRole("button", { name: "選択" }).click();

    await page.locator("#stock-quantity").fill("10");
    await page.getByRole("button", { name: "在庫追加" }).click();
    await expect(page.getByText("在庫を追加しました。", { exact: true })).toBeVisible();

    await logout(page);
    await login(page, "operator", "operator123");

    await page.goto("/orders/new");
    await page.locator("#customer-name").fill("Playwright Cancel Customer");
    await selectProductBySku(page, sku);
    await page.locator("#quantity-0").fill("1");
    await page.getByRole("button", { name: "受注作成" }).click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    await expect(page.getByText("RESERVED", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: "受注キャンセル" }).click();
    await expect(page.getByText("CANCELLED", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "受注キャンセル" })).toHaveCount(0);
  });

  test("admin can inspect audit logs after operator order confirmation", async ({ page }) => {
    const token = `${Date.now()}-audit`;
    const sku = `E2E-AUDIT-${token}`;
    const productName = `E2E Audit Product ${token}`;
    const customerName = `Playwright Audit Customer ${token}`;

    await login(page, "admin", "admin123");
    await page.goto("/products");

    await page.locator("#create-sku").fill(sku);
    await page.locator("#create-name").fill(productName);
    await page.locator("#create-unit-price").fill("1500");
    await page.locator("#create-description").fill("Playwright audit flow product");
    await page.getByRole("button", { name: "作成" }).click();
    await expect(page.getByText("商品を作成しました。", { exact: true })).toBeVisible();

    const targetRow = page.locator("tr", { hasText: sku }).first();
    await targetRow.getByRole("button", { name: "選択" }).click();
    await page.locator("#stock-quantity").fill("20");
    await page.getByRole("button", { name: "在庫追加" }).click();
    await expect(page.getByText("在庫を追加しました。", { exact: true })).toBeVisible();

    await logout(page);
    await login(page, "operator", "operator123");

    await page.goto("/orders/new");
    await page.locator("#customer-name").fill(customerName);
    await selectProductBySku(page, sku);
    await page.locator("#quantity-0").fill("2");
    await page.getByRole("button", { name: "受注作成" }).click();

    await expect(page).toHaveURL(/\/orders\/\d+$/);
    const orderNumber = await getOrderNumber(page);
    await page.getByRole("button", { name: "受注確定" }).click();
    await expect(page.getByText("CONFIRMED", { exact: true })).toBeVisible();

    await logout(page);
    await login(page, "admin", "admin123");

    await page.goto("/audit-logs");
    await expect(page.getByRole("heading", { name: "監査ログ" })).toBeVisible();

    await expectAuditLogRow(page, "PRODUCT_CREATE", ["admin", sku]);
    await expectAuditLogRow(page, "ORDER_CREATE", ["operator", customerName]);
    await expectAuditLogRow(page, "ORDER_CONFIRM", ["operator", orderNumber]);
  });
});
