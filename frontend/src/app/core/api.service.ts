import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AiStatus,
  AskResponse,
  CategoryInfo,
  Insights,
  Statement,
  StatementTransaction,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  statements(): Observable<Statement[]> {
    return this.http.get<Statement[]>('/api/statements');
  }
  statement(id: number): Observable<Statement> {
    return this.http.get<Statement>(`/api/statements/${id}`);
  }
  upload(file: File): Observable<Statement> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Statement>('/api/statements', form);
  }
  deleteStatement(id: number): Observable<void> {
    return this.http.delete<void>(`/api/statements/${id}`);
  }
  transactions(id: number): Observable<StatementTransaction[]> {
    return this.http.get<StatementTransaction[]>(`/api/statements/${id}/transactions`);
  }
  recategorize(id: number, txId: number, category: string): Observable<StatementTransaction> {
    return this.http.patch<StatementTransaction>(`/api/statements/${id}/transactions/${txId}`, { category });
  }
  insights(id: number): Observable<Insights> {
    return this.http.get<Insights>(`/api/statements/${id}/insights`);
  }
  ask(id: number, question: string): Observable<AskResponse> {
    return this.http.post<AskResponse>(`/api/statements/${id}/ask`, { question });
  }
  aiStatus(): Observable<AiStatus> {
    return this.http.get<AiStatus>('/api/ai/status');
  }
  categories(): Observable<CategoryInfo[]> {
    return this.http.get<CategoryInfo[]>('/api/categories');
  }
}
