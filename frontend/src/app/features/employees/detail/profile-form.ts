import { Component, computed, effect, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { EmployeeDetail, JobLevel, Meta, UpdateEmployeeRequest } from '../../../core/api/models';

export type ProfileInput = Omit<UpdateEmployeeRequest, 'version'>;

/** Presentational profile editor. Country is not editable: relocation changes currency and therefore salary. */
@Component({
  selector: 'pa-profile-form',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './profile-form.html',
  styleUrl: './forms.scss',
})
export class ProfileForm {
  readonly employee = input.required<EmployeeDetail>();
  readonly meta = input<Meta | undefined>();
  readonly serverErrors = input<Record<string, string> | null>(null);
  readonly saving = input(false);
  readonly submitted = output<ProfileInput>();
  readonly cancelled = output<void>();

  protected readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(200)] }),
    departmentId: new FormControl(0, { nonNullable: true, validators: Validators.required }),
    jobTitle: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    level: new FormControl<JobLevel>('L1', { nonNullable: true }),
    variableTargetPercent: new FormControl(0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0), Validators.max(100), Validators.pattern(/^\d{1,3}(\.\d{1,2})?$/)],
    }),
  });

  private readonly title = toSignal(this.form.controls.jobTitle.valueChanges, { initialValue: '' });
  protected readonly titleOptions = computed(() => {
    const query = this.title().toLowerCase();
    return (this.meta()?.jobTitles ?? []).filter((t) => t.toLowerCase().includes(query)).slice(0, 8);
  });

  private initialisedFor: number | null = null;

  constructor() {
    // Fill from the employee once; keep the user's edits if a newer version arrives after a conflict.
    effect(() => {
      const e = this.employee();
      if (this.initialisedFor !== e.id) {
        this.initialisedFor = e.id;
        this.form.reset({
          firstName: e.firstName, lastName: e.lastName, email: e.email,
          departmentId: e.departmentId, jobTitle: e.jobTitle, level: e.level,
          variableTargetPercent: e.variableTargetPercent,
        });
      }
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

  protected error(field: keyof ProfileInput): string | null {
    const errors = this.form.controls[field].errors;
    if (!errors) return null;
    if (errors['server']) return errors['server'];
    if (errors['required']) return 'Required';
    if (errors['email']) return 'Enter a valid email address';
    if (errors['maxlength']) return 'Too long';
    if (errors['min'] || errors['max'] || errors['pattern']) return 'Between 0 and 100, at most two decimals';
    return null;
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const v = this.form.getRawValue();
    this.submitted.emit({ ...v, firstName: v.firstName.trim(), lastName: v.lastName.trim(), email: v.email.trim(), jobTitle: v.jobTitle.trim() });
  }
}
