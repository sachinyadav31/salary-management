import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth';
import { signedInGuard, signedOutGuard } from './auth-guards';
import { sessionInterceptor } from './session-interceptor';

describe('authentication', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([sessionInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  it('learns who is signed in from the session endpoint', async () => {
    const loaded = firstValueFrom(auth.loadSession());
    http.expectOne('/api/auth/session').flush({ authenticated: true, user: { email: 'a@acme.example', fullName: 'Ada' } });
    await loaded;

    expect(auth.user()?.fullName).toBe('Ada');
  });

  it('treats an unreachable session endpoint as signed out', async () => {
    const loaded = firstValueFrom(auth.loadSession());
    http.expectOne('/api/auth/session').flush(null, { status: 503, statusText: 'Unavailable' });
    await loaded;

    expect(auth.signedIn()).toBe(false);
  });

  it('signs in and out', () => {
    auth.login('a@acme.example', 'secret').subscribe();
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ email: 'a@acme.example', password: 'secret' });
    login.flush({ email: 'a@acme.example', fullName: 'Ada' });
    expect(auth.signedIn()).toBe(true);

    auth.logout();
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

    expect(auth.signedIn()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('guards app pages and the login page', () => {
    const run = (guard: typeof signedInGuard, url: string) =>
      TestBed.runInInjectionContext(() => guard({} as never, { url } as never)) as boolean | UrlTree;

    const redirect = run(signedInGuard, '/employees/42?country=IN') as UrlTree;
    expect(router.serializeUrl(redirect)).toBe('/login?returnUrl=%2Femployees%2F42%3Fcountry%3DIN');
    expect(run(signedOutGuard, '/login')).toBe(true);

    auth.user.set({ email: 'a@acme.example', fullName: 'Ada' });
    expect(run(signedInGuard, '/insights')).toBe(true);
    expect(router.serializeUrl(run(signedOutGuard, '/login') as UrlTree)).toBe('/insights');
  });

  it('sends the user back to sign in when an API call reports the session has ended', () => {
    auth.user.set({ email: 'a@acme.example', fullName: 'Ada' });

    TestBed.inject(HttpClient).get('/api/employees').subscribe({ error: () => {} });
    http.expectOne('/api/employees').flush({ title: 'Not signed in' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.signedIn()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/', expired: 1 } });
  });
});
