/**
 * Swish App – Test 5: FMCG Catalog Import & Dynamic Pricing
 * ────────────────────────────────────────────────────────
 * Covers: FMCG Import Console UI & Integration flow
 *
 * Sequence:
 *  1. Open a clean browser session → visit http://localhost:3000
 *  2. Login as Administrator (admin)
 *  3. Wait for Admin dashboard to mount
 *  4. Locate "Global FMCG Import & Dynamic Pricing Hub" card
 *  5. Click the "Import & Price FMCG Products" button
 *  6. Verify success status and imported products list in the table
 *  7. Assert: no uncaught console errors
 */

import { expect, test } from "@playwright/test";
import { collectConsoleErrors, interceptAPIs, loginAs } from "./helpers";

test.describe("Test 5 — FMCG Catalog Import & Dynamic Pricing Console", () => {
	test.beforeEach(async ({ page }) => {
		await interceptAPIs(page);

		// Intercept the FMCG catalog import POST API
		await page.route("**/api/v1/products/import-fmcg", async (route) => {
			await route.fulfill({
				status: 200,
				contentType: "application/json",
				body: JSON.stringify([
					{
						barcode: "7613035449626",
						name: "Nestle Chocapic Céréales",
						brand: "Nestlé",
						category: "Snacks & Drinks",
						emoji: "🍫",
						basePrice: 4.5,
						dynamicPrice: 4.86,
						surgeMultiplier: 1.2,
						discountPercent: 10.0,
						pricingRationale:
							"Surge applied. Near expiry discount of 10% active.",
						source: "API",
						status: "SUCCESS",
					},
					{
						barcode: "7622202225512",
						name: "Cadbury Oreo Biscuits",
						brand: "Cadbury",
						category: "Snacks & Drinks",
						emoji: "🍪",
						basePrice: 2.2,
						dynamicPrice: 2.38,
						surgeMultiplier: 1.2,
						discountPercent: 10.0,
						pricingRationale: "Standard zone surge of 20% applied.",
						source: "API",
						status: "SUCCESS",
					},
					{
						barcode: "5449000000996",
						name: "Coca-Cola Original 330ml",
						brand: "Coca-Cola",
						category: "Snacks & Drinks",
						emoji: "🥤",
						basePrice: 1.8,
						dynamicPrice: 1.94,
						surgeMultiplier: 1.2,
						discountPercent: 10.0,
						pricingRationale: "High demand surge active.",
						source: "FALLBACK",
						status: "SUCCESS",
					},
				]),
			});
		});
	});

	test("FMCG catalog import and dynamic pricing recalculation flow runs successfully", async ({
		page,
	}) => {
		const consoleErrors = collectConsoleErrors(page);

		// ── Step 1 & 2: Visit app and login as admin ──
		await loginAs(page, "admin");

		// ── Step 3: Wait for Admin Panel to mount ──
		await page
			.locator(".admin-dashboard")
			.waitFor({ state: "visible", timeout: 20_000 });
		await expect(page.locator("#tab-admin")).toHaveClass(/active/);

		// ── Step 4: Locate FMCG Console Card ──
		const fmcgCard = page
			.locator("text=Global FMCG Import & Dynamic Pricing Hub")
			.locator("..");
		await expect(fmcgCard).toBeVisible();

		// Verify card description is present
		await expect(
			page.locator(
				"text=Fetch live FMCG product metadata (Nestlé, Cadbury, Mondelez, Coca-Cola, PepsiCo, Unilever, Dabur, Himalaya, ITC, P&G)",
			),
		).toBeVisible();

		// ── Step 5: Click the import button ──
		const importBtn = fmcgCard.locator("button", {
			hasText: "Import & Price FMCG Products",
		});
		await expect(importBtn).toBeVisible();
		await importBtn.click();

		// ── Step 6: Verify success status and populated products table ──
		const successMsg = page.locator("text=✓ Successfully Imported 10 Items");
		await expect(successMsg).toBeVisible({ timeout: 10_000 });

		// Verify the summary table exists and matches mock elements
		const table = fmcgCard.locator("table");
		await expect(table).toBeVisible();

		// Check rows mapping
		await expect(table.locator("text=Nestle Chocapic Céréales")).toBeVisible();
		await expect(table.locator("text=Cadbury Oreo Biscuits")).toBeVisible();
		await expect(table.locator("text=Coca-Cola Original 330ml")).toBeVisible();

		// Check the source tags
		const apiTag = table.locator("text=API").first();
		const fallbackTag = table.locator("text=FALLBACK").first();
		await expect(apiTag).toBeVisible();
		await expect(fallbackTag).toBeVisible();

		// ── Step 7: Assert no console errors ──
		const criticalErrors = consoleErrors.filter(
			(e) =>
				!e.includes("Warning:") &&
				!e.includes("Download the React DevTools") &&
				!e.includes("ERR_CONNECTION_REFUSED") &&
				!e.includes("ERR_FAILED") &&
				!e.includes("net::ERR"),
		);
		expect(
			criticalErrors,
			`Unexpected console errors: ${JSON.stringify(criticalErrors)}`,
		).toEqual([]);
	});

	test("Customer storefront renders FMCG brand filter and dynamic pricing badges", async ({
		page,
	}) => {
		const consoleErrors = collectConsoleErrors(page);

		// Intercept customer catalog to return FMCG items
		await page.route("**/api/customer/catalog", async (route) => {
			await route.fulfill({
				status: 200,
				contentType: "application/json",
				body: JSON.stringify([
					{
						item_id: "p1",
						name: "Organic Fresh Milk",
						price: 3.49,
						stock: 12,
						category: "Dairy & Eggs",
						emoji: "🥛",
						perishable: true,
					},
					{
						item_id: "brand-7613035449626",
						name: "Nestle Chocapic Céréales",
						price: 4.86,
						stock: 25,
						category: "Snacks & Drinks",
						emoji: "🍫",
						perishable: false,
					},
					{
						item_id: "brand-7622202225512",
						name: "Cadbury Oreo Biscuits",
						price: 1.98,
						stock: 15,
						category: "Snacks & Drinks",
						emoji: "🍪",
						perishable: false,
					},
				]),
			});
		});

		// ── Step 1 & 2: Log in as customer ──
		await loginAs(page, "customer");

		// ── Step 3: Wait for Customer MFE to mount ──
		await page
			.locator(".customer-dashboard")
			.waitFor({ state: "visible", timeout: 20_000 });

		// Ensure we're on the catalog tab
		const catalogTab = page.locator(".customer-tab-btn", {
			hasText: "Browse Store Catalog",
		});
		await catalogTab.click();
		await page.waitForTimeout(1000);

		// ── Step 4: Verify brand filter presence and interaction ──
		const brandHeader = page.locator("text=Shop by Top FMCG Brand");
		await expect(brandHeader).toBeVisible();

		const nestleBtn = page.locator("button.brand-pill", {
			hasText: "🍫 Nestlé",
		});
		await expect(nestleBtn).toBeVisible();

		// Click the Nestlé brand filter
		await nestleBtn.click();
		await page.waitForTimeout(1000);

		// Verify indicator banner
		const filterBanner = page.locator(
			"text=🎯 Filter Active: Showing Nestle items",
		);
		await expect(filterBanner).toBeVisible();

		// Verify Nestlé product card and its surge pricing badge
		const nestleCard = page
			.locator(".product-card", { hasText: "Nestle Chocapic" })
			.first();
		await expect(nestleCard).toBeVisible();
		await expect(nestleCard.locator("text=+$0.36 Surge")).toBeVisible();

		// Click Cadbury brand filter
		const cadburyBtn = page.locator("button.brand-pill", {
			hasText: "🍬 Cadbury",
		});
		await expect(cadburyBtn).toBeVisible();
		await cadburyBtn.click();
		await page.waitForTimeout(1000);

		// Verify Cadbury product card and its discount pricing badge (base = 2.20, price = 1.98 -> 10% OFF)
		const cadburyCard = page
			.locator(".product-card", { hasText: "Cadbury Oreo" })
			.first();
		await expect(cadburyCard).toBeVisible();
		await expect(cadburyCard.locator("text=10% OFF")).toBeVisible();

		// Verify console errors
		const criticalErrors = consoleErrors.filter(
			(e) =>
				!e.includes("Warning:") &&
				!e.includes("Download the React DevTools") &&
				!e.includes("ERR_CONNECTION_REFUSED") &&
				!e.includes("ERR_FAILED") &&
				!e.includes("net::ERR"),
		);
		expect(criticalErrors).toEqual([]);
	});
});
