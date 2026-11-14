import { useCallback, useEffect, useState } from 'react';
import { api, AuditEntry, CaseAction, CaseDetail, CaseStatus, CaseSummary } from './api';

const STATUSES: (CaseStatus | '')[] = ['OPEN', 'ESCALATED', 'APPROVED', 'REJECTED', ''];
const ACTIONS: CaseAction[] = ['APPROVE', 'REJECT', 'ESCALATE'];
const MIN_COMMENT = 10;

const isOverride = (c: { decision: string; status: CaseStatus }) => c.decision === 'REJECT' && c.status === 'APPROVED';
const riskLevel = (score: number) => (score >= 60 ? 'high' : score >= 30 ? 'medium' : 'low');

export default function App() {
  const [status, setStatus] = useState<CaseStatus | ''>('OPEN');
  const [cases, setCases] = useState<CaseSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadQueue = useCallback(() => {
    api.listCases(status).then(setCases).catch((e: Error) => setError(e.message));
  }, [status]);

  useEffect(loadQueue, [loadQueue]);

  const highRisk = cases.filter((c) => c.score >= 60).length;
  const open = cases.filter((c) => c.status === 'OPEN' || c.status === 'ESCALATED').length;

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="logo" aria-hidden>◆</span>
          <div>
            <div className="brand-name">Doorstep</div>
            <div className="brand-sub">Underwriter fraud review</div>
          </div>
        </div>
        <div className="stats">
          <Stat label="Cases in view" value={cases.length} />
          <Stat label="High risk (≥60)" value={highRisk} tone="high" />
          <Stat label="Awaiting underwriter" value={open} tone="medium" />
        </div>
      </header>

      <main className="layout">
        <section className="card queue">
          <div className="card-head">
            <h1>Review queue</h1>
            <div className="filters" role="group" aria-label="Status filter">
              {STATUSES.map((s) => (
                <button key={s || 'ALL'} className={`chip ${s === status ? 'active' : ''}`}
                        onClick={() => { setStatus(s); setSelected(null); }}>
                  {s || 'ALL'}
                </button>
              ))}
            </div>
          </div>
          <p className="hint">Highest risk first. Click a row to open the case.</p>
          {error && <p role="alert" className="error">{error}</p>}
          <table>
            <thead>
              <tr><th>Score</th><th>Applicant</th><th>PAN</th><th>Phone</th><th>System verdict</th><th>Underwriter outcome</th></tr>
            </thead>
            <tbody>
              {cases.map((c) => (
                <tr key={c.caseId} onClick={() => setSelected(c.caseId)}
                    className={`${c.caseId === selected ? 'selected' : ''} risk-${riskLevel(c.score)}`}>
                  <td><span className={`score ${riskLevel(c.score)}`}>{c.score}</span></td>
                  <td className="name">{c.applicantName}</td>
                  <td className="mono">{c.maskedPan}</td>
                  <td className="mono">{c.maskedPhone}</td>
                  <td><span className={`pill decision-${c.decision}`}>{c.decision}</span></td>
                  <td><span className={`pill status-${c.status}`}>{c.status}</span>{isOverride(c) && <span className="pill override">OVERRIDDEN</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {cases.length === 0 && !error && <p className="empty">No cases with this status.</p>}
        </section>

        {selected
          ? <CasePanel caseId={selected} onChanged={loadQueue} />
          : (
            <aside className="card detail placeholder">
              <div className="placeholder-icon" aria-hidden>☜</div>
              <h2>Select a case</h2>
              <p>Click any row in the queue to see why it was flagged, act on it, and view its audit trail.</p>
            </aside>
          )}
      </main>
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className={`stat ${tone ?? ''}`}>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}

function CasePanel({ caseId, onChanged }: { caseId: string; onChanged: () => void }) {
  const [detail, setDetail] = useState<CaseDetail | null>(null);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [comment, setComment] = useState('');
  const [user, setUser] = useState('underwriter');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const refresh = useCallback(() => {
    api.getCase(caseId).then(setDetail).catch((e: Error) => setError(e.message));
    api.getAudit(caseId).then(setAudit).catch((e: Error) => setError(e.message));
  }, [caseId]);

  useEffect(() => {
    setError(null);
    setNotice(null);
    refresh();
  }, [refresh]);

  const act = async (action: CaseAction) => {
    setError(null);
    setNotice(null);
    try {
      const updated = await api.act(caseId, action, comment, user);
      setComment('');
      setNotice(`Case moved to ${updated.status}`);
      refresh();
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  if (!detail) return <aside className="card detail">Loading…</aside>;
  const level = riskLevel(detail.score);
  const final = detail.status === 'APPROVED' || detail.status === 'REJECTED';
  return (
    <aside className="card detail">
      <div className={`detail-head ${level}`}>
        <div>
          <h2>{detail.applicantName}</h2>
          <div className="sub">{detail.applicationId}</div>
        </div>
        <div className={`score big ${level}`}>{detail.score}</div>
      </div>

      <dl className="facts">
        <div><dt>PAN</dt><dd className="mono">{detail.maskedPan}</dd></div>
        <div><dt>Phone</dt><dd className="mono">{detail.maskedPhone}</dd></div>
        <div><dt>Device</dt><dd className="mono">{detail.deviceId}</dd></div>
        <div><dt>Requested</dt><dd>₹{Number(detail.requestedAmount).toLocaleString('en-IN')}</dd></div>
        <div><dt>System verdict</dt><dd><span className={`pill decision-${detail.decision}`}>{detail.decision}</span></dd></div>
        <div><dt>Underwriter outcome</dt><dd><span className={`pill status-${detail.status}`}>{detail.status}</span></dd></div>
      </dl>

      <h3>Why it was flagged</h3>
      <ul className="hits">
        {detail.ruleHits.map((h) => (
          <li key={h.ruleId}>
            <div className="hit-top"><span className="rule">{h.ruleId}</span><span className="points">+{h.points}</span></div>
            <div className="reason">{h.reason}</div>
          </li>
        ))}
      </ul>

      <h3>Take action</h3>
      {detail.decision === 'REJECT' && !final && (
        <p className="warn">⚠ The system verdict is REJECT. Approving overrides it, and your name and comment are written to the audit trail.</p>
      )}
      {final && <p className="muted">This case is {detail.status} and final. The server will refuse further actions.</p>}
      <label className="field">Acting underwriter
        <input aria-label="Acting user" value={user} onChange={(e) => setUser(e.target.value)} />
      </label>
      <label className="field">Comment
        <textarea aria-label="Comment" placeholder={`Why? (at least ${MIN_COMMENT} characters)`} value={comment}
                  onChange={(e) => setComment(e.target.value)} />
        <span className={`counter ${comment.trim().length >= MIN_COMMENT ? 'ok' : ''}`}>
          {comment.trim().length}/{MIN_COMMENT}
        </span>
      </label>
      <div className="actions">
        {ACTIONS.map((a) => <button key={a} className={`btn btn-${a}`} onClick={() => act(a)}>{a}</button>)}
      </div>
      {error && <p role="alert" className="error">⚠ {error}</p>}
      {notice && <p role="status" className="success">✓ {notice}</p>}

      <h3>Audit trail</h3>
      {audit.length === 0
        ? <p className="muted">No actions yet.</p>
        : (
          <ol className="timeline">
            {audit.map((a) => (
              <li key={a.at}>
                <div className="when">{new Date(a.at).toLocaleString()}</div>
                <div><span className={`pill status-${a.fromStatus}`}>{a.fromStatus}</span> → <span className={`pill status-${a.toStatus}`}>{a.toStatus}</span> by <b>{a.actor}</b></div>
                <div className="reason">“{a.comment}”</div>
              </li>
            ))}
          </ol>
        )}
    </aside>
  );
}
