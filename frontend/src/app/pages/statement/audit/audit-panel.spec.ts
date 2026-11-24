import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../../core/api.service';
import { AuditRun, StatementTransaction } from '../../../core/models';
import { AuditPanel } from './audit-panel';

const tx: StatementTransaction = {
  id: 4,
  dateTime: '2026-11-05T12:00:00',
  description: 'Uklon trip',
  mcc: null,
  amount: -180,
  category: 'OTHER',
  categoryLabel: 'Other',
  categoryColor: '#94a3b8',
  categorySource: 'RULES',
};

const running: AuditRun = {
  id: 1,
  status: 'RUNNING',
  startedAt: '2026-11-24T10:00:00Z',
  finishedAt: null,
  error: null,
  steps: [{ tool: 'get_overview', input: '', outcome: 'overview', at: '' }],
  report: null,
  inputTokens: 0,
  outputTokens: 0,
};

const done: AuditRun = {
  ...running,
  status: 'DONE',
  steps: [...running.steps, { tool: 'search_transactions', input: '{"category":"OTHER"}', outcome: '1 matches', at: '' }],
  report: {
    summary: 'One taxi ride is uncategorized.',
    complete: true,
    findings: [
      {
        type: 'MISCATEGORIZED',
        severity: 'MEDIUM',
        title: 'Taxi in Other',
        details: 'Uklon is a taxi service.',
        transactionIds: [4],
        suggestedCategory: 'TRANSPORT',
      },
    ],
  },
  inputTokens: 4200,
  outputTokens: 300,
};

describe('AuditPanel', () => {
  let fixture: ComponentFixture<AuditPanel>;
  let api: Record<string, ReturnType<typeof vi.fn>>;

  async function create(aiEnabled: boolean, latest: () => unknown) {
    api = { latestAudit: vi.fn(latest), startAudit: vi.fn(() => of(running)) };
    await TestBed.configureTestingModule({
      imports: [AuditPanel],
      providers: [{ provide: ApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(AuditPanel);
    fixture.componentRef.setInput('statementId', 7);
    fixture.componentRef.setInput('aiEnabled', aiEnabled);
    fixture.componentRef.setInput('transactions', [tx]);
    fixture.componentRef.setInput('categories', [
      { value: 'OTHER', label: 'Other', color: '#999' },
      { value: 'TRANSPORT', label: 'Transport', color: '#00f' },
    ]);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const notFound = () => throwError(() => new HttpErrorResponse({ status: 404 }));

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('explains how to enable AI and hides the button when Claude is off', async () => {
    await create(false, notFound);
    expect(el().textContent).toContain('ANTHROPIC_API_KEY');
    expect(el().querySelector('button')).toBeNull();
  });

  it('shows live steps while running, then the findings', async () => {
    const sequence = [running, done];
    await create(true, notFound);
    api['latestAudit'].mockImplementation(() => of(sequence.shift() ?? done));

    (el().querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api['startAudit']).toHaveBeenCalledWith(7);
    expect(el().textContent).toContain('get_overview');
    expect(el().textContent).toContain('thinking');

    await vi.advanceTimersByTimeAsync(2000);
    fixture.detectChanges();

    expect(el().textContent).toContain('search_transactions');
    expect(el().textContent).toContain('One taxi ride is uncategorized.');
    expect(el().textContent).toContain('Wrong category');
    expect(el().textContent).toContain('Uklon trip');
    expect(el().textContent).not.toContain('thinking');
  });

  it('applies a suggested category only when the user clicks', async () => {
    await create(true, () => of(done));
    const emitted: unknown[] = [];
    fixture.componentInstance.applyCategory.subscribe((e) => emitted.push(e));

    const apply = [...el().querySelectorAll('button')].find((b) => b.textContent?.includes('Apply')) as HTMLButtonElement;
    expect(apply.textContent).toContain('Apply: Transport');
    expect(emitted).toHaveLength(0);

    apply.click();
    expect(emitted).toEqual([{ transaction: tx, category: 'TRANSPORT' }]);
  });
});
