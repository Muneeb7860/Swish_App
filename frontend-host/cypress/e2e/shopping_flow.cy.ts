describe('Swish App Q-Commerce E2E Shopping Flow', () => {
  beforeEach(() => {
    // Intercept auth request to bypass MFA for direct testing
    cy.intercept('POST', '/api/auth/login', {
      statusCode: 200,
      body: {
        mfaRequired: false,
        sessionToken: null,
        token: 'mock-cypress-jwt-token'
      }
    }).as('loginRequest');

    // Intercept initial agent metrics call to avoid console warnings
    cy.intercept('GET', '/api/agent/metrics', {
      statusCode: 200,
      body: {
        dailyCost: 0.15,
        totalTokens: 12000,
        requestsCount: 42
      }
    }).as('metricsRequest');

    // Visit the frontend host
    cy.visit('/');
  });

  it('performs customer authentication, browses catalog, validates vouchers, triggers low-stock substitutions, applies invoice tip/ESG, and completes E2E checkout', () => {
    // ─── 1. SECURE MFA SIGN-IN JOURNEY ───
    cy.get('#mfa-login-portal').should('be.visible');
    
    // Choose Customer role
    cy.get('#mfa-select-role').select('customer');
    
    // Enter secure password key
    cy.get('#input-mfa-password').type('adminpassword');
    
    // Click authenticate credentials
    cy.get('#btn-mfa-request-otp').click();

    // Verify successful login by checking that the MFA overlay disappears and header brand is loaded
    cy.get('#mfa-login-portal').should('not.exist');
    cy.get('.app-header').should('be.visible');
    cy.contains('swiss_App').should('be.visible');

    // ─── 2. STORE CATALOG BROWSING & ESG / TIP INVOICE CALCULATIONS ───
    // Check that we are on the Customer tab by default
    cy.get('#tab-customer').should('have.class', 'active');
    
    // Add "Organic Fresh Milk" (price $3.49) to the cart
    cy.contains('Organic Fresh Milk').parents('.product-card').within(() => {
      cy.get('.add-cart-btn').click();
    });

    // Check that the Cart Drawer contains the added item and shows 1 item
    cy.get('.customer-cart-drawer').should('be.visible');
    cy.get('.customer-cart-drawer').contains('Organic Fresh Milk').should('be.visible');
    cy.get('.customer-cart-drawer').contains('1x $3.49').should('be.visible');

    // Select $2 Rider Tip
    cy.contains('ADD RIDER TIP').parent().within(() => {
      cy.contains('$2').click();
    });

    // Assert that the Rider Tip is added to the invoice breakdown
    cy.get('.customer-cart-drawer').contains('Rider Tip:').should('be.visible');
    cy.get('.customer-cart-drawer').contains('$2.00').should('be.visible');

    // Click "Return bags for $0.50 cash rebate offset"
    cy.get('#esg-bags').click();

    // Assert that the paper bag rebate is applied to the invoice
    cy.get('.customer-cart-drawer').contains('Paper Bag Rebate:').should('be.visible');
    cy.get('.customer-cart-drawer').contains('-$0.50').should('be.visible');

    // Validate overall total calculation:
    // Subtotal ($3.49) + Delivery ($2.99) + Tip ($2.00) - Rebate ($0.50) = $7.98
    cy.get('.customer-cart-drawer').contains('Total cost:').parent().within(() => {
      cy.contains('$7.98').should('be.visible');
    });

    // ─── 3. VOUCHER DISCOUNT VALIDATION IN PROFILE HUB ───
    // Click "My Profile Hub" tab inside the Customer App
    cy.contains('My Profile Hub').click();
    
    // Select "My Discount Vouchers" sidebar menu item
    cy.contains('My Discount Vouchers').click();

    // Assert that both standard promotional vouchers are present
    cy.contains('SWISSWELCOME5').should('be.visible');
    cy.contains('Get $5.00 cash voucher on your first grocery basket!').should('be.visible');
    cy.contains('FRESH10').should('be.visible');
    cy.contains('Flat $10.00 discount coupon on organic dairy orders.').should('be.visible');

    // ─── 4. MOCK INVENTORY DEPLETION STOCKOUT & SUBSTITUTION FLOW ───
    // Go back to the Browse Store Catalog tab
    cy.contains('Browse Store Catalog').click();

    // Find "Double Chocolate Muffins" (which has only 2 units left, triggering low-stock warning)
    cy.contains('Double Chocolate Muffins').parents('.product-card').within(() => {
      cy.contains('🔥 Only 2 left!').should('be.visible');
      cy.get('.add-cart-btn').click();
    });

    // Verify that the Stock Alert low-stock substitution modal pops up
    cy.get('.cert-modal-overlay').should('be.visible');
    cy.contains('Stock Alert: Running Low!').should('be.visible');

    // Assert that the suggested high-availability backup substitute ("Whole Wheat Sourdough") is shown
    cy.contains('Whole Wheat Sourdough').should('be.visible');

    // Click "Swap Item" button to exchange Muffins for Sourdough
    cy.contains('Swap Item').click();

    // Assert that the substitution modal has closed and the swapped substitute is in the cart instead
    cy.get('.cert-modal-overlay').should('not.exist');
    cy.get('.customer-cart-drawer').contains('Whole Wheat Sourdough').should('be.visible');

    // ─── 5. E2E CHECKOUT PERSISTENCE & SLA LIFECYCLE ───
    // Click "Pay via Wallet" to complete checkout
    cy.get('#btn-checkout-wallet').click();

    // Verify cart is cleared
    cy.get('.customer-cart-drawer').contains('Cart is empty').should('be.visible');

    // Assert that the Active SLA countdown bar is displayed at the top of the cockpit
    cy.contains('ACTIVE SLA COUNTDOWN').should('be.visible');
    cy.contains('Status: PICKING').should('be.visible');
  });
});
