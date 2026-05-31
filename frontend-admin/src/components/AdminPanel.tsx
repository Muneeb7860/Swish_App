import React from 'react';
import * as Lucide from 'lucide-react';

export default function AdminPanel({
  coldChainBreakdownActive,
  setColdChainBreakdownActive,
  wholesalerOutageActive,
  setWholesalerOutageActive,
  paymentOutageActive,
  setPaymentOutageActive,
  redisCrashActive,
  setRedisCrashActive,
  dbLatencyActive,
  setDbLatencyActive,
  riderTrafficActive,
  setRiderTrafficActive,
  simulateTelemetryFraud,
  setSimulateTelemetryFraud,
  onboardingQueue,
  handleApproveOnboard,
  hitlQueue,
  handleReleaseHitl,
  handleVoidHitl
}) {
  return (
    <div className="admin-dashboard" style={{ display: 'flex', gap: '1.25rem' }}>
      
      {/* Chaos Panel */}
      <div style={{ flex: 1.2, display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        <div className="glass-card" style={{ padding: '1rem', borderLeft: '3px solid var(--color-admin)' }}>
          <h3 style={{ fontWeight: 800, color: 'var(--color-admin)', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <Lucide.Flame size={18} />
            Chaos Engineering Control Desk
          </h3>
          <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
            Inject database latency spikes, telemetry geofencing mismatches, cold chain warming anomalies, wholesaler fallbacks, or gateway outages.
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1.25rem' }}>
            {/* Cold Chain Breakdown Switch */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', padding: '0.5rem', borderRadius: '6px' }}>
              <div>
                <span style={{ fontSize: '0.8rem', fontWeight: 700 }}>Simulate Perishable Cold Chain Outage</span>
                <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Cargo container warms up by +1.8°C/s in transit.</div>
              </div>
              <input 
                type="checkbox" 
                checked={coldChainBreakdownActive} 
                onChange={(e) => setColdChainBreakdownActive(e.target.checked)} 
                style={{ accentColor: 'var(--color-admin)', scale: '1.2', cursor: 'pointer' }}
              />
            </div>

            {/* Wholesaler Outage Switch */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', padding: '0.5rem', borderRadius: '6px' }}>
              <div>
                <span style={{ fontSize: '0.8rem', fontWeight: 700 }}>Simulate Primary Wholesaler Supplier Outage</span>
                <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>B2B restocks route to Secondary supplier ($35 surcharge, -20 trust).</div>
              </div>
              <input 
                type="checkbox" 
                checked={wholesalerOutageActive} 
                onChange={(e) => setWholesalerOutageActive(e.target.checked)} 
                style={{ accentColor: 'var(--color-admin)', scale: '1.2', cursor: 'pointer' }}
              />
            </div>

            {/* Payment Outage Switch */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', padding: '0.5rem', borderRadius: '6px' }}>
              <div>
                <span style={{ fontSize: '0.8rem', fontWeight: 700 }}>Simulate Payment Gateways Down</span>
                <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Triggers gateway failover chain: Swipe ➔ PayPal ➔ Cash on Delivery (COD).</div>
              </div>
              <input 
                type="checkbox" 
                checked={paymentOutageActive} 
                onChange={(e) => setPaymentOutageActive(e.target.checked)} 
                style={{ accentColor: 'var(--color-admin)', scale: '1.2', cursor: 'pointer' }}
              />
            </div>

            {/* Geotag Fraud Switch */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', padding: '0.5rem', borderRadius: '6px' }}>
              <div>
                <span style={{ fontSize: '0.8rem', fontWeight: 700 }}>Simulate GPS Geotag / Proximity Fraud</span>
                <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Fails telemetry refund check, blocking refund bot triggers.</div>
              </div>
              <input 
                type="checkbox" 
                checked={simulateTelemetryFraud} 
                onChange={(e) => setSimulateTelemetryFraud(e.target.checked)} 
                style={{ accentColor: 'var(--color-admin)', scale: '1.2', cursor: 'pointer' }}
              />
            </div>

            {/* DB latency switch */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', padding: '0.5rem', borderRadius: '6px' }}>
              <div>
                <span style={{ fontSize: '0.8rem', fontWeight: 700 }}>Inject Database Latency Spike</span>
                <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Simulates BFF database latency. Hystrix circuit breaker caches results.</div>
              </div>
              <input 
                type="checkbox" 
                checked={dbLatencyActive} 
                onChange={(e) => setDbLatencyActive(e.target.checked)} 
                style={{ accentColor: 'var(--color-admin)', scale: '1.2', cursor: 'pointer' }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Verification queues */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        {/* Onboarding queue */}
        <div className="glass-card" style={{ padding: '1rem' }}>
          <h4 style={{ fontWeight: 800, marginBottom: '0.5rem' }}>Onboarding Verification Desk (3-Level Checks)</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '0.75rem' }}>
            {onboardingQueue.filter(app => !app.approvals.l1 || !app.approvals.l2 || !app.approvals.l3).map(app => (
              <div key={app.id} style={{ background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', padding: '0.5rem', borderRadius: '6px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', fontWeight: 700 }}>
                  <span>{app.name}</span>
                  <span style={{ color: 'var(--color-admin)' }}>{app.type.toUpperCase()}</span>
                </div>
                <div style={{ display: 'flex', gap: '0.25rem', marginTop: '0.5rem' }}>
                  <button 
                    className={`btn-secondary-glow`} 
                    style={{ flex: 1, fontSize: '0.65rem', padding: '0.2rem', background: app.approvals.l1 ? 'rgba(16, 185, 129, 0.1)' : 'transparent', color: app.approvals.l1 ? 'var(--color-customer)' : 'var(--text-primary)' }}
                    onClick={() => handleApproveOnboard(app.id, 'l1')}
                    disabled={app.approvals.l1}
                  >
                    L1 ID
                  </button>
                  <button 
                    className={`btn-secondary-glow`} 
                    style={{ flex: 1, fontSize: '0.65rem', padding: '0.2rem', background: app.approvals.l2 ? 'rgba(16, 185, 129, 0.1)' : 'transparent', color: app.approvals.l2 ? 'var(--color-customer)' : 'var(--text-primary)' }}
                    onClick={() => handleApproveOnboard(app.id, 'l2')}
                    disabled={app.approvals.l2}
                  >
                    L2 Vehicle
                  </button>
                  <button 
                    className={`btn-secondary-glow`} 
                    style={{ flex: 1, fontSize: '0.65rem', padding: '0.2rem', background: app.approvals.l3 ? 'rgba(16, 185, 129, 0.1)' : 'transparent', color: app.approvals.l3 ? 'var(--color-customer)' : 'var(--text-primary)' }}
                    onClick={() => handleApproveOnboard(app.id, 'l3')}
                    disabled={app.approvals.l3}
                  >
                    L3 Background
                  </button>
                </div>
              </div>
            ))}
            {onboardingQueue.every(app => app.approvals.l1 && app.approvals.l2 && app.approvals.l3) && (
              <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textAlign: 'center' }}>No pending applications</p>
            )}
          </div>
        </div>

        {/* HITL Queue */}
        <div className="glass-card" style={{ padding: '1rem' }}>
          <h4 style={{ fontWeight: 800, marginBottom: '0.5rem' }}>Human-in-the-Loop (HITL) Queue</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.75rem' }}>
            {hitlQueue.length === 0 ? (
              <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textAlign: 'center' }}>No pending approvals</p>
            ) : (
              hitlQueue.map(ticket => (
                <div key={ticket.id} style={{ background: 'rgba(255,255,255,0.01)', border: '1px solid var(--border-color)', padding: '0.5rem', borderRadius: '6px', fontSize: '0.75rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                    <span>{ticket.type.toUpperCase()}</span>
                    <span>${ticket.amount.toFixed(2)}</span>
                  </div>
                  <p style={{ color: 'var(--text-muted)', margin: '0.2rem 0' }}>{ticket.desc}</p>
                  <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.4rem' }}>
                    <button className="btn-primary-glow" style={{ flex: 1, padding: '0.25rem', border: 'none', background: 'var(--color-customer)', color: '#ffffff', cursor: 'pointer' }} onClick={() => handleReleaseHitl(ticket)}>
                      Approve Release
                    </button>
                    <button className="btn-secondary-glow" style={{ flex: 1, padding: '0.25rem', color: 'var(--color-admin)', borderColor: 'var(--color-admin)', cursor: 'pointer' }} onClick={() => handleVoidHitl(ticket)}>
                      Void Ticket
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

    </div>
  );
}
