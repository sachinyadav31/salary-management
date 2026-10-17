// TypeScript mirrors of the backend DTOs. Keep in sync with the OpenAPI spec (/v3/api-docs).

/**
 * Exact money from the API. `minorUnits` is safe as a JS number (salaries are far below 2^53);
 * `amount` is the exact decimal string for display.
 */
export interface Money {
  minorUnits: number;
  currency: string;
  amount: string;
}

export type JobLevel = 'L1' | 'L2' | 'L3' | 'L4' | 'L5' | 'L6';
export type EmployeeStatus = 'ACTIVE' | 'INACTIVE';
export type BandPosition = 'BELOW' | 'WITHIN' | 'ABOVE';

export interface PayBand {
  min: Money;
  mid: Money;
  max: Money;
}

export interface EmployeeSummary {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  countryCode: string;
  countryName: string;
  departmentId: number;
  department: string;
  jobTitle: string;
  level: JobLevel;
  status: EmployeeStatus;
  hireDate: string;
  salary: Money;
  salaryUsd: Money;
  payBand: PayBand | null;
  compaRatio: number | null;
  bandPosition: BandPosition | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Meta {
  countries: { code: string; name: string; currency: string }[];
  departments: { id: number; name: string }[];
  jobTitles: string[];
  levels: JobLevel[];
  statuses: EmployeeStatus[];
  reportingCurrency: string;
  fxRatesAsOf: string;
}

/** RFC 7807 problem details, as returned by the API for every error. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

// --- Insights -----------------------------------------------------------------------------------

export interface PayStats {
  min: Money;
  max: Money;
  average: Money;
  median: Money;
}

export interface BandSplit {
  below: number;
  within: number;
  above: number;
  withinPercent: number | null;
  averageCompaRatio: number | null;
}

export interface InsightsSummary {
  asOf: string;
  fxRatesAsOf: string;
  reportingCurrency: string;
  headcount: number;
  countries: number;
  totalPayroll: Money;
  averageSalary: Money | null;
  medianSalary: Money | null;
  bands: BandSplit;
}

export interface CountryInsight {
  countryCode: string;
  countryName: string;
  currency: string;
  headcount: number;
  payroll: Money;
  payrollUsd: Money;
  local: PayStats;
  usd: PayStats;
  bands: BandSplit;
}

export interface DistributionBucket {
  from: Money;
  to: Money;
  count: number;
}

export interface Distribution {
  asOf: string;
  currency: string;
  headcount: number;
  buckets: DistributionBucket[];
  stats: PayStats | null;
}

export type OutlierType = 'UNDERPAID_GROUP' | 'OVERPAID_GROUP' | 'WIDE_SPREAD' | 'DEPARTMENT_LAG';

export interface OutlierCard {
  id: string;
  type: OutlierType;
  severity: number;
  headline: string;
  detail: string;
  affected: number;
  groupSize: number;
  /** Directory query params that list exactly the people behind the card. */
  filter: Record<string, string>;
}

export interface Envelope<T> {
  asOf: string;
  fxRatesAsOf: string;
  data: T;
}

// --- Employee detail and writes ------------------------------------------------------------------

export interface EmployeeDetail extends EmployeeSummary {
  version: number;
  createdAt: string;
  updatedAt: string;
}

export type SalaryChangeReason = 'HIRE' | 'PROMOTION' | 'ANNUAL_REVIEW' | 'MARKET_ADJUSTMENT' | 'CORRECTION';
export type HistoryStatus = 'PAST' | 'CURRENT' | 'SCHEDULED';

export interface SalaryHistoryItem {
  id: number;
  amount: Money;
  effectiveDate: string;
  reason: SalaryChangeReason;
  note: string | null;
  status: HistoryStatus;
  changePercent: number | null;
}

export interface UpdateEmployeeRequest {
  firstName: string;
  lastName: string;
  email: string;
  departmentId: number;
  jobTitle: string;
  level: JobLevel;
  version: number;
}

export interface RecordSalaryChangeRequest {
  /** Decimal string in the employee's currency, e.g. "95000.00" (never a float). */
  amount: string;
  effectiveDate: string;
  reason: Exclude<SalaryChangeReason, 'HIRE'>;
  note: string | null;
  version: number;
}

export interface SalaryChangeResponse {
  employee: EmployeeDetail;
  history: SalaryHistoryItem[];
}
