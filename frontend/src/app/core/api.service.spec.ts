import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uploads the file as multipart form data', () => {
    const file = new File(['date,description,amount'], 'november.csv', { type: 'text/csv' });
    api.upload(file).subscribe();

    const req = http.expectOne('/api/statements');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush({});
  });

  it('sends the question to the ask endpoint', () => {
    api.ask(7, 'How much on coffee?').subscribe();
    const req = http.expectOne('/api/statements/7/ask');
    expect(req.request.body).toEqual({ question: 'How much on coffee?' });
    req.flush({ question: 'q', answer: 'a', model: 'claude-opus-5-5' });
  });

  it('recategorizes a transaction with PATCH', () => {
    api.recategorize(7, 42, 'TRAVEL').subscribe();
    const req = http.expectOne('/api/statements/7/transactions/42');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ category: 'TRAVEL' });
    req.flush({});
  });
});
