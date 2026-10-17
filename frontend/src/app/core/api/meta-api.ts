import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { Meta } from './models';

/** Reference data changes only with deployments, so it is fetched once per session. */
@Injectable({ providedIn: 'root' })
export class MetaApi {
  private readonly http = inject(HttpClient);

  readonly meta$: Observable<Meta> = this.http.get<Meta>('/api/meta').pipe(shareReplay(1));
}
