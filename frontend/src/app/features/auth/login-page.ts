import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';

import { problemMessage } from '../../core/api/problem-interceptor';
import { AuthService } from '../../core/auth/auth';

/** Demo accounts seeded for reviewers (migration V6). Not a pattern for real deployments. */
export const DEMO_ACCOUNTS = [
  { email: 'hr.admin@acme.example', name: 'Priya Sharma' },
  { email: 'alex.morgan@acme.example', name: 'Alex Morgan' },
];
export const DEMO_PASSWORD = 'PayAtlas!2026';

@Component({
  selector: 'pa-login-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  /** Query params, bound by the router. */
  readonly returnUrl = input<string>();
  readonly expired = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly demoAccounts = DEMO_ACCOUNTS;
  protected readonly demoPassword = DEMO_PASSWORD;
  protected readonly showPassword = signal(false);
  protected readonly signingIn = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  protected useDemo(email: string): void {
    this.form.setValue({ email, password: DEMO_PASSWORD });
    this.error.set(null);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.signingIn()) return;
    const { email, password } = this.form.getRawValue();
    this.signingIn.set(true);
    this.error.set(null);
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.safeReturnUrl()),
      error: (error: HttpErrorResponse) => {
        this.signingIn.set(false);
        this.error.set(problemMessage(error));
        this.form.controls.password.reset();
      },
    });
  }

  /** Only same-app paths: never redirect to an external URL taken from the query string (open redirect). */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : '/insights';
  }
}
