export type CaseStatus = 'OPEN' | 'ESCALATED' | 'APPROVED' | 'REJECTED';
export type CaseAction = 'APPROVE' | 'REJECT' | 'ESCALATE';

export interface RuleHit { ruleId: string; points: number; reason: string }

export interface CaseSummary {
  caseId: string; applicationId: string; applicantName: string;
  maskedPan: string; maskedPhone: string; score: number;
  decision: string; status: CaseStatus; createdAt: string;
}

export interface CaseDetail extends CaseSummary {
  deviceId: string; requestedAmount: number; updatedAt: string; ruleHits: RuleHit[];
}

export interface AuditEntry { fromStatus: CaseStatus; toStatus: CaseStatus; actor: string; comment: string; at: string }

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const fieldErrors = body?.fieldErrors?.map((e: { field: string; message: string }) => `${e.field} ${e.message}`);
    throw new Error(fieldErrors?.length ? fieldErrors.join('; ') : body?.message ?? `HTTP ${response.status}`);
  }
  return body as T;
}

export const api = {
  listCases: (status: CaseStatus | '') =>
    request<CaseSummary[]>(`/api/cases${status ? `?status=${status}` : ''}`),
  getCase: (caseId: string) => request<CaseDetail>(`/api/cases/${caseId}`),
  getAudit: (caseId: string) => request<AuditEntry[]>(`/api/cases/${caseId}/audit`),
  act: (caseId: string, action: CaseAction, comment: string, user: string) =>
    request<CaseDetail>(`/api/cases/${caseId}/actions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User': user },
      body: JSON.stringify({ action, comment }),
    }),
};
