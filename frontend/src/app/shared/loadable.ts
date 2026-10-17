import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';

export interface Loadable<T> {
  loading: boolean;
  error: boolean;
  data: T | null;
}

export const LOADING: Loadable<never> = { loading: true, error: false, data: null };

/**
 * Runs `source` on every emission of `trigger` (first load, retry, changed inputs), cancelling the previous
 * request, and exposes loading / error / data so each card can render its own state.
 */
export function loadable<K, T>(trigger: Observable<K>, source: (key: K) => Observable<T>): Observable<Loadable<T>> {
  return trigger.pipe(
    switchMap((key) =>
      source(key).pipe(
        map((data): Loadable<T> => ({ loading: false, error: false, data })),
        catchError(() => of<Loadable<T>>({ loading: false, error: true, data: null })),
        startWith<Loadable<T>>(LOADING),
      ),
    ),
  );
}
