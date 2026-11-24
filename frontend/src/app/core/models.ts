export interface User {
  id: number;
  email: string;
  displayName: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: User;
}

export type StatementStatus = 'PROCESSING' | 'DONE' | 'FAILED';
export type CategorySource = 'AI' | 'RULES' | 'MANUAL';

export interface Statement {
  id: number;
  fileName: string;
  format: 'MONOBANK' | 'GENERIC';
  status: StatementStatus;
  error: string | null;
  transactionCount: number;
  aiCategorized: number;
  periodFrom: string | null;
  periodTo: string | null;
  uploadedAt: string;
  processedAt: string | null;
}

export interface CategoryInfo {
  value: string;
  label: string;
  color: string;
}

export interface StatementTransaction {
  id: number;
  dateTime: string;
  description: string;
  mcc: number | null;
  amount: number;
  category: string | null;
  categoryLabel: string | null;
  categoryColor: string | null;
  categorySource: CategorySource | null;
}

export interface CategoryTotal {
  category: string;
  label: string;
  color: string;
  total: number;
  count: number;
  share: number;
}

export interface Insights {
  income: number;
  expenses: number;
  balance: number;
  transactionCount: number;
  byCategory: CategoryTotal[];
  byMonth: { month: string; income: number; expenses: number }[];
  topMerchants: { merchant: string; total: number; count: number }[];
  subscriptions: { merchant: string; typicalAmount: number; charges: number; lastCharge: string; yearlyCost: number }[];
  anomalies: { transactionId: number; date: string; description: string; amount: number; category: string; reason: string }[];
}

export interface AskResponse {
  question: string;
  answer: string;
  model: string;
}

export interface AiStatus {
  enabled: boolean;
  model: string | null;
}

/** Spring's ProblemDetail error body. */
export interface ApiProblem {
  status: number;
  detail?: string;
  errors?: Record<string, string>;
}

export type AuditStatus = 'RUNNING' | 'DONE' | 'FAILED';
export type FindingType = 'MISCATEGORIZED' | 'DUPLICATE' | 'SUBSCRIPTION' | 'UNUSUAL_EXPENSE' | 'SAVING_TIP';

export interface AuditFinding {
  type: FindingType;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  title: string;
  details: string;
  transactionIds: number[];
  suggestedCategory: string | null;
}

export interface AuditRun {
  id: number;
  status: AuditStatus;
  startedAt: string;
  finishedAt: string | null;
  error: string | null;
  steps: { tool: string; input: string; outcome: string; at: string }[];
  report: { summary: string; findings: AuditFinding[]; complete: boolean } | null;
  inputTokens: number;
  outputTokens: number;
}
