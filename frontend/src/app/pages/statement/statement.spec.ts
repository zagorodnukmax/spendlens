import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../../core/api.service';
import { Insights, Statement } from '../../core/models';
import { StatementPage } from './statement';

const statement = (status: Statement['status'], aiCategorized = 0): Statement => ({
  id: 7,
  fileName: 'november.csv',
  format: 'MONOBANK',
  status,
  error: null,
  transactionCount: 2,
  aiCategorized,
  periodFrom: '2026-11-01',
  periodTo: '2026-11-02',
  uploadedAt: '2026-11-23T10:00:00Z',
  processedAt: null,
});

const insights: Insights = {
  income: 1000,
  expenses: 135,
  balance: 865,
  transactionCount: 2,
  byCategory: [{ category: 'RESTAURANTS', label: 'Cafe & Restaurants', color: '#ea580c', total: 135, count: 2, share: 1 }],
  byMonth: [{ month: '2026-11', income: 1000, expenses: 135 }],
  topMerchants: [],
  subscriptions: [],
  anomalies: [],
};

describe('StatementPage', () => {
  let fixture: ComponentFixture<StatementPage>;
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let statuses: Statement[];

  async function create(aiEnabled: boolean) {
    api = {
      statement: vi.fn(() => of(statuses.shift() ?? statement('DONE'))),
      insights: vi.fn(() => of(insights)),
      transactions: vi.fn(() => of([])),
      categories: vi.fn(() => of([])),
      aiStatus: vi.fn(() => of({ enabled: aiEnabled, model: aiEnabled ? 'claude-opus-5-5' : null })),
      ask: vi.fn(),
      recategorize: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [StatementPage],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(StatementPage);
    fixture.componentRef.setInput('id', '7');
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('polls while processing and loads insights when done', async () => {
    statuses = [statement('PROCESSING'), statement('PROCESSING'), statement('DONE', 2)];
    await create(true);

    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    expect(text()).toContain('Analyzing your statement');
    expect(api['insights']).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(2000);
    fixture.detectChanges();

    expect(api['statement']).toHaveBeenCalledTimes(3);
    expect(api['insights']).toHaveBeenCalledOnce();
    expect(text()).toContain('2 of 2 categorized by Claude');
    expect(text()).toContain('Cafe & Restaurants');

    await vi.advanceTimersByTimeAsync(5000);
    expect(api['statement']).toHaveBeenCalledTimes(3); // polling stopped
  });

  it('shows how to enable AI when Claude is not configured', async () => {
    statuses = [statement('DONE')];
    await create(false);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(text()).toContain('ANTHROPIC_API_KEY');
    expect(text()).toContain('Categorized by rules');
  });

  it('appends the answer to the conversation, or the error', async () => {
    statuses = [statement('DONE', 2)];
    await create(true);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);

    api['ask'].mockReturnValueOnce(of({ question: 'q', answer: 'You spent 135 UAH.', model: 'claude-opus-5-5' }));
    api['ask'].mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 503, error: { status: 503, detail: 'AI is busy' } })),
    );
    const page = fixture.componentInstance as unknown as { ask(q: string): void };
    page.ask('How much on coffee?');
    page.ask('And on taxis?');
    fixture.detectChanges();

    expect(api['ask']).toHaveBeenCalledWith(7, 'How much on coffee?');
    expect(text()).toContain('You spent 135 UAH.');
    expect(text()).toContain('AI is busy');
  });
});
