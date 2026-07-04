# Phase 2: Tier 1 Extraction Templates

Ready-to-extract component abstractions and utilities for shared-ui library.

---

## 1. Unified Skeleton Component

**Source**: frontend-host/src/components/LoadingSkeleton.tsx  
**Reuse**: 5 apps (customer, host, admin, rider, b2b)  
**Impact**: Eliminates 100+ lines of duplicate loading state code

### Current (Fragmented)
```tsx
// frontend-host
export const ProductCardSkeleton: React.FC = () => { /* 15 lines */ }
export const ProductGridSkeleton: React.FC<{count?: 8}> = () => { /* 8 lines */ }
export const TableRowsSkeleton: React.FC<{rows?: 5; cols?: 4}> = () => { /* 20 lines */ }
export const GenericCardSkeleton: React.FC = () => { /* 12 lines */ }
// Total: 55 lines per copy × reuse = 275 lines fragmented
```

### Unified (Proposed)
```tsx
// shared-ui/src/components/Skeleton.tsx

import type React from 'react'

type SkeletonVariant = 'product-card' | 'product-grid' | 'table-rows' | 'generic-card'

interface SkeletonProps {
  variant: SkeletonVariant
  count?: number  // for grids
  rows?: number   // for tables
  cols?: number   // for tables
}

/**
 * Unified loading skeleton component with 4 variants.
 * Displays animated placeholder while data loads.
 */
export const Skeleton: React.FC<SkeletonProps> = ({
  variant,
  count = 8,
  rows = 5,
  cols = 4,
}) => {
  switch (variant) {
    case 'product-card':
      return (
        <div className="glass-card product-card" style={{ cursor: 'default' }}>
          <div className="skeleton-image skeleton-shimmer" />
          <div className="skeleton-text medium skeleton-shimmer" />
          <div className="product-info-row" style={{ marginTop: '1rem' }}>
            <div
              className="skeleton-text short skeleton-shimmer"
              style={{ margin: 0, height: 16 }}
            />
            <div
              className="skeleton-shimmer"
              style={{ width: 60, height: 28, borderRadius: 8 }}
            />
          </div>
        </div>
      )

    case 'product-grid':
      return (
        <div className="products-grid">
          {Array.from({ length: count }).map((_, i) => (
            <Skeleton key={i} variant="product-card" />
          ))}
        </div>
      )

    case 'table-rows':
      return (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '1rem',
          width: '100%',
        }}>
          {Array.from({ length: rows }).map((_, r) => (
            <div
              key={r}
              style={{
                display: 'flex',
                gap: '1rem',
                padding: '1rem',
                background: 'rgba(255, 255, 255, 0.02)',
                border: '1px solid rgba(255, 255, 255, 0.05)',
                borderRadius: 12,
                alignItems: 'center',
              }}
            >
              {Array.from({ length: cols }).map((_, c) => (
                <div
                  key={c}
                  className="skeleton-shimmer skeleton-text"
                  style={{
                    flex: c === 0 ? 2 : 1,
                    margin: 0,
                    height: 14,
                  }}
                />
              ))}
            </div>
          ))}
        </div>
      )

    case 'generic-card':
      return (
        <div
          className="glass-card"
          style={{
            padding: '1.5rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.8rem',
          }}
        >
          <div
            className="skeleton-text medium skeleton-shimmer"
            style={{ height: 18 }}
          />
          <div className="skeleton-text skeleton-shimmer" />
          <div className="skeleton-text skeleton-shimmer" />
          <div className="skeleton-text short skeleton-shimmer" />
        </div>
      )

    default:
      return null
  }
}

// Convenience exports for migration
export const ProductCardSkeleton: React.FC = () => <Skeleton variant="product-card" />
export const ProductGridSkeleton: React.FC<{count?: 8}> = (props) => <Skeleton variant="product-grid" {...props} />
export const TableRowsSkeleton: React.FC<{rows?: 5; cols?: 4}> = (props) => <Skeleton variant="table-rows" {...props} />
export const GenericCardSkeleton: React.FC = () => <Skeleton variant="generic-card" />
```

