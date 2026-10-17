import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { startWith } from 'rxjs';

import { AwardBonusRequest, BonusType, EmployeeDetail } from '../../../core/api/models';
import { MoneyPipe } from '../../../shared/money';
import { parseAmount } from './salary-preview';
import { bonusLabel } from './timeline';

const TYPES: BonusType[] = ['SPOT', 'PERFORMANCE', 'SIGN_ON', 'RETENTION'];
const isoDate = (date: Date) => date.toISOString().slice(0, 10);

/** Presentational: validates and shows context; the drawer submits. Bonuses never change base salary. */
@Component({
  selector: 'pa-bonus-form',
  imports: [DecimalPipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MoneyPipe],
  templateUrl: './bonus-form.html',
  styleUrl: './forms.scss',
})
export class BonusForm {
  readonly employee = input.required<EmployeeDetail>();
  readonly serverErrors = input<Record<string, string> | null>(null);
  readonly saving = input(false);
  readonly submitted = output<AwardBonusRequest>();
  readonly cancelled = output<void>();

  /** Validators run before inputs are set; they read this snapshot (see SalaryChangeForm). */
  private current: EmployeeDetail | null = null;

  protected readonly types = TYPES;
  protected readonly label = bonusLabel;
  protected readonly maxDate = isoDate(new Date(Date.now() + 365 * 24 * 3600 * 1000));

  protected readonly form = new FormGroup({
    type: new FormControl<BonusType>('SPOT', { nonNullable: true }),
    amount: new FormControl('', { nonNullable: true, validators: (c) => this.validateAmount(c) }),
    awardedOn: new FormControl(isoDate(new Date()), { nonNullable: true, validators: [Validators.required, (c) => this.validateDate(c)] }),
    note: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500), (c) => this.validateNote(c)] }),
  });

  private readonly values = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });

  /** The amount as a share of base salary and of the variable-pay target, to sanity-check it. */
  protected readonly context = computed(() => {
    const employee = this.employee();
    const parsed = parseAmount(this.values().amount, employee.salary.currency);
    if (!parsed.ok) return null;
    const ofBase = (parsed.minorUnits * 100) / employee.salary.minorUnits;
    const target = employee.variableTarget.minorUnits;
    return { ofBase, ofTarget: target > 0 ? (parsed.minorUnits * 100) / target : null };
  });

  constructor() {
    this.form.controls.type.valueChanges.subscribe(() => this.form.controls.note.updateValueAndValidity());
    effect(() => {
      this.current = this.employee();
      this.form.controls.amount.updateValueAndValidity();
      this.form.controls.awardedOn.updateValueAndValidity();
    });
    effect(() => {
      for (const [field, message] of Object.entries(this.serverErrors() ?? {})) {
        const control = this.form.get(field);
        control?.setErrors({ ...(control.errors ?? {}), server: message });
        control?.markAsTouched();
      }
    });
  }

  isDirty(): boolean {
    return this.form.dirty;
  }

  protected error(field: 'amount' | 'awardedOn' | 'note'): string | null {
    const errors = this.form.controls[field].errors;
    if (!errors) return null;
    if (errors['server']) return errors['server'];
    if (errors['amount'] === 'required') return 'Enter the bonus amount';
    if (errors['amount'] === 'decimals') return 'Too many decimal places for this currency';
    if (errors['amount']) return 'Enter a positive amount, e.g. 25000';
    if (errors['beforeHire']) return 'Not before the hire date';
    if (errors['tooFar']) return 'At most one year ahead';
    if (errors['requiredForSpot']) return 'Say what this bonus recognises';
    if (errors['required']) return 'Required';
    return 'At most 500 characters';
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const value = this.form.getRawValue();
    const parsed = parseAmount(value.amount, this.employee().salary.currency);
    if (!parsed.ok) return;
    this.submitted.emit({ type: value.type, amount: parsed.amount, awardedOn: value.awardedOn, note: value.note.trim() || null });
  }

  private validateAmount(control: AbstractControl): ValidationErrors | null {
    const parsed = parseAmount(control.value, this.current?.salary.currency ?? 'USD');
    return parsed.ok ? null : { amount: parsed.error };
  }

  private validateDate(control: AbstractControl): ValidationErrors | null {
    const value = control.value as string;
    if (!value) return null;
    if (this.current && value < this.current.hireDate) return { beforeHire: true };
    return value > this.maxDate ? { tooFar: true } : null;
  }

  /** Mirrors the backend: a spot bonus must say what it recognises. */
  private validateNote(control: AbstractControl): ValidationErrors | null {
    return control.parent?.get('type')?.value === 'SPOT' && !String(control.value ?? '').trim() ? { requiredForSpot: true } : null;
  }
}
