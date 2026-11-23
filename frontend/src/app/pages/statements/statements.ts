import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/errors';
import { AiStatus, Statement } from '../../core/models';

@Component({
  selector: 'app-statements',
  imports: [RouterLink, DatePipe],
  templateUrl: './statements.html',
  styleUrl: './statements.css',
})
export class Statements {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly statements = signal<Statement[]>([]);
  protected readonly ai = signal<AiStatus | null>(null);
  protected readonly uploading = signal(false);
  protected readonly dragOver = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.load();
    this.api.aiStatus().subscribe((s) => this.ai.set(s));
  }

  protected onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.upload(file);
    input.value = '';
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.upload(file);
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  protected remove(s: Statement, event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    if (!confirm(`Delete "${s.fileName}"?`)) return;
    this.api.deleteStatement(s.id).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  private upload(file: File): void {
    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.error.set('Please choose a .csv file');
      return;
    }
    this.uploading.set(true);
    this.error.set(null);
    this.api.upload(file).subscribe({
      next: (s) => this.router.navigate(['/statements', s.id]),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.uploading.set(false);
      },
    });
  }

  private load(): void {
    this.api.statements().subscribe({
      next: (list) => this.statements.set(list),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