**Migration**:
```tsx
// Before (each app)
import { ProductCardSkeleton } from './components/LoadingSkeleton'

// After (shared)
import { Skeleton, ProductCardSkeleton } from '@swish/shared-ui'

// Or use new API directly
<Skeleton variant="product-grid" count={12} />
```

**CSS Classes Required**:
- `.glass-card` — from consolidated tokens
- `.skeleton-image`, `.skeleton-text`, `.skeleton-shimmer` — unified animation
- `.product-card`, `.products-grid` — common layouts

---

## 2. Unified AuthPortal Component

**Sources**:
- frontend-customer/src/components/AuthGate.tsx (100+ lines)
- frontend-admin/src/components/AdminLogin.tsx (60+ lines)
- frontend-host/src/components/MfaLoginPortal.tsx (150+ lines)

**Reuse**: 3 apps (customer, admin, host)  
**Impact**: Eliminates 300+ lines of auth logic duplication + maintenance burden

### Current (Fragmented)

| Source | Component | Purpose | Props | Lines |
|--------|-----------|---------|-------|-------|
| customer | AuthGate | Dual-mode (login/register) | onAuth callback | 100+ |
| admin | AdminLogin | Admin JWT exchange | onLogin callback | 60+ |
| host | MfaLoginPortal | MFA + TOTP + SMS | 13 separate props! | 150+ |

### Unified (Proposed)

