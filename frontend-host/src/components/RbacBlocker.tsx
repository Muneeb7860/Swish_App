import React, { useEffect } from 'react';
import * as Lucide from 'lucide-react';

export default function RbacBlocker({ targetRole, currentUserSession, handleLogout, logKafka, triggerToast }) {
  useEffect(() => {
    if (logKafka) {
      logKafka('system', 'security.rbac_violation', `UNAUTHORIZED: Role [${currentUserSession?.role?.toUpperCase()}] blocked from accessing [${targetRole.toUpperCase()}] dashboard.`);
    }
    if (triggerToast) {
      triggerToast(`403 FORBIDDEN: Access denied to ${targetRole} environment`, 'admin');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [targetRole]);

  const handleEscalate = () => {
    if (logKafka) {
      logKafka('system', 'security.privilege_escalation_attempt', `Session role ${currentUserSession?.role} requested privilege elevation for ${targetRole}`);
    }
    if (triggerToast) {
      triggerToast('Elevation request forwarded to Security Ops.', 'system');
    }
  };

  return (
    <div className="rbac-blocker-container">
      <div className="rbac-warning-icon">
        <Lucide.ShieldAlert size={32} />
      </div>
      <h2 style={{ color: 'var(--color-admin)', fontWeight: 800, fontSize: '1.5rem', marginBottom: '0.5rem', fontFamily: 'var(--font-display)' }}>403 Forbidden - Access Denied</h2>
      <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '1.5rem', maxWidth: '400px', fontFamily: 'var(--font-sans)' }}>
        Your authenticated session with role <strong style={{ color: 'var(--color-engine)' }}>{currentUserSession?.role?.toUpperCase()}</strong> is unauthorized to access the <strong style={{ color: 'var(--color-admin)' }}>{targetRole.toUpperCase()}</strong> environment.
      </p>
      <div className="crud-btn-group" style={{ flexDirection: 'column', gap: '0.5rem', width: '100%', maxWidth: '280px' }}>
        <button aria-label="Button" 
          className="btn-primary-glow" 
          style={{ background: 'var(--color-admin)', color: '#ffffff', width: '100%', border: 'none', padding: '0.5rem', cursor: 'pointer', fontFamily: 'var(--font-sans)' }} 
          onClick={handleEscalate}
        >
          Request Access Elevation
        </button>
        <button aria-label="Button" 
          className="btn-secondary-glow" 
          style={{ width: '100%', padding: '0.5rem', cursor: 'pointer', fontFamily: 'var(--font-sans)' }} 
          onClick={handleLogout}
        >
          Log In as Different Role
        </button>
      </div>
    </div>
  );
}
