import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { handlesErrors } from './problem-interceptor';
import {
  AwardBonusRequest, Bonus, CreateEmployeeRequest, BulkChangeRequest, BulkPreview, BulkResult, CareerEvent, EmployeeDetail, EmployeeStatus, EmployeeSummary, PageResponse, RecordSalaryChangeRequest, SalaryChangeResponse,
  SalaryHistoryItem, UpdateEmployeeRequest,
} from './models';

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);
  private readonly changed = new Subject<EmployeeDetail | null>();

  /** Emits after any successful write (null after a bulk change), so lists showing employees can refresh. */
  readonly changed$ = this.changed.asObservable();

  search(params: HttpParams): Observable<PageResponse<EmployeeSummary>> {
    return this.http.get<PageResponse<EmployeeSummary>>('/api/employees', { params });
  }

  /** Same filters as {@link search}; the browser downloads the streamed CSV directly. */
  exportUrl(params: HttpParams): string {
    const query = params.toString();
    return '/api/employees/export.csv' + (query ? `?${query}` : '');
  }

  get(id: number): Observable<EmployeeDetail> {
    return this.http.get<EmployeeDetail>(`/api/employees/${id}`);
  }

  bonuses(id: number): Observable<Bonus[]> {
    return this.http.get<Bonus[]>(`/api/employees/${id}/bonuses`);
  }

  /** Returns the updated list of bonuses. */
  awardBonus(id: number, body: AwardBonusRequest): Observable<Bonus[]> {
    return this.http.post<Bonus[]>(`/api/employees/${id}/bonuses`, body, { context: handlesErrors() });
  }

  careerEvents(id: number): Observable<CareerEvent[]> {
    return this.http.get<CareerEvent[]>(`/api/employees/${id}/career-events`);
  }

  history(id: number): Observable<SalaryHistoryItem[]> {
    return this.http.get<SalaryHistoryItem[]>(`/api/employees/${id}/salary-history`);
  }

  // Writes: errors (400 field errors, 409 conflicts) are shown by the form itself, not the global snackbar.

  /** Hires someone; returns them with their generated employee code. */
  create(body: CreateEmployeeRequest): Observable<EmployeeDetail> {
    return this.http.post<EmployeeDetail>('/api/employees', body, { context: handlesErrors() });
  }

  update(id: number, body: UpdateEmployeeRequest): Observable<EmployeeDetail> {
    return this.http.put<EmployeeDetail>(`/api/employees/${id}`, body, { context: handlesErrors() });
  }

  changeStatus(id: number, status: EmployeeStatus, version: number): Observable<EmployeeDetail> {
    return this.http.patch<EmployeeDetail>(`/api/employees/${id}/status`, { status, version }, { context: handlesErrors() });
  }

  recordSalaryChange(id: number, body: RecordSalaryChangeRequest): Observable<SalaryChangeResponse> {
    return this.http.post<SalaryChangeResponse>(`/api/employees/${id}/salary-changes`, body, { context: handlesErrors() });
  }

  /** Same filters as {@link search}: previews a raise or bonus for everyone matching them. Saves nothing. */
  bulkPreview(params: HttpParams, body: BulkChangeRequest): Observable<BulkPreview> {
    return this.http.post<BulkPreview>('/api/bulk-changes/preview', body, { params, context: handlesErrors() });
  }

  /** Applies in one transaction; the same requestId never applies twice. */
  bulkApply(params: HttpParams, body: BulkChangeRequest & { requestId: string }): Observable<BulkResult> {
    return this.http.post<BulkResult>('/api/bulk-changes', body, { params, context: handlesErrors() });
  }

  notifyChanged(employee: EmployeeDetail | null): void {
    this.changed.next(employee);
  }
}
