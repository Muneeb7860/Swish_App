import React from 'react';
import * as Lucide from 'lucide-react';

export default function BusinessApp({
  products,
  merchantWallet,
  ledger,
  trustLogs,
  customerTrustScore,
  riderTrustScore,
  pickerTrustScore,
  wholesalerTrustScore,
  centralCapacity,
  eastCapacity,
  centralScalingCount,
  eastScalingCount,
  handleScaleCapacity,
  downloadRegulatoryReport
}) {
  // Calculate total stock in stores
  const totalStockCentral = products.reduce((sum, p) => sum + p.stock, 0);
  const totalStockEast = products.reduce((sum, p) => sum + p.stockEast, 0);

  const centralFillPct = Math.min(100, (totalStockCentral / centralCapacity) * 100);
  const eastFillPct = Math.min(100, (totalStockEast / eastCapacity) * 100);

  // Surcharges dynamically mapped from scaling count
  const getScalingFee = (count) => {
    if (count === 0) return 15.00;
    if (count === 1) return 25.00;
    if (count === 2) return 35.00;
    return null;
  };

  const centralFee = getScalingFee(centralScalingCount);
  const eastFee = getScalingFee(eastScalingCount);

  return (
    <div className="business-dashboard" style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      
      {/* Wallet and summary bar */}
      <div className="glass-card" style={{ padding: '1rem', borderLeft: '3px solid var(--color-business)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ fontWeight: 800 }}>Business Web Console</h3>
          <div style={{ display: 'flex', gap: '1.5rem', alignItems: 'center' }}>
            <span>Merchant Wallet: <strong style={{ color: 'var(--color-business)' }}>${merchantWallet.toFixed(2)}</strong></span>
            <button className="btn-secondary-glow" style={{ fontSize: '0.75rem', cursor: 'pointer' }} onClick={downloadRegulatoryReport}>
              Download Regulatory Audit Report (CSV)
            </button>
          </div>
        </div>
      </div>

      {/* Trust scores overview */}
      <div className="product-shelf-grid" style={{ gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem' }}>
        <div className="glass-card" style={{ padding: '0.75rem', textAlign: 'center' }}>
          <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>CUSTOMER TRUST</span>
          <h3 style={{ color: 'var(--color-customer)', fontWeight: 800 }}>{customerTrustScore}/100</h3>
        </div>
        <div className="glass-card" style={{ padding: '0.75rem', textAlign: 'center' }}>
          <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>RIDER TRUST</span>
          <h3 style={{ color: 'var(--color-rider)', fontWeight: 800 }}>{riderTrustScore}/100</h3>
        </div>
        <div className="glass-card" style={{ padding: '0.75rem', textAlign: 'center' }}>
          <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>PICKER ACCURACY</span>
          <h3 style={{ color: 'var(--color-inventory)', fontWeight: 800 }}>{pickerTrustScore}/100</h3>
        </div>
        <div className="glass-card" style={{ padding: '0.75rem', textAlign: 'center' }}>
          <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>WHOLESALER TRUST</span>
          <h3 style={{ color: 'var(--color-business)', fontWeight: 800 }}>{wholesalerTrustScore}/100</h3>
        </div>
      </div>

      {/* Store capacity progress trackers */}
      <div style={{ display: 'flex', gap: '1.25rem' }}>
        {/* Central Store capacity */}
        <div className="glass-card" style={{ flex: 1, padding: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontWeight: 800 }}>Central MFC Capacity</span>
            {centralFillPct > 90 && <span className="warning-flag" style={{ animation: 'pulse 1s infinite' }}>⚠️ STORAGE BOTTLENECK</span>}
          </div>
          <div style={{ fontSize: '1.25rem', fontWeight: 800, margin: '0.5rem 0' }}>
            {totalStockCentral} / {centralCapacity} Units ({Math.round(centralFillPct)}% Full)
          </div>
          <div style={{ background: '#020408', height: '8px', borderRadius: '99px', overflow: 'hidden', marginBottom: '0.75rem' }}>
            <div style={{ background: centralFillPct > 90 ? 'var(--color-admin)' : 'var(--color-business)', height: '100%', width: `${centralFillPct}%` }} />
          </div>
          {centralFee !== null ? (
            <button 
              id="btn-rent-central"
              className="btn-secondary-glow" 
              style={{ width: '100%', fontSize: '0.7rem', padding: '0.3rem', cursor: 'pointer' }}
              onClick={() => handleScaleCapacity('Central')}
            >
              Rent Central Overflow Storage Bay (Surcharge: ${centralFee.toFixed(2)})
            </button>
          ) : (
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Central Storage Capacity Max Scaled (+120 bay limit)</span>
          )}
        </div>

        {/* East Store capacity */}
        <div className="glass-card" style={{ flex: 1, padding: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontWeight: 800 }}>East MFC Capacity</span>
            {eastFillPct > 90 && <span className="warning-flag" style={{ animation: 'pulse 1s infinite' }}>⚠️ STORAGE BOTTLENECK</span>}
          </div>
          <div style={{ fontSize: '1.25rem', fontWeight: 800, margin: '0.5rem 0' }}>
            {totalStockEast} / {eastCapacity} Units ({Math.round(eastFillPct)}% Full)
          </div>
          <div style={{ background: '#020408', height: '8px', borderRadius: '99px', overflow: 'hidden', marginBottom: '0.75rem' }}>
            <div style={{ background: eastFillPct > 90 ? 'var(--color-admin)' : 'var(--color-business)', height: '100%', width: `${eastFillPct}%` }} />
          </div>
          {eastFee !== null ? (
            <button 
              id="btn-rent-east"
              className="btn-secondary-glow" 
              style={{ width: '100%', fontSize: '0.7rem', padding: '0.3rem', cursor: 'pointer' }}
              onClick={() => handleScaleCapacity('East')}
            >
              Rent East Overflow Storage Bay (Surcharge: ${eastFee.toFixed(2)})
            </button>
          ) : (
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>East Storage Capacity Max Scaled (+120 bay limit)</span>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', gap: '1.25rem' }}>
        {/* double entry ledger table */}
        <div className="glass-card" style={{ flex: 1.5, padding: '1rem', overflowX: 'auto' }}>
          <h4 style={{ fontWeight: 800, marginBottom: '0.5rem' }}>OLAP Financial Ledger</h4>
          <table className="ledger-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.75rem', textAlign: 'left' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-color)', color: 'var(--text-secondary)' }}>
                <th style={{ padding: '0.4rem' }}>Time</th>
                <th style={{ padding: '0.4rem' }}>Type</th>
                <th style={{ padding: '0.4rem' }}>Ref Code</th>
                <th style={{ padding: '0.4rem' }}>Description</th>
                <th style={{ padding: '0.4rem' }}>Debit</th>
                <th style={{ padding: '0.4rem' }}>Credit</th>
              </tr>
            </thead>
            <tbody>
              {ledger.slice().reverse().map(l => (
                <tr key={l.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.02)' }}>
                  <td style={{ padding: '0.4rem', fontFamily: 'var(--font-mono)' }}>{l.time}</td>
                  <td style={{ padding: '0.4rem' }}>{l.type}</td>
                  <td style={{ padding: '0.4rem', fontFamily: 'var(--font-mono)' }}>{l.ref}</td>
                  <td style={{ padding: '0.4rem', color: 'var(--text-muted)' }}>{l.desc}</td>
                  <td style={{ padding: '0.4rem', color: 'var(--color-admin)' }}>{l.debit > 0 ? `$${l.debit.toFixed(2)}` : ''}</td>
                  <td style={{ padding: '0.4rem', color: 'var(--color-customer)' }}>{l.credit > 0 ? `$${l.credit.toFixed(2)}` : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* security logs */}
        <div className="glass-card" style={{ flex: 1, padding: '1rem' }}>
          <h4 style={{ fontWeight: 800, marginBottom: '0.5rem' }}>🛡️ Security Trust & Fraud Log</h4>
          <div className="kafka-log-list" style={{ height: '240px', overflowY: 'auto' }}>
            {trustLogs.slice().reverse().map(log => (
              <div key={log.id} style={{ fontSize: '0.7rem', padding: '0.35rem 0', borderBottom: '1px solid rgba(255,255,255,0.02)' }}>
                <span style={{ color: 'var(--text-muted)' }}>[{log.time}]</span>{' '}
                <span className={`kafka-log-event event-${log.actor}`}>{log.actor.toUpperCase()}</span>:{' '}
                <span style={{ color: 'var(--text-primary)' }}>{log.event}</span>{' '}
                <span style={{ color: log.delta >= 0 ? 'var(--color-customer)' : 'var(--color-admin)' }}>
                  ({log.delta >= 0 ? '+' : ''}{log.delta})
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

    </div>
  );
}
