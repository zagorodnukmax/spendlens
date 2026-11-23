import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, switchMap, takeWhile, timer } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/errors';
import {
  AiStatus,
  CategoryInfo,
  Insights,
  Statement,
  StatementTransaction,
} from '../../core/models';

interface QA {
  question: string;
  answer: string | null;
  error: string | null;
}

@Component({
  selector: 'app-statement',
  imports: [RouterLink, FormsModule, CurrencyPipe, DatePipe, PercentPipe],
  templateUrl: './statement.html',
  styleUrl: './statement.css',
})
export class StatementPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  /** Route parameter, bound by withComponentInputBinding(). */
  readonly id = input.required<string>();

  protected readonly statement = signal<Statement | null>(null);
  protected readonly insights = signal<Insights | null>(null);
  protected readonly transactions = signal<StatementTransaction[]>([]);
  protected readonly categories = signal<CategoryInfo[]>([]);
  protected readonly ai = signal<AiStatus | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly filter = signal<string>('ALL');
  protected readonly question = signal('');
  protected readonly asking = signal(false);
  protected readonly conversation = signal<QA[]>([]);

  protected readonly suggestions = [
    'How much did I spend on food and restaurants?',
    'Which subscriptions could I cancel to save money?',
    'What was my biggest expense and why does it stand out?',
    'Порівняй мої витрати по місяцях',
  ];

  protected readonly visibleTransactions = computed(() => {
    const f = this.filter();
    return f === 'ALL' ? this.transactions() : this.transactions().filter((t) => t.category === f);
  });

  protected readonly maxCategoryTotal = computed(() =>
    Math.max(1, ...(this.insights()?.byCategory.map((c) => c.total) ?? [])),
  );

  ngOnInit(): void {
    const id = Number(this.id());
    this.api.categories().subscribe((c) => this.categories.set(c));
    this.api.aiStatus().subscribe((s) => this.ai.set(s));

    // Poll while the backend categorizes the statement in the background.
    timer(0, 1000)
      .pipe(
        switchMap(() => this.api.statement(id)),
        takeWhile((s) => s.status === 'PROCESSING', true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (s) => {
          this.statement.set(s);
          if (s.status === 'DONE') this.loadResults(id);
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  protected ask(text?: string): void {
    const q = (text ?? this.question()).trim();
    if (!q || this.asking()) return;
    const id = Number(this.id());
    this.asking.set(true);
    this.question.set('');
    this.conversation.update((c) => [...c, { question: q, answer: null, error: null }]);
    this.api.ask(id, q).subscribe({
      next: (res) => this.finishAnswer({ question: q, answer: res.answer, error: null }),
      error: (err) => this.finishAnswer({ question: q, answer: null, error: errorMessage(err) }),
    });
  }

  protected recategorize(t: StatementTransaction, category: string): void {
    const id = Number(this.id());
    this.api.recategorize(id, t.id, category).subscribe({
      next: (updated) => {
        this.transactions.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.api.insights(id).subscribe((i) => this.insights.set(i));
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected barWidth(total: number): string {
    return `${(total / this.maxCategoryTotal()) * 100}%`;
  }

  protected sourceLabel(t: StatementTransaction): string {
    switch (t.categorySource) {
      case 'AI':
        return '✨ AI';
      case 'MANUAL':
        return '✎ you';
      default:
        return '⚙ rules';
    }
  }

  private finishAnswer(qa: QA): void {
    this.conversation.update((c) => [...c.slice(0, -1), qa]);
    this.asking.set(false);
  }

  private loadResults(id: number): void {
    forkJoin({ insights: this.api.insights(id), transactions: this.api.transactions(id) }).subscribe({
      next: ({ insights, transactions }) => {
        this.insights.set(insights);
        this.transactions.set(transactions);
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