```tsx
// shared-ui/src/components/AuthPortal.tsx

import type React from 'react'
import { useState } from 'react'

export interface AuthSession {
  token: string
  sessionId?: string
  userId?: string
  role?: string
}

export type AuthRole = 'customer' | 'admin' | 'rider'
export type AuthStep = 'credentials' | 'mfa' | 'success'
export type MfaMethod = 'sms' | 'totp'

export interface AuthPortalProps {
  /** User role (customer/admin/rider) determines API endpoint and features */
  role: AuthRole

  /** Enable multi-factor authentication */
  mfaEnabled?: boolean

  /** Available MFA methods (default: ['sms', 'totp']) */
  mfaMethods?: MfaMethod[]

  /** API base URL (default: window.location.origin + /api/v1/auth) */
  apiUrl?: string

  /** Called on successful authentication */
  onAuthSuccess: (session: AuthSession) => void

  /** Called on auth error */
  onAuthError?: (error: Error) => void

  /** Pre-fill email (e.g., for admin/dev) */
  defaultEmail?: string

  /** Custom form label */
  formLabel?: string

  /** Custom submit button text */
  submitLabel?: string
}

/**
 * Unified authentication portal for multi-role applications.
 * Handles login, registration, and MFA flows in a single component.
 *
 * Supports:
 * - Customer: Login + Register (dual-mode)
 * - Admin: Single-purpose login with JWT exchange
 * - Rider: Similar to customer, role-specific
 * - MFA: Optional SMS or TOTP verification
 */
export const AuthPortal: React.FC<AuthPortalProps> = ({
  role,
  mfaEnabled = false,
  mfaMethods = ['sms', 'totp'],
  apiUrl = `${import.meta.env.VITE_API_URL ?? window.location.origin}/api/v1/auth`,
  onAuthSuccess,
  onAuthError,
  defaultEmail = role === 'admin' ? 'admin@swish.local' : '',
  formLabel = `${role.charAt(0).toUpperCase() + role.slice(1)} Login`,
  submitLabel = 'Sign In',
}) => {
  // Mode (login vs register) — only for customer
  const [mode, setMode] = useState<'login' | 'register'>(role === 'customer' ? 'login' : 'credentials')
  const [step, setStep] = useState<AuthStep>('credentials')

  // Form state
  const [email, setEmail] = useState(defaultEmail)
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // MFA state
  const [mfaMethod, setMfaMethod] = useState<MfaMethod>('sms')
  const [mfaOtp, setMfaOtp] = useState('')
  const [mfaSecret, setMfaSecret] = useState('')
  const [mfaTimer, setMfaTimer] = useState(0)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const endpoint = role === 'admin'
        ? '/login'
        : mode === 'login'
          ? '/login'
          : '/register'

      const body: Record<string, any> = { email, password }

      // Add device fingerprint for customer login
      if (role === 'customer' && mode === 'login') {
        body.deviceFingerprint = navigator.userAgent.slice(0, 64)
      }

      const response = await fetch(`${apiUrl}${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })

      const data = await response.json()

      if (!response.ok) {
        throw new Error(data.message ?? `Error ${response.status}`)
      }

      // Registration success → auto-switch to login
      if (mode === 'register') {
        setMode('login')
        setError('Account created — please log in.')
        setLoading(false)
        return
      }

      // Check if MFA is required
      if (mfaEnabled && data.mfaRequired) {
        setMfaSecret(data.mfaSecret)
        setStep('mfa')
        setLoading(false)
        return
      }

      // Success
      onAuthSuccess({
        token: data.token,
        sessionId: data.sessionId,
        userId: data.userId,
        role: data.role ?? role,
      })
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Unknown error')
      setError(error.message)
      onAuthError?.(error)
    } finally {
      setLoading(false)
    }
  }

  const handleMfaVerify = async () => {
    setLoading(true)
    setError('')

    try {
      const response = await fetch(`${apiUrl}/verify-mfa`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          mfaSecret,
          otp: mfaOtp,
          method: mfaMethod,
        }),
      })

      const data = await response.json()

      if (!response.ok) {
        throw new Error(data.message ?? 'MFA verification failed')
      }

      onAuthSuccess({
        token: data.token,
        sessionId: data.sessionId,
        userId: data.userId,
      })
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Unknown error')
      setError(error.message)
      onAuthError?.(error)
    } finally {
      setLoading(false)
    }
  }

  const containerStyle: React.CSSProperties = {
    maxWidth: 400,
    margin: '2rem auto',
    padding: '2rem',
    background: 'var(--bg-glass)',
    border: '1px solid var(--border-default)',
    borderRadius: 'var(--radius-lg)',
    backdropFilter: 'blur(20px)',
  }

  const fieldStyle: React.CSSProperties = {
    width: '100%',
    padding: '0.75rem',
    marginTop: '0.5rem',
    background: 'var(--bg-root)',
    border: '1px solid var(--border-default)',
    borderRadius: 'var(--radius-md)',
    color: 'var(--text-primary)',
    fontSize: 'var(--text-sm)',
    fontFamily: 'var(--font-sans)',
  }

  // MFA Step
  if (step === 'mfa') {
    return (
      <div style={containerStyle}>
        <h2 style={{ marginBottom: '1rem', color: 'var(--text-primary)' }}>
          Verify Your Identity
        </h2>

        {mfaMethods.includes('sms') && (
          <label style={{ display: 'flex', alignItems: 'center', marginBottom: '1rem' }}>
            <input
              type="radio"
              checked={mfaMethod === 'sms'}
              onChange={() => setMfaMethod('sms')}
            />
            <span style={{ marginLeft: '0.5rem', color: 'var(--text-secondary)' }}>
              SMS Code
            </span>
          </label>
        )}

        {mfaMethods.includes('totp') && (
          <label style={{ display: 'flex', alignItems: 'center', marginBottom: '1rem' }}>
            <input
              type="radio"
              checked={mfaMethod === 'totp'}
              onChange={() => setMfaMethod('totp')}
            />
            <span style={{ marginLeft: '0.5rem', color: 'var(--text-secondary)' }}>
              Authenticator App
            </span>
          </label>
        )}

        <input
          type="text"
          placeholder="Enter 6-digit code"
          value={mfaOtp}
          onChange={(e) => setMfaOtp(e.target.value.slice(0, 6))}
          style={fieldStyle}
          maxLength={6}
        />

        {error && (
          <div style={{
            marginTop: '0.5rem',
            padding: '0.5rem',
            background: 'var(--error-muted)',
            color: 'var(--error)',
            borderRadius: 'var(--radius-sm)',
            fontSize: 'var(--text-xs)',
          }}>
            {error}
          </div>
        )}

        <button
          onClick={handleMfaVerify}
          disabled={loading || mfaOtp.length < 6}
          style={{
            width: '100%',
            marginTop: '1rem',
            padding: '0.75rem',
            background: loading ? 'var(--accent-muted)' : 'var(--accent)',
            color: '#fff',
            border: 'none',
            borderRadius: 'var(--radius-md)',
            cursor: loading ? 'not-allowed' : 'pointer',
            fontWeight: 600,
            opacity: loading ? 0.6 : 1,
          }}
        >
          {loading ? 'Verifying...' : 'Verify'}
        </button>
      </div>
    )
  }

  // Credentials Step
  return (
    <div style={containerStyle}>
      <h2 style={{ marginBottom: '1rem', textAlign: 'center', color: 'var(--text-primary)' }}>
        {formLabel}
      </h2>

      <form onSubmit={handleSubmit}>
        <div>
          <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
            Email
          </label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            style={fieldStyle}
          />
        </div>

        <div style={{ marginTop: '1rem' }}>
          <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
            Password
          </label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            style={fieldStyle}
          />
        </div>

        {error && (
          <div style={{
            marginTop: '1rem',
            padding: '0.75rem',
            background: 'var(--error-muted)',
            color: 'var(--error)',
            borderRadius: 'var(--radius-sm)',
            fontSize: 'var(--text-xs)',
          }}>
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={loading}
          style={{
            width: '100%',
            marginTop: '1.5rem',
            padding: '0.75rem',
            background: loading ? 'var(--accent-muted)' : 'var(--accent)',
            color: '#fff',
            border: 'none',
            borderRadius: 'var(--radius-md)',
            cursor: loading ? 'not-allowed' : 'pointer',
            fontWeight: 600,
            fontSize: 'var(--text-sm)',
            opacity: loading ? 0.6 : 1,
            transition: 'opacity 0.2s',
          }}
        >
          {loading ? 'Signing in...' : submitLabel}
        </button>
      </form>

      {role === 'customer' && (
        <div style={{ marginTop: '1rem', textAlign: 'center', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
          {mode === 'login' ? (
            <>
              Don't have an account?{' '}
              <button
                onClick={() => setMode('register')}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--accent)',
                  cursor: 'pointer',
                  textDecoration: 'underline',
                }}
              >
                Register
              </button>
            </>
          ) : (
            <>
              Already have an account?{' '}
              <button
                onClick={() => setMode('login')}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--accent)',
                  cursor: 'pointer',
                  textDecoration: 'underline',
                }}
              >
                Login
              </button>
            </>
          )}
        </div>
      )}
    </div>
  )
}
```

**Migration**:
```tsx
// Before (3 separate components across 3 apps)
// customer:
<AuthGate onAuth={(session) => handleAuth(session)} />
// admin:
<AdminLogin onLogin={(email, pw) => handleAdminLogin(email, pw)} />
// host:
<MfaLoginPortal isAuthenticated={...} mfaStep={...} ... (13 props) />

