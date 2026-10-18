import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { Meta, PayBandSummary } from './models';

/** Reference data changes only with deployments, so it is fetched once per session. */
@Injectable({ providedIn: 'root' })
export class MetaApi {
  private readonly http = inject(HttpClient);

  readonly meta$: Observable<Meta> = this.http.get<Meta>('/api/meta').pipe(shareReplay(1));

  /** Pay bands change only with deployments too; used to show where a starting salary lands. */
  readonly payBands$: Observable<PayBandSummary[]> = this.http.get<PayBandSummary[]>('/api/pay-bands').pipe(shareReplay(1));
}
