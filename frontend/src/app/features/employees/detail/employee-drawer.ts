import { DatePipe, DecimalPipe } from '@angular/common';
import { A11yModule } from '@angular/cdk/a11y';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, afterNextRender, computed, inject, input, signal, viewChild } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject, forkJoin, map, merge, of } from 'rxjs';

import { EmployeeApi } from '../../../core/api/employee-api';
import { MetaApi } from '../../../core/api/meta-api';
import { problemMessage } from '../../../core/api/problem-interceptor';
import { EmployeeDetail, EmployeeStatus, ProblemDetail, SalaryHistoryItem } from '../../../core/api/models';
import { BandBar } from '../../../shared/band-bar';
import { Chart } from '../../../shared/charts/chart';
import { ChartPalette, resolvePalette } from '../../../shared/charts/palette';
import { CompaBadge } from '../../../shared/compa-badge';
import { CanLeave } from '../../../shared/can-leave';
import { confirm } from '../../../shared/confirm-dialog';
import { countryFlag } from '../../../shared/flag';
import { LOADING, loadable } from '../../../shared/loadable';
import { MoneyPipe, formatMoney } from '../../../shared/money';
import { FieldChange, profileChanges } from './profile-diff';
import { ProfileForm, ProfileInput } from './profile-form';
import { SalaryChangeForm, SalaryChangeInput } from './salary-change-form';
import { tenure } from './salary-preview';
import { reasonLabel, timelineOption } from './timeline-option';

export type DrawerMode = 'view' | 'salary' | 'edit';

interface Loaded {
  employee: EmployeeDetail;
  history: SalaryHistoryItem[];
}

/** Employee detail as a routed drawer over the directory (/employees/:id keeps the list's filters). */
@Component({
  selector: 'pa-employee-drawer',
  imports: [
    A11yModule, DatePipe, DecimalPipe, MatButtonModule, MatIconModule, MatTooltipModule,
    BandBar, Chart, CompaBadge, MoneyPipe, ProfileForm, SalaryChangeForm,
  ],
  templateUrl: './employee-drawer.html',
  styleUrl: './employee-drawer.scss',
  host: { '(document:keydown.escape)': 'onEscape()' },
})
export class EmployeeDrawer implements CanLeave {
  /** Route param, bound by the router (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(EmployeeApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly meta = toSignal(inject(MetaApi).meta$);

  private readonly reload = new Subject<void>();
  /** Replaces the loaded data after a successful write, without a refetch. */
  private readonly replaced = new Subject<Loaded>();

  protected readonly state = toSignal(
    merge(
      loadable(
        merge(toObservable(this.id), this.reload.pipe(map(() => this.id()))).pipe(map(Number)),
        (id) => forkJoin({ employee: this.api.get(id), history: this.api.history(id) }),
      ),
      this.replaced.pipe(map((data) => ({ loading: false, error: false, data }))),
    ),
    { initialValue: LOADING },
  );

  protected readonly employee = computed(() => this.state().data?.employee ?? null);
  protected readonly history = computed(() => this.state().data?.history ?? []);
  protected readonly mode = signal<DrawerMode>('view');
  protected readonly saving = signal(false);
  protected readonly serverErrors = signal<Record<string, string> | null>(null);
  /** Set after a 409: what someone else changed. The latest version is already loaded; the user's edits are kept. */
  protected readonly conflict = signal<FieldChange[] | null>(null);

  private readonly salaryForm = viewChild(SalaryChangeForm);
  private readonly profileForm = viewChild(ProfileForm);

  private readonly palette = signal<ChartPalette | null>(null);
  protected readonly timeline = computed(() => {
    const palette = this.palette();
    const history = this.history();
    return palette && history.length ? timelineOption(history, palette) : null;
  });

  protected readonly flag = countryFlag;
  protected readonly reason = reasonLabel;
  protected readonly tenure = computed(() => {
    const employee = this.employee();
    return employee ? tenure(employee.hireDate) : '';
  });

  constructor() {
    afterNextRender(() => this.palette.set(resolvePalette()));
  }

  protected retry(): void {
    this.reload.next();
  }

  protected close(): void {
    this.router.navigate(['..'], { relativeTo: this.route, queryParamsHandling: 'preserve' });
  }

  protected onEscape(): void {
    if (this.dialog.openDialogs.length) return; // the dialog handles its own Escape
    if (this.mode() !== 'view') {
      this.cancelEdit();
    } else {
      this.close();
    }
  }

