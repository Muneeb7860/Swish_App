import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

const BACKEND_LOG_PATH = 'C:/Users/DELL 9420/Documents/swiss_App/dev/e2e/backend.log';
const ARTIFACTS_DIR = 'C:/Users/DELL 9420/.gemini/antigravity/brain/d972ace5-02e9-49d4-8399-1bb6c80d31b4';

// Helper to wait
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Helper to extract OTP from backend log file
function getOtpCode(logPath) {
  if (!fs.existsSync(logPath)) {
    return null;
  }
  const logs = fs.readFileSync(logPath, 'utf8');
  const matches = [...logs.matchAll(/PIN code:\s*(\d{6})/g)];
  if (matches.length > 0) {
    // Return the latest matched code
    return matches[matches.length - 1][1];
  }
  return null;
}

async function run() {
  console.log('[E2E TEST] Starting Playwright E2E browser automation...');
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  // Listen for console and error events
  page.on('console', msg => console.log(`[BROWSER CONSOLE] ${msg.text()}`));
  page.on('pageerror', err => console.error(`[BROWSER ERROR] ${err.toString()}`));

  try {
    // 1. Navigate to the App Shell
    console.log('[E2E TEST] Navigating to App Shell on http://localhost:3000...');
    await page.goto('http://localhost:3000');
    await page.setViewportSize({ width: 1280, height: 800 });

    // 2. Select Admin Role & Fill Password
    console.log('[E2E TEST] Filling credentials for role: admin...');
    await page.waitForSelector('#mfa-login-portal');
    await page.selectOption('#mfa-select-role', 'admin');
    await page.fill('#input-mfa-password', 'swiss-secure-password');
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'e2e_1_pre_login.png') });

    // 3. Click Request OTP and wait for the login API call to complete
    console.log('[E2E TEST] Requesting OTP code...');
    await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/api/auth/login'), { timeout: 15000 }),
      page.click('#btn-mfa-request-otp')
    ]);
    console.log('[E2E TEST] Login API responded. Polling for OTP PIN...');

    // 4. Polling backend.log to extract the generated OTP PIN
    console.log('[E2E TEST] Polling backend logs for PIN code...');
    let otpCode = null;
    for (let i = 0; i < 20; i++) {
      await sleep(1000);
      otpCode = getOtpCode(BACKEND_LOG_PATH);
      if (otpCode) {
        console.log(`[E2E TEST] Extracted OTP PIN successfully: ${otpCode}`);
        break;
      }
    }

    if (!otpCode) {
      throw new Error('Timeout: Could not find OTP PIN code in backend.log. Verify the backend service started correctly.');
    }

    // 5. Fill OTP & Verification
    console.log('[E2E TEST] Inputting OTP code and verifying session...');
    await page.waitForSelector('#input-mfa-otp');
    await page.fill('#input-mfa-otp', otpCode);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'e2e_2_otp_filled.png') });
    await page.click('#btn-mfa-verify-otp');

    // 6. Wait for dashboard unlock and Header
    console.log('[E2E TEST] Waiting for system dashboard unlock...');
    await page.waitForSelector('.app-header');
    console.log('[E2E TEST] Authenticated successfully! Swapping roles...');

    // 7. Verify Customer MFE view
    console.log('[E2E TEST] Inspecting Customer MFE...');
    await page.click('#tab-customer');
    await sleep(2000); // Allow remote Customer MFE bundles to load and execute
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'e2e_3_customer_view.png') });

    // 8. Verify Rider MFE view
    console.log('[E2E TEST] Inspecting Rider MFE...');
    await page.click('#tab-rider');
    await sleep(1500);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'e2e_4_rider_view.png') });

    // 9. Verify Picker MFE view
    console.log('[E2E TEST] Inspecting Picker MFE...');
    await page.click('#tab-inventory');
    await sleep(1500);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'e2e_5_picker_view.png') });

    // 10. Verify System Admin MFE view
    console.log('[E2E TEST] Inspecting Admin Control Panel...');
    await page.click('#tab-admin');
    await sleep(1500);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'e2e_6_admin_view.png') });

    console.log('[E2E TEST] E2E Verification complete! Captures successfully exported to artifacts.');
  } catch (err) {
    console.error('[E2E TEST] Failure during E2E verification run:', err);
    await page.screenshot({ path: path.join(ARTIFACTS_DIR, 'e2e_error.png') });
    process.exit(1);
  } finally {
    await browser.close();
  }
}

run();