// After (single component)
<AuthPortal
  role="customer"
  mfaEnabled={true}
  onAuthSuccess={(session) => handleAuth(session)}
/>

<AuthPortal
  role="admin"
  apiUrl="http://localhost:8083/api/v1/auth"
  onAuthSuccess={(session) => handleAuth(session)}
/>

<AuthPortal
  role="rider"
  mfaEnabled={true}
  mfaMethods={['sms']}
  onAuthSuccess={(session) => handleAuth(session)}
/>
```

---

## 3. Unified Design Tokens (tokens.css)

**Source**: Consolidate from all 5 apps  
**Output**: Single `tokens.css` (~300 lines)  
**Savings**: 12,401 → 3,500 lines total CSS

### Token Organization

```css
/* tokens.css — single source of truth */

@layer tokens {
  :root {
    /* ── BACKGROUNDS (from b2b) ── */
    --bg-root: #06090f;
    --bg-surface: #0c1120;
    --bg-elevated: #111827;
    --bg-muted: #1a2236;
    --bg-glass: rgba(15, 23, 42, 0.65);
    --bg-glass-strong: rgba(15, 23, 42, 0.85);

    /* ── TEXT (universal) ── */
    --text-primary: #f1f5f9;
    --text-secondary: #94a3b8;
    --text-muted: #64748b;
    --text-disabled: #475569;

    /* ── STATUS COLORS (universal) ── */
    --success: #10b981;
    --warning: #f59e0b;
    --error: #ef4444;
    --info: #06b6d4;

    /* ── ROLE-SPECIFIC ACCENTS (from customer) ── */
    --color-customer: #10b981;   /* green */
    --color-rider: #f59e0b;      /* amber */
    --color-admin: #ef4444;      /* red */
    --color-inventory: #3b82f6;  /* blue */
    --color-engine: #06b6d4;     /* cyan */

    /* ── SPACING (standardized 4px scale) ── */
    --space-0: 0px;
    --space-1: 4px;
    --space-2: 8px;
    --space-3: 12px;
    --space-4: 16px;
    --space-5: 20px;
    --space-6: 24px;
    --space-7: 32px;
    --space-8: 40px;

    /* ── TYPOGRAPHY (consistent) ── */
    --font-sans: "Outfit", "Inter", system-ui, -apple-system, sans-serif;
    --font-mono: "Fira Code", ui-monospace, Consolas, monospace;
    
    --text-xs: 0.6875rem;
    --text-sm: 0.8125rem;
    --text-base: 0.9375rem;
    --text-lg: 1.125rem;
    --text-xl: 1.5rem;
    --text-2xl: 1.875rem;

    /* ── BORDERS & RADII ── */
    --radius-sm: 6px;
    --radius-md: 10px;
    --radius-lg: 14px;
    --radius-xl: 20px;
    --radius-full: 9999px;

    --border-default: rgba(255, 255, 255, 0.07);
    --border-subtle: rgba(255, 255, 255, 0.04);
    --border-strong: rgba(255, 255, 255, 0.12);

    /* ── SHADOWS ── */
    --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.3), 0 1px 2px rgba(0, 0, 0, 0.2);
    --shadow-md: 0 4px 12px rgba(0, 0, 0, 0.35), 0 2px 4px rgba(0, 0, 0, 0.25);
    --shadow-lg: 0 12px 32px rgba(0, 0, 0, 0.45), 0 4px 8px rgba(0, 0, 0, 0.3);
    --shadow-glow: 0 0 20px rgba(99, 102, 241, 0.15), 0 0 60px rgba(99, 102, 241, 0.05);

    /* ── MOTION ── */
    --duration-fast: 150ms;
    --duration-normal: 250ms;
    --duration-slow: 400ms;
    --ease-smooth: cubic-bezier(0.4, 0, 0.2, 1);
    --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  }
}
```

**Usage across apps**:
```tsx
// Before (each app defined its own)
// frontend-customer:
const bg = '#0b0f19'
const text = '#f8fafc'

// After (all use unified)
<div style={{
  background: 'var(--bg-surface)',
  color: 'var(--text-primary)',
  padding: 'var(--space-4)',
}}>
```

---

## Implementation Order

1. **Skeleton** (15 min) → zero risk, immediate value
2. **AuthPortal** (1 hr) → high consolidation (300 lines)
3. **Tokens** (45 min) → foundation for all apps

**Total Phase 2A**: ~2 hours

---

**Next**: Proceed with extraction or refine design?
