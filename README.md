# SpendLens 🔎

**AI-powered bank statement analyzer.** Upload a CSV export from monobank (or any bank) and SpendLens:

- **categorizes every transaction with Claude** (Anthropic API, structured output) — with an automatic
  fallback to MCC-code + keyword rules when AI is off or a request fails;
- finds **recurring subscriptions** and what they cost per year;
- flags **unusual expenses** (e.g. "8.1× your typical Groceries expense");
- shows spending **by category and by month**;
- answers questions in plain language: *"How much did I spend on taxis in October?"*,
  *"Which subscriptions could I cancel?"* — in English or Ukrainian.

| Layer    | Tech |
|----------|------|
| Backend  | Java 25, Spring Boot 4.2, Spring Security 7 (JWT **Bearer**), Spring Data JPA, `@Async` processing |
| AI       | Official [Anthropic Java SDK](https://github.com/anthropics/anthropic-java), model `claude-opus-5-5`, structured outputs |
| Database | H2 (default, zero setup) or PostgreSQL (`postgres` profile + `docker-compose.yml`) |
| Frontend | Angular 22 — standalone components, signals, zoneless, lazy routes |
| Quality  | 46 backend + 27 frontend tests, GitHub Actions CI |

> Follow-up to [Spendwise](https://github.com/zagorodnukmax/spendwise), where categorization was keyword-only.
> Here the same problem is solved with an LLM, and the keyword approach became the fallback.

## How it works

```
CSV ─▶ StatementParser ─▶ save (status PROCESSING) ─▶ 201 Created
                                   │ after commit (@Async)
                                   ▼
                      CategorizationService
                        ├─ batches of 40 ─▶ Claude (structured JSON: index → category enum)
                        └─ failed batch / skipped row ─▶ RuleBasedCategorizer (MCC + keywords)
                                   ▼
                        status DONE ─▶ UI stops polling ─▶ InsightsService (exact, no AI)
                                                         └▶ "Ask" ─▶ Claude with statement context
```

Design decisions:

- **AI only where it adds value.** Categorizing messy merchant names and answering free-form questions use
  Claude. Totals, subscriptions and anomalies are plain deterministic code, because numbers must be exact.
- **Always a complete result.** Each transaction stores who categorized it (`AI`, `RULES`, `MANUAL`), and the
  user can correct any category.
- **Closed category list** (enum) → the model output is schema-validated and statistics stay comparable.
- **Testable AI boundary.** All LLM calls go through the `ClaudeGateway` interface. Tests use a fake, plus one
  test that runs the real SDK client against a local HTTP stub to check the exact request we send.

More detail (in Russian): [LOGIC.md](LOGIC.md).

## Run it

**Prerequisites:** JDK 25, Node.js 22.22+ / 24+. Docker is optional.

### Backend
```bash
cd backend
./mvnw spring-boot:run
```
or in IntelliJ IDEA: open `backend`, run `SpendlensApplication`.

**Enable Claude** (optional, but that's the fun part). Get a key at
[console.anthropic.com](https://console.anthropic.com), then:
```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd backend && ./mvnw spring-boot:run
```
In IntelliJ: *Run → Edit Configurations → Environment variables* → `ANTHROPIC_API_KEY=...`.
Without a key everything works except "Ask", and categorization uses the rules.

### Frontend
```bash
cd frontend
npm install
npm start
```
Open http://localhost:4200 and log in as **`demo@spendlens.dev` / `demo12345`** (a sample monobank statement is
preloaded). Sample files to upload are in [`samples/`](samples).

### PostgreSQL instead of H2
```bash
docker compose up -d
cd backend && SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

### Tests
```bash
cd backend && ./mvnw test                   # 46 tests
cd frontend && npx ng test --watch=false    # 27 tests
```

## Supported CSV formats

- **monobank export** — `Дата i час операції, Деталі операції, MCC, Сума в валюті картки (UAH), …`
- **generic** — `date, description, amount[, mcc]`; comma or semicolon separator; `2026-11-05` or `05.11.2026`;
  negative amount = expense.

## API

All endpoints except `/api/auth/**` require `Authorization: Bearer <token>`.

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Get a JWT |
| POST | `/api/statements` (multipart `file`) | Upload, returns `PROCESSING` |
| GET | `/api/statements`, `/api/statements/{id}` | List / status |
| GET | `/api/statements/{id}/transactions` | Categorized transactions |
| PATCH | `/api/statements/{id}/transactions/{txId}` | Correct a category |
| GET | `/api/statements/{id}/insights` | Totals, categories, months, subscriptions, anomalies |
| POST | `/api/statements/{id}/ask` | `{ "question": "..." }` → answer from Claude (503 if AI is off) |
| DELETE | `/api/statements/{id}` | Delete |
| GET | `/api/ai/status`, `/api/categories` | AI on/off, category list |

## Privacy note

When Claude is enabled, transaction descriptions and amounts are sent to the Anthropic API. Don't upload
statements you're not comfortable sharing with an external service; use rules mode instead.