  protected initials(employee: EmployeeDetail): string {
    return (employee.firstName[0] + employee.lastName[0]).toUpperCase();
  }

  protected startEdit(mode: Exclude<DrawerMode, 'view'>): void {
    this.serverErrors.set(null);
    this.conflict.set(null);
    this.mode.set(mode);
  }

  protected cancelEdit(): void {
    this.confirmDiscard().subscribe((discard) => {
      if (discard) {
        this.mode.set('view');
        this.serverErrors.set(null);
        this.conflict.set(null);
      }
    });
  }

  /** Route guard hook: closing (X, backdrop, Back button, any navigation) with unsaved input asks first. */
  canLeave(): Observable<boolean> {
    return this.confirmDiscard();
  }

  protected saveSalaryChange(input: SalaryChangeInput): void {
    const employee = this.employee()!;
    this.write(
      this.api.recordSalaryChange(employee.id, { ...input, version: employee.version }),
      (response) => {
        this.accept({ employee: response.employee, history: response.history });
        const scheduled = response.history.find((h) => h.status === 'SCHEDULED' && h.effectiveDate === input.effectiveDate);
        this.notify(scheduled
          ? `Scheduled: ${formatMoney(scheduled.amount)} from ${input.effectiveDate}`
          : `Salary updated to ${formatMoney(response.employee.salary)}`);
      },
    );
  }

  protected saveProfile(input: ProfileInput): void {
    const employee = this.employee()!;
    this.write(this.api.update(employee.id, { ...input, version: employee.version }), (updated) => {
      this.accept({ employee: updated, history: this.history() });
      this.notify('Profile saved');
    });
  }

  protected changeStatus(status: EmployeeStatus): void {
    const employee = this.employee()!;
    const proceed = status === 'INACTIVE'
      ? confirm(this.dialog, {
          title: `Deactivate ${employee.fullName}?`,
          message: 'They will be excluded from insights and the default directory view. Their salary history is kept, and you can reactivate them at any time.',
          confirm: 'Deactivate',
          danger: true,
        })
      : of(true);
    proceed.subscribe((ok) => {
      if (!ok) return;
      this.write(this.api.changeStatus(employee.id, status, employee.version), (updated) => {
        this.accept({ employee: updated, history: this.history() });
        this.notify(status === 'INACTIVE' ? `${updated.fullName} deactivated` : `${updated.fullName} reactivated`);
      });
    });
  }

  protected dismissConflict(): void {
    this.conflict.set(null);
  }

  /** Runs a write; maps 409 → conflict flow, 400 → field errors, anything else → form-level message. */
  private write<T>(request: Observable<T>, onSuccess: (result: T) => void): void {
    this.saving.set(true);
    this.serverErrors.set(null);
    request.subscribe({
      next: (result) => {
        this.saving.set(false);
        this.conflict.set(null);
        this.mode.set('view');
        onSuccess(result);
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        if (error.status === 409) {
          this.loadLatestAfterConflict();
        } else if (error.status === 400) {
          const problem = error.error as ProblemDetail | null;
          this.serverErrors.set(problem?.errors ?? { _form: problem?.detail ?? problemMessage(error) });
        } else {
          this.serverErrors.set({ _form: problemMessage(error) });
          if (this.mode() === 'view') this.notify(problemMessage(error));
        }
      },
    });
  }

  /**
   * Someone saved first. Load their version (so the next save carries the new version), show what changed,
   * and keep the user's form open with their input, so nothing is overwritten silently and nothing is lost.
   */
  private loadLatestAfterConflict(): void {
    const stale = this.employee()!;
    forkJoin({ employee: this.api.get(stale.id), history: this.api.history(stale.id) }).subscribe({
      next: (latest) => {
        this.conflict.set(profileChanges(stale, latest.employee));
        this.accept(latest);
      },
      error: (error: HttpErrorResponse) => this.serverErrors.set({ _form: problemMessage(error) }),
    });
  }

  private confirmDiscard(): Observable<boolean> {
    const dirty = (this.mode() === 'salary' && this.salaryForm()?.isDirty()) || (this.mode() === 'edit' && this.profileForm()?.isDirty());
    return dirty
      ? confirm(this.dialog, { title: 'Discard changes?', message: 'Your unsaved input will be lost.', confirm: 'Discard', cancel: 'Keep editing', danger: true })
      : of(true);
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 4000 });
  }

  /** Replace the shown data with the server's latest and let the directory refresh. */
  private accept(data: Loaded): void {
    this.replaced.next(data);
    this.api.notifyChanged(data.employee);
  }
}
