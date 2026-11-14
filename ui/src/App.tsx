import { useCallback, useEffect, useState } from 'react';
import { api, AuditEntry, CaseAction, CaseDetail, CaseStatus, CaseSummary } from './api';

const STATUSES: (CaseStatus | '')[] = ['OPEN', 'ESCALATED', 'APPROVED', 'REJECTED', ''];
const ACTIONS: CaseAction[] = ['APPROVE', 'REJECT', 'ESCALATE'];

export default function App() {
  const [status, setStatus] = useState<CaseStatus | ''>('OPEN');
  const [cases, setCases] = useState<CaseSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadQueue = useCallback(() => {
    api.listCases(status).then(setCases).catch((e: Error) => setError(e.message));
  }, [status]);

  useEffect(loadQueue, [loadQueue]);

  return (
    <main className="layout">
      <section className="queue">
        <h1>Review queue</h1>
        <label>
          Status{' '}
          <select value={status} onChange={(e) => setStatus(e.target.value as CaseStatus | '')}>
            {STATUSES.map((s) => <option key={s} value={s}>{s || 'ALL'}</option>)}
          </select>
        </label>
        {error && <p role="alert" className="error">{error}</p>}
        <table>
          <thead>
            <tr><th>Score</th><th>Applicant</th><th>PAN</th><th>Phone</th><th>Decision</th><th>Status</th></tr>
          </thead>
          <tbody>
            {cases.map((c) => (
              <tr key={c.caseId} onClick={() => setSelected(c.caseId)}
                  className={c.caseId === selected ? 'selected' : undefined}>
                <td>{c.score}</td><td>{c.applicantName}</td><td>{c.maskedPan}</td>
                <td>{c.maskedPhone}</td><td>{c.decision}</td><td>{c.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
      {selected && <CasePanel caseId={selected} onChanged={loadQueue} />}
    </main>
  );
}

function CasePanel({ caseId, onChanged }: { caseId: string; onChanged: () => void }) {
  const [detail, setDetail] = useState<CaseDetail | null>(null);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [comment, setComment] = useState('');
  const [user, setUser] = useState('underwriter');
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    api.getCase(caseId).then(setDetail).catch((e: Error) => setError(e.message));
    api.getAudit(caseId).then(setAudit).catch((e: Error) => setError(e.message));
  }, [caseId]);

  useEffect(() => {
    setError(null);
    refresh();
  }, [refresh]);

  const act = async (action: CaseAction) => {
    setError(null);
    try {
      await api.act(caseId, action, comment, user);
      setComment('');
      refresh();
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  if (!detail) return <section className="detail">Loading…</section>;
  return (
    <section className="detail">
      <h2>{detail.applicantName} · score {detail.score}</h2>
      <p>PAN {detail.maskedPan} · Phone {detail.maskedPhone} · Device {detail.deviceId}</p>
      <p>Requested {detail.requestedAmount} · {detail.decision} · <strong>{detail.status}</strong></p>
      <h3>Rule hits</h3>
      <ul>
        {detail.ruleHits.map((h) => <li key={h.ruleId}><b>{h.ruleId}</b> (+{h.points}) {h.reason}</li>)}
      </ul>
      <h3>Action</h3>
      <input aria-label="Acting user" value={user} onChange={(e) => setUser(e.target.value)} />
      <textarea aria-label="Comment" placeholder="Comment (at least 10 characters)" value={comment}
                onChange={(e) => setComment(e.target.value)} />
      <div>{ACTIONS.map((a) => <button key={a} onClick={() => act(a)}>{a}</button>)}</div>
      {error && <p role="alert" className="error">{error}</p>}
      <h3>Audit history</h3>
      <ol>
        {audit.map((a) => (
          <li key={a.at}>{a.at}: {a.fromStatus} → {a.toStatus} by {a.actor}: “{a.comment}”</li>
        ))}
      </ol>
    </section>
  );
}
