import React from 'react';
import * as Lucide from 'lucide-react';

export default function SystemEngineRoom({
  rateLimitActive,
  dbLatencyActive,
  redisCrashActive,
  paymentOutageActive,
  riderTrafficActive,
  circuitBreakerTripped,
  activeProfile,
  oltpWriteLatency,
  olapSyncTimer,
  jwtFlash,
  vaultTimer,
  latencyHistory,
  cacheHits,
  cacheMisses,
  kafkaLogs,
  agentMetrics = { dailyCost: 0.0, hourlyRequestCount: 0, dailyBudgetLimit: 5.0, hourlyRequestLimit: 100 }
}) {

  const currentBffLatency = latencyHistory.length > 0 ? latencyHistory[latencyHistory.length - 1] : 4;
  const totalCacheQueries = cacheHits + cacheMisses;
  const cacheHitRate = totalCacheQueries > 0 ? Math.round((cacheHits / totalCacheQueries) * 100) : 100;

  return (
    <div className="system-engine-sidebar">
      
      <div className="engine-header">
        <div className="engine-title">
          <Lucide.Cpu size={18} style={{ animation: 'spin 4s linear infinite' }} />
          <span>System Engine Room</span>
        </div>
        <span style={{ fontSize: '0.7rem', color: 'var(--color-engine)', fontWeight: 'bold', fontFamily: 'var(--font-mono)' }}>
          LIVE METRICS
        </span>
      </div>

      {rateLimitActive && (
        <div className="rate-limit-banner">
          <Lucide.ShieldAlert size={16} />
          <div>
            <strong>BLOCKED:</strong> Rate Limiter activated. Delaying requests.
          </div>
        </div>
      )}

      {(dbLatencyActive || redisCrashActive || paymentOutageActive || riderTrafficActive || circuitBreakerTripped) && (
        <div className="chaos-banner">
          <Lucide.AlertOctagon size={16} />
          <div>
            <strong>WARNING:</strong> Active injected faults detected. Resilience protocols active.
          </div>
        </div>
      )}

      <div className="db-split-container">
        <div className="db-panel" style={{ borderLeft: '3px solid var(--color-customer)' }}>
          <div className="db-title-row">
            <span>OLTP DB (Writes)</span>
            <Lucide.Database size={10} className="event-customer" />
          </div>
          <span className="db-latency" style={{ color: oltpWriteLatency > 100 ? 'var(--color-admin)' : 'var(--color-customer)' }}>
            {oltpWriteLatency} ms
          </span>
          <span className="db-stats">Concurrency: High</span>
        </div>

        <div className="db-panel" style={{ borderLeft: '3px solid var(--color-business)' }}>
          <div className="db-title-row">
            <span>OLAP Sync (ETL)</span>
            <Lucide.Layers size={10} className="event-business" />
          </div>
          <span className="db-latency" style={{ color: 'var(--color-business)' }}>
            {olapSyncTimer} s
          </span>
          <div className="etl-sync-progress">
            <div className="etl-sync-fill" style={{ width: `${(olapSyncTimer / 8) * 100}%` }} />
          </div>
        </div>
      </div>

      <div className="glass-card" style={{ padding: '0.65rem 0.75rem', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', fontWeight: 700 }}>
          <span style={{ color: 'var(--text-secondary)' }}>Security Guardrails Node</span>
          <span style={{ color: jwtFlash ? 'var(--color-customer)' : 'var(--text-muted)' }}>
            {jwtFlash ? '● JWT VALIDATED' : '● JWT STANDBY'}
          </span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: '0.15rem' }}>
          <span>Secrets rotation cycle:</span>
          <span style={{ fontFamily: 'var(--font-mono)' }}>{vaultTimer}s (HashiCorp Vault)</span>
        </div>
      </div>

      <div className="metric-grid">
        <div className="metric-card">
          <span className="metric-label">BFF Latency</span>
          <span className="metric-value" style={{ color: currentBffLatency > 200 ? 'var(--color-admin)' : 'var(--color-customer)' }}>
            {currentBffLatency} ms
          </span>
          <div className="metric-bar">
            <div 
              className="metric-fill" 
              style={{ 
                width: `${Math.min(100, (currentBffLatency / 300) * 100)}%`, 
                background: currentBffLatency > 200 ? 'var(--color-admin)' : 'var(--color-customer)' 
              }} 
            />
          </div>
        </div>

        <div className="metric-card">
          <span className="metric-label">Redis Cache Hit Rate</span>
          <span className="metric-value" style={{ color: 'var(--color-engine)' }}>
            {cacheHitRate}%
          </span>
          <div className="metric-bar">
            <div 
              className="metric-fill" 
              style={{ 
                width: `${cacheHitRate}%`, 
                background: 'var(--color-engine)' 
              }} 
            />
          </div>
        </div>
      </div>

      <div className="glass-card" style={{ padding: '0.65rem 0.75rem', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', fontWeight: 700 }}>
          <span style={{ color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
            <Lucide.Bot size={13} className="event-system" />
            Agent Operations Control
          </span>
          <span style={{ color: 'var(--color-business)', fontSize: '0.65rem', fontWeight: 'bold' }}>
            ● GEMINI FLASH FREE TIER
          </span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: '0.15rem' }}>
          <span>Daily Cost Budget:</span>
          <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-primary)' }}>
            ${agentMetrics.dailyCost?.toFixed(4)} / ${agentMetrics.dailyBudgetLimit?.toFixed(2)}
          </span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.65rem', color: 'var(--text-muted)' }}>
          <span>Hourly Rate Limit:</span>
          <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-primary)' }}>
            {agentMetrics.hourlyRequestCount} / {agentMetrics.hourlyRequestLimit} reqs
          </span>
        </div>
        <div className="etl-sync-progress" style={{ height: '4px', marginTop: '0.2rem' }}>
          <div 
            className="etl-sync-fill" 
            style={{ 
              width: `${Math.min(100, (agentMetrics.dailyCost / (agentMetrics.dailyBudgetLimit || 5.0)) * 100)}%`,
              background: agentMetrics.dailyCost >= 5.0 ? 'var(--color-admin)' : 'var(--color-business)'
            }} 
          />
        </div>
      </div>

      <div className="glass-card" style={{ padding: '0.75rem 1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>

          <span>BFF API Response Latency History</span>
          <span style={{ fontFamily: 'var(--font-mono)' }}>Max: 300ms</span>
        </div>
        
        <svg viewBox="0 0 300 80" style={{ width: '100%', height: '50px', background: '#020408', borderRadius: '6px', border: '1px solid var(--border-color)' }}>
          {latencyHistory.length > 1 && (
            <polyline
              fill="none"
              stroke="var(--color-engine)"
              strokeWidth="2"
              points={latencyHistory.map((val, idx) => {
                const x = (idx / (latencyHistory.length - 1)) * 280 + 10;
                const y = 70 - (Math.min(val, 300) / 300) * 60;
                return `${x},${y}`;
              }).join(' ')}
            />
          )}
        </svg>
      </div>

      <div className="kafka-console">
        <div className="kafka-console-header">
          <span className="kafka-console-title">Kafka Event Broker Console</span>
          <div style={{ display: 'flex', gap: '0.35rem' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#ef4444' }}></span>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#f59e0b' }}></span>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#10b981' }}></span>
          </div>
        </div>

        <div className="kafka-log-list">
          {kafkaLogs.slice().reverse().map(log => (
            <div key={log.id} className="kafka-log-item">
              <span className="kafka-log-time">[{log.time}]</span>
              <span className={`kafka-log-event event-${log.source}`}>{log.event}</span>
              <span className="kafka-log-meta">↳ {log.meta}</span>
            </div>
          ))}
        </div>
      </div>

    </div>
  );
}
