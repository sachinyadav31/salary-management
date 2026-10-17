import { CanDeactivateFn } from '@angular/router';
import { Observable } from 'rxjs';

/** Implemented by routed components that may hold unsaved input. */
export interface CanLeave {
  canLeave(): Observable<boolean> | boolean;
}

/**
 * Asks the component before navigating away. Lives apart from the components so the route config
 * doesn't pull lazily-loaded code into the main bundle.
 */
export const canLeaveGuard: CanDeactivateFn<CanLeave> = (component) => component?.canLeave() ?? true;
