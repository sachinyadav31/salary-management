import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { CountryInsight, Distribution, Envelope, InsightsSummary, OutlierCard } from './models';

@Injectable({ providedIn: 'root' })
export class InsightsApi {
  private readonly http = inject(HttpClient);

  summary(): Observable<InsightsSummary> {
    return this.http.get<InsightsSummary>('/api/insights/summary');
  }

  byCountry(): Observable<CountryInsight[]> {
    return this.http.get<Envelope<CountryInsight[]>>('/api/insights/by-country').pipe(map((e) => e.data));
  }

  /** Local currency for one country, USD org-wide. */
  distribution(country: string | null, department: number | null = null): Observable<Distribution> {
    let params = new HttpParams();
    if (country) params = params.set('country', country);
    if (department) params = params.set('department', department);
    return this.http.get<Distribution>('/api/insights/distribution', { params });
  }

  outliers(limit = 6): Observable<OutlierCard[]> {
    return this.http
      .get<Envelope<OutlierCard[]>>('/api/insights/outliers', { params: { limit } })
      .pipe(map((e) => e.data));
  }
}
