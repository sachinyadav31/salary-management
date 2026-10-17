import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { handlesErrors, problemInterceptor, problemMessage } from './problem-interceptor';
import { HttpErrorResponse } from '@angular/common/http';

describe('problem interceptor', () => {
  const open = vi.fn();

  beforeEach(() => {
    open.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([problemInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open } },
      ],
    });
  });

  it('shows API errors in a snackbar', () => {
    TestBed.inject(HttpClient).get('/api/x').subscribe({ error: () => {} });
    TestBed.inject(HttpTestingController).expectOne('/api/x')
      .flush({ detail: 'size must be between 1 and 100' }, { status: 400, statusText: 'Bad Request' });

    expect(open).toHaveBeenCalledWith('size must be between 1 and 100', 'Dismiss', expect.anything());
  });

  it('stays quiet for requests whose caller handles errors itself', () => {
    TestBed.inject(HttpClient).put('/api/x', {}, { context: handlesErrors() }).subscribe({ error: () => {} });
    TestBed.inject(HttpTestingController).expectOne('/api/x').flush({ detail: 'conflict' }, { status: 409, statusText: 'Conflict' });

    expect(open).not.toHaveBeenCalled();
  });

  it('summarises field errors and network failures', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 400, error: { errors: { email: 'must be a well-formed email address' } } })))
      .toBe('email: must be a well-formed email address');
    expect(problemMessage(new HttpErrorResponse({ status: 0 }))).toContain('Cannot reach the server');
  });
});
