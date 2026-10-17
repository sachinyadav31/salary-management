import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EmployeeSummary, PageResponse } from './models';

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);

  search(params: HttpParams): Observable<PageResponse<EmployeeSummary>> {
    return this.http.get<PageResponse<EmployeeSummary>>('/api/employees', { params });
  }

  /** Same filters as {@link search}; the browser downloads the streamed CSV directly. */
  exportUrl(params: HttpParams): string {
    const query = params.toString();
    return '/api/employees/export.csv' + (query ? `?${query}` : '');
  }
}
