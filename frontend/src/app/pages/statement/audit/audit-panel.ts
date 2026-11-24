import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, switchMap, takeWhile, timer } from 'rxjs';
import { ApiService } from '../../../core/api.service';
import { errorMessage } from '../../../core/errors';
import { AuditFinding, AuditRun, CategoryInfo, StatementTransaction } from '../../../core/models';

/**
 * Runs the AI audit agent for a statement, shows its tool calls live, then the findings.
 * Suggested category fixes are applied only when the user clicks (human in the loop).
 */
@Component({
  selector: 'app-audit-panel',
  imports: [CurrencyPipe],
  templateUrl: './audit-panel.html',
  styleUrl: './audit-panel.css',
})
export class AuditPanel implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly statementId = input.required<number>();
  readonly aiEnabled = input.required<boolean>();
  readonly transactions = input.required<StatementTransaction[]>();
  readonly categories = input.required<CategoryInfo[]>();
  /** Emitted when the user accepts a suggested category. */
  readonly applyCategory = output<{ transaction: StatementTransaction; category: string }>();

  protected readonly run = signal<AuditRun | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly running = computed(() => this.run()?.status === 'RUNNING');

  protected readonly toolIcons: Record<string, string> = {
    get_overview: '📊',
    search_transactions: '🔍',
    find_possible_duplicates: '👯',
    get_merchant_history: '🏪',
    list_recurring_charges: '🔁',
    submit_report: '📝',
  };

  protected readonly typeLabels: Record<string, string> = {
    MISCATEGORIZED: 'Wrong category',
    DUPLICATE: 'Possible double charge',
    SUBSCRIPTION: 'Subscription',
    UNUSUAL_EXPENSE: 'Unusual expense',
    SAVING_TIP: 'Saving tip',
  };

  private polling?: Subscription;

  ngOnInit(): void {
    this.api.latestAudit(this.statementId()).subscribe({
      next: (run) => {
        this.run.set(run);
        if (run.status === 'RUNNING') this.poll();
      },
      error: (err) => {
        if (!(err instanceof HttpErrorResponse && err.status === 404)) this.error.set(errorMessage(err));
      },
    });
  }

  protected start(): void {
    this.error.set(null);
    this.api.startAudit(this.statementId()).subscribe({
      next: (run) => {
        this.run.set(run);
        this.poll();
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected transactionsOf(f: AuditFinding): StatementTransaction[] {
    const byId = new Map(this.transactions().map((t) => [t.id, t]));
    return f.transactionIds.map((id) => byId.get(id)).filter((t): t is StatementTransaction => !!t);
  }

  protected categoryLabel(value: string | null): string {
    return this.categories().find((c) => c.value === value)?.label ?? value ?? '';
  }

  protected needsFix(f: AuditFinding, t: StatementTransaction): boolean {
    return !!f.suggestedCategory && t.category !== f.suggestedCategory;
  }

  private poll(): void {
    this.polling?.unsubscribe();
    this.polling = timer(1000, 1000)
      .pipe(
        switchMap(() => this.api.latestAudit(this.statementId())),
        takeWhile((r) => r.status === 'RUNNING', true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (r) => this.run.set(r),
        error: (err) => this.error.set(errorMessage(err)),
      });
  }
}
