import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { handlesErrors } from './problem-interceptor';
import {
  EmployeeDetail, EmployeeStatus, EmployeeSummary, PageResponse, RecordSalaryChangeRequest, SalaryChangeResponse,
  SalaryHistoryItem, UpdateEmployeeRequest,
} from './models';

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);
  private readonly changed = new Subject<EmployeeDetail>();

  /** Emits after any successful write, so lists showing the employee can refresh. */
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

  history(id: number): Observable<SalaryHistoryItem[]> {
    return this.http.get<SalaryHistoryItem[]>(`/api/employees/${id}/salary-history`);
  }

  // Writes: errors (400 field errors, 409 conflicts) are shown by the form itself, not the global snackbar.

  update(id: number, body: UpdateEmployeeRequest): Observable<EmployeeDetail> {
    return this.http.put<EmployeeDetail>(`/api/employees/${id}`, body, { context: handlesErrors() });
  }

  changeStatus(id: number, status: EmployeeStatus, version: number): Observable<EmployeeDetail> {
    return this.http.patch<EmployeeDetail>(`/api/employees/${id}/status`, { status, version }, { context: handlesErrors() });
  }

  recordSalaryChange(id: number, body: RecordSalaryChangeRequest): Observable<SalaryChangeResponse> {
    return this.http.post<SalaryChangeResponse>(`/api/employees/${id}/salary-changes`, body, { context: handlesErrors() });
  }

  notifyChanged(employee: EmployeeDetail): void {
    this.changed.next(employee);
  }
}
