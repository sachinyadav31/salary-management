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
