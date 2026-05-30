import React, { useState } from 'react';
import * as Lucide from 'lucide-react';
import { useAiStream } from '../hooks/useAiStream';

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
  const [aiPanelOpen, setAiPanelOpen] = useState(false);
  const { streamData, isStreaming, error, startStream } = useAiStream();

  // Calculate total stock in stores
  const totalStockCentral = products.reduce((sum, p) => sum + p.stock, 0);
  const totalStockEast = products.reduce((sum, p) => sum + p.stockEast, 0);

  const centralFillPct = Math.min(100, (totalStockCentral / centralCapacity) * 100);
  const eastFillPct = Math.min(100, (totalStockEast / eastCapacity) * 100);

  // AI Diagnostic Auditor Trigger
  const handleTriggerAudit = () => {
    const activeLedgerCount = ledger.length;
    const latestLedgerEntry = ledger.length > 0 ? ledger[ledger.length - 1] : null;
    const ledgerDesc = latestLedgerEntry ? latestLedgerEntry.desc : 'No transactions recorded yet.';

    const prompt = `Analyze the current Swiss Q-Commerce operational telemetry and provide a brief executive audit report:
- Central MFC Capacity: ${totalStockCentral}/${centralCapacity} units (${Math.round(centralFillPct)}% full).
- East MFC Capacity: ${totalStockEast}/${eastCapacity} units (${Math.round(eastFillPct)}% full).
- Merchant Wallet Balance: $${merchantWallet.toFixed(2)}.
- System Trust Vectors: Wholesaler ${wholesalerTrustScore}/100, Customer ${customerTrustScore}/100, Picker Accuracy ${pickerTrustScore}/100, Rider Score ${riderTrustScore}/100.
- Latest OLAP Ledger Transaction: "${ledgerDesc}".

Provide a highly concise, professional business health assessment, flag any structural/trust bottlenecks, and list 2 key recommendations. Use bullet points. Keep it professional.`;

    startStream('/api/ai/local', prompt);
  };
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

      {/* AI Telemetry & Operations Auditor Widget */}
      <div className="glass-card" style={{ 
        padding: '1.25rem', 
        borderLeft: '4px solid var(--color-business)',
        background: 'rgba(255,255,255,0.01)',
        borderRadius: '12px',
        transition: 'all 0.3s ease',
        marginBottom: '1.25rem'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }} onClick={() => setAiPanelOpen(!aiPanelOpen)}>
          <h3 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 800, color: 'var(--color-business)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Lucide.Sparkles size={16} className="animate-pulse" style={{ color: 'var(--color-business)' }} />
            🔮 Swiss AI Operational Telemetry & Financial Auditor
          </h3>
          <button className="btn-secondary-glow" style={{ padding: '0.2rem 0.5rem', fontSize: '0.7rem', border: 'none', cursor: 'pointer' }}>
            {aiPanelOpen ? 'Hide Auditor' : 'Show Auditor'}
          </button>
        </div>

        {aiPanelOpen && (
          <div style={{ marginTop: '1rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', margin: 0 }}>
              The Local LLM (Qwen 2.5) executes on-premises, scanning merchant wallets, warehouse capacities, and ledger anomalies to generate live corporate recommendations.
            </p>
            
            <div>
              <button 
                className="btn-primary-glow"
                style={{ 
                  background: 'var(--color-business)', 
                  color: 'white', 
                  border: 'none', 
                  padding: '0.5rem 1rem', 
                  fontSize: '0.8rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.4rem'
                }}
                onClick={handleTriggerAudit}
                disabled={isStreaming}
              >
                <Lucide.ShieldCheck size={14} />
                {isStreaming ? 'Analyzing Operations...' : 'Run On-Premises Operational Audit'}
              </button>
            </div>

            {error && (
              <div style={{ color: 'var(--color-admin)', fontSize: '0.75rem', marginTop: '0.25rem' }}>
                ⚠️ Local Audit Failure: {error}
              </div>
            )}

            {streamData && (
              <div style={{ 
                background: 'rgba(0,0,0,0.25)', 
                padding: '1rem', 
                borderRadius: '8px', 
                border: '1px solid rgba(255,255,255,0.05)',
                fontSize: '0.8rem',
                lineHeight: '1.45',
                color: 'var(--text-primary)',
                maxHeight: '250px',
                overflowY: 'auto',
                whiteSpace: 'pre-wrap',
                position: 'relative',
                fontFamily: 'var(--font-mono)'
              }}>
                {streamData}
                {isStreaming && <span className="animate-ping" style={{ color: 'var(--color-business)', fontWeight: 'bold', marginLeft: '2px' }}>▋</span>}
              </div>
            )}
          </div>
        )}
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
