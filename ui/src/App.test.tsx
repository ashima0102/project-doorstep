import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

const cases = [
  { caseId: 'c1', applicationId: 'APP-1', applicantName: 'Asha Verma', maskedPan: '******234F',
    maskedPhone: '******3210', score: 95, decision: 'REJECT', status: 'OPEN', createdAt: '2026-11-14T06:00:00Z' },
  { caseId: 'c2', applicationId: 'APP-2', applicantName: 'Ravi Iyer', maskedPan: '******999Z',
    maskedPhone: '******1111', score: 35, decision: 'REVIEW', status: 'OPEN', createdAt: '2026-11-14T06:01:00Z' },
];

afterEach(() => vi.restoreAllMocks());

describe('review queue', () => {
  it('renders cases from the API showing only masked identifiers', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(cases), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    render(<App />);

    expect(await screen.findByText('Asha Verma')).toBeInTheDocument();
    expect(screen.getByText('******234F')).toBeInTheDocument();
    expect(screen.getByText('******3210')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/cases?status=OPEN', undefined);

    const text = document.body.textContent ?? '';
    expect(text).not.toMatch(/[A-Z]{5}[0-9]{4}[A-Z]/); // no full PAN
    expect(text).not.toMatch(/\b[0-9]{10}\b/);          // no full phone
    const rows = screen.getAllByRole('row');
    expect(rows[1]).toHaveTextContent('95');             // highest score first, as returned by the API
  });
});
