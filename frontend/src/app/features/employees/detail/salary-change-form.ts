import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, input, output, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { startWith } from 'rxjs';

import { EmployeeDetail, RecordSalaryChangeRequest, SalaryChangeReason } from '../../../core/api/models';
import { MoneyPipe } from '../../../shared/money';
import { parseAmount, previewChange } from './salary-preview';
import { reasonLabel } from './timeline-option';

export type SalaryChangeInput = Omit<RecordSalaryChangeRequest, 'version'>;

const REASONS: Exclude<SalaryChangeReason, 'HIRE'>[] = ['ANNUAL_REVIEW', 'MERIT', 'PROMOTION', 'MARKET_ADJUSTMENT', 'CORRECTION'];
const AMOUNT_ERRORS: Record<string, string> = {
  required: 'Enter the new annual salary',
  invalid: 'Use digits only, e.g. 95000.50',
  decimals: 'Too many decimal places for this currency',
  positive: 'Salary must be more than zero',
};

const isoDate = (date: Date) => date.toISOString().slice(0, 10);

/** Presentational: validates and previews; the drawer submits and handles server errors and conflicts. */
@Component({
  selector: 'pa-salary-change-form',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MoneyPipe],
  templateUrl: './salary-change-form.html',
  styleUrl: './forms.scss',
})
export class SalaryChangeForm {
  readonly employee = input.required<EmployeeDetail>();
  /** Reason pre-selected when the form opens (e.g. PROMOTION after a level increase). */
  readonly initialReason = input<Exclude<SalaryChangeReason, 'HIRE'>>('ANNUAL_REVIEW');
  /** Field → message from a 400 response; `_form` for errors not tied to a field. */
  readonly serverErrors = input<Record<string, string> | null>(null);
  readonly saving = input(false);
  readonly submitted = output<SalaryChangeInput>();
  readonly cancelled = output<void>();

  /**
   * Validators run as soon as the FormControls are constructed, before inputs are set, and reading a
   * required input that early throws. They read this snapshot instead, kept in sync by an effect.
   */
  private current: EmployeeDetail | null = null;

  protected readonly reasons = REASONS;
  protected readonly reasonLabel = reasonLabel;
  protected readonly today = isoDate(new Date());
  protected readonly maxDate = isoDate(new Date(Date.now() + 365 * 24 * 3600 * 1000));

  protected readonly form = new FormGroup({
    amount: new FormControl('', { nonNullable: true, validators: (c) => this.validateAmount(c) }),
    effectiveDate: new FormControl(this.today, { nonNullable: true, validators: [Validators.required, (c) => this.validateDate(c)] }),
    reason: new FormControl<Exclude<SalaryChangeReason, 'HIRE'>>('ANNUAL_REVIEW', { nonNullable: true }),
    note: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500), (c) => this.validateNote(c)] }),
  });

  private readonly values = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });

  protected readonly currencySymbol = computed(() =>
    new Intl.NumberFormat('en', { style: 'currency', currency: this.employee().salary.currency, currencyDisplay: 'narrowSymbol' })
      .formatToParts(0).find((p) => p.type === 'currency')?.value ?? '');

  /** Live consequence of the typed amount: % change, new compa-ratio, band position. */
  protected readonly preview = computed(() => {
    const employee = this.employee();
    const parsed = parseAmount(this.values().amount, employee.salary.currency);
    return parsed.ok ? previewChange(employee.salary, employee.payBand, parsed.minorUnits) : null;
  });
  protected readonly scheduled = computed(() => (this.values().effectiveDate ?? '') > this.today);

  constructor() {
    this.form.controls.reason.valueChanges.subscribe(() => this.form.controls.note.updateValueAndValidity());

    // Apply the pre-selected reason once, without marking the form dirty.
    effect(() => {
      const reason = this.initialReason();
      untracked(() => {
        if (!this.form.dirty) this.form.controls.reason.setValue(reason, { emitEvent: true });
      });
    });

    // Re-validate when the employee changes (e.g. latest version loaded after a conflict).
    effect(() => {
      this.current = this.employee();
      this.form.controls.amount.updateValueAndValidity();
      this.form.controls.effectiveDate.updateValueAndValidity();
    });

    // Attach server-side validation messages to the matching fields.
    effect(() => {
      const errors = this.serverErrors() ?? {};
      for (const [field, message] of Object.entries(errors)) {
        const control = this.form.get(field);
        control?.setErrors({ ...(control.errors ?? {}), server: message });
        control?.markAsTouched();
      }
    });
  }

  isDirty(): boolean {
    return this.form.dirty;
  }

  protected amountError(): string | null {
    const errors = this.form.controls.amount.errors;
    if (!errors) return null;
    return errors['server'] ?? AMOUNT_ERRORS[errors['amount']] ?? null;
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const value = this.form.getRawValue();
    const parsed = parseAmount(value.amount, this.employee().salary.currency);
    if (!parsed.ok) return;
    this.submitted.emit({
      amount: parsed.amount,
      effectiveDate: value.effectiveDate,
      reason: value.reason,
      note: value.note.trim() || null,
    });
  }

  private validateAmount(control: AbstractControl): ValidationErrors | null {
    const parsed = parseAmount(control.value, this.current?.salary.currency ?? 'USD');
    return parsed.ok ? null : { amount: parsed.error };
  }

  /** Mirrors the backend: not before hire, at most one year ahead. */
  private validateDate(control: AbstractControl): ValidationErrors | null {
    const value = control.value as string;
    if (!value) return null;
    const hireDate = this.current?.hireDate;
    if (hireDate && value < hireDate) return { beforeHire: hireDate };
    if (value > this.maxDate) return { tooFar: true };
    return null;
  }

  private validateNote(control: AbstractControl): ValidationErrors | null {
    const reason = control.parent?.get('reason')?.value;
    return reason === 'CORRECTION' && !String(control.value ?? '').trim() ? { requiredForCorrection: true } : null;
  }
}
