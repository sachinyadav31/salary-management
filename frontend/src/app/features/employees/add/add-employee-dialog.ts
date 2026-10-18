import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { startWith } from 'rxjs';

import { EmployeeApi } from '../../../core/api/employee-api';
import { MetaApi } from '../../../core/api/meta-api';
import { EmployeeDetail, JobLevel } from '../../../core/api/models';
import { BandBar } from '../../../shared/band-bar';
import { countryFlag } from '../../../shared/flag';
import { MoneyPipe } from '../../../shared/money';
import { parseAmount, previewChange } from '../detail/salary-preview';
import { bandFor, createErrors, hireDateBounds, hireDateError } from './new-hire';

type Field = 'firstName' | 'lastName' | 'email' | 'countryCode' | 'departmentId' | 'jobTitle' | 'hireDate' | 'salaryAmount';

const today = () => new Date().toISOString().slice(0, 10);

/**
 * Hire someone. The country fixes the currency, the server assigns the employee code and records the HIRE salary,
 * and the starting salary is shown against that country × level's pay band before saving.
 */
@Component({
  selector: 'pa-add-employee-dialog',
  imports: [
    DecimalPipe, ReactiveFormsModule, MatAutocompleteModule, MatButtonModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, BandBar, MoneyPipe,
  ],
  templateUrl: './add-employee-dialog.html',
  styleUrl: './add-employee-dialog.scss',
})
export class AddEmployeeDialog {
  private readonly api = inject(EmployeeApi);
  private readonly metaApi = inject(MetaApi);
  private readonly ref = inject<MatDialogRef<AddEmployeeDialog, EmployeeDetail>>(MatDialogRef);

  protected readonly meta = toSignal(this.metaApi.meta$);
  protected readonly bands = toSignal(this.metaApi.payBands$);
  protected readonly flag = countryFlag;
  protected readonly dates = hireDateBounds();
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(200)] }),
    countryCode: new FormControl<string | null>(null, { validators: Validators.required }),
    departmentId: new FormControl<number | null>(null, { validators: Validators.required }),
    jobTitle: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    level: new FormControl<JobLevel>('L2', { nonNullable: true }),
    hireDate: new FormControl(today(), { nonNullable: true, validators: (c) => this.validateHireDate(c) }),
    salaryAmount: new FormControl('', { nonNullable: true, validators: (c) => this.validateSalary(c) }),
  });

  protected readonly values = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });

  protected readonly currency = computed(() => {
    const code = this.values().countryCode;
    return this.meta()?.countries.find((c) => c.code === code)?.currency ?? null;
  });

  protected readonly titleOptions = computed(() => {
    const query = (this.values().jobTitle ?? '').toLowerCase();
    return (this.meta()?.jobTitles ?? []).filter((t) => t.toLowerCase().includes(query)).slice(0, 8);
  });

  protected readonly band = computed(() => bandFor(this.bands(), this.values().countryCode ?? null, this.values().level ?? 'L1'));

  /** Where the starting salary lands in the band, with the same compa-ratio formula as the server. */
  protected readonly preview = computed(() => {
    const currency = this.currency();
    if (!currency) return null;
    const parsed = parseAmount(this.values().salaryAmount, currency);
    if (!parsed.ok) return null;
    const salary = { minorUnits: parsed.minorUnits, currency, amount: parsed.amount };
    const { compaRatio, position } = previewChange(salary, this.band(), parsed.minorUnits);
    return { salary, compaRatio, position };
  });

  constructor() {
    // The currency depends on the country, so re-check the amount's decimals when it changes.
    effect(() => {
      this.currency();
      this.form.controls.salaryAmount.updateValueAndValidity({ emitEvent: false });
    });
  }

  protected error(field: Field): string | null {
    const errors = this.form.controls[field].errors;
    if (!errors || !this.form.controls[field].touched) return null;
    if (errors['server']) return errors['server'];
    if (errors['required']) return 'Required';
    if (errors['email']) return 'Enter a valid email';
    if (errors['hireDate']) return errors['hireDate'];
    if (errors['amount'] === 'required') return 'Enter the starting salary';
    if (errors['amount'] === 'decimals') return 'Too many decimal places for this currency';
    if (errors['amount']) return 'Enter a positive amount, e.g. 85000';
    return 'Too long';
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    const currency = this.currency();
    if (this.form.invalid || this.saving() || !currency) return;
    const v = this.form.getRawValue();
    const salary = parseAmount(v.salaryAmount, currency);
    if (!salary.ok) return;
    this.saving.set(true);
    this.formError.set(null);
    this.api.create({
      firstName: v.firstName.trim(), lastName: v.lastName.trim(), email: v.email.trim(), countryCode: v.countryCode!,
      departmentId: v.departmentId!, jobTitle: v.jobTitle.trim(), level: v.level, hireDate: v.hireDate, salaryAmount: salary.amount,
    }).subscribe({
      next: (employee) => this.ref.close(employee),
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        for (const [field, message] of Object.entries(createErrors(e))) {
          const control = this.form.get(field);
          if (control) {
            control.setErrors({ ...(control.errors ?? {}), server: message });
            control.markAsTouched();
          } else {
            this.formError.set(message);
          }
        }
      },
    });
  }

  private validateHireDate(control: AbstractControl): ValidationErrors | null {
    const message = hireDateError(control.value as string);
    return message ? { hireDate: message } : null;
  }

  private validateSalary(control: AbstractControl): ValidationErrors | null {
    const parsed = parseAmount(control.value, this.currency?.() ?? 'USD');
    return parsed.ok ? null : { amount: parsed.error };
  }
}

/** Opens the dialog; on save, opens the new employee's drawer so HR sees the result (and their HIRE record). */
export function openAddEmployee(dialog: MatDialog, router: Router, api: EmployeeApi): void {
  dialog
    .open(AddEmployeeDialog, { width: '640px', maxWidth: 'calc(100vw - 32px)', autoFocus: 'first-tabbable' })
    .afterClosed()
    .subscribe((employee) => {
      if (!employee) return;
      api.notifyChanged(employee);
      router.navigate(['/employees', employee.id]);
    });
}
