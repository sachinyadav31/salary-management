import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { startWith } from 'rxjs';

import { EmployeeApi } from '../../../core/api/employee-api';
import { BonusType, BulkChangeRequest, BulkKind, BulkPreview, BulkResult, BulkRow, Money, SalaryChangeReason } from '../../../core/api/models';
import { problemMessage } from '../../../core/api/problem-interceptor';
import { MoneyPipe } from '../../../shared/money';
import { reasonLabel } from '../detail/timeline-option';
import { bonusLabel } from '../detail/timeline';
import { BULK_BONUS_TYPES, BULK_REASONS, BonusMode, toBulkRequest } from './bulk-change';

export interface BulkChangeData {
  /** The directory filters (unpaged), sent as-is: the server selects the same people the table shows. */
  params: HttpParams;
  /** What the table shows right now, for the heading. */
  total: number;
  selection: string;
  /** The selection's one currency, or null when it spans countries. */
  currency: string | null;
}

const isoDate = (date: Date) => date.toISOString().slice(0, 10);

/**
 * Preview first, then apply. Any edit discards the preview, so what you apply is always what you saw. The request id
 * is created with the preview, so a double click or a retry after a network error applies it once.
 */
@Component({
  selector: 'pa-bulk-change-dialog',
  imports: [
    DecimalPipe, ReactiveFormsModule, MatButtonModule, MatButtonToggleModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MoneyPipe,
  ],
  templateUrl: './bulk-change-dialog.html',
  styleUrl: './bulk-change-dialog.scss',
})
export class BulkChangeDialog {
  protected readonly data = inject<BulkChangeData>(MAT_DIALOG_DATA);
  private readonly api = inject(EmployeeApi);
  private readonly ref = inject<MatDialogRef<BulkChangeDialog, BulkResult>>(MatDialogRef);

  protected readonly reasons = BULK_REASONS;
  protected readonly bonusTypes = BULK_BONUS_TYPES;
  protected readonly reasonLabel = reasonLabel;
  protected readonly bonusLabel = bonusLabel;
  protected readonly maxDate = isoDate(new Date(Date.now() + 365 * 24 * 3600 * 1000));

  protected readonly form = new FormGroup({
    kind: new FormControl<BulkKind>('RAISE', { nonNullable: true }),
    percent: new FormControl('', { nonNullable: true }),
    bonusMode: new FormControl<BonusMode>('percent', { nonNullable: true }),
    amount: new FormControl('', { nonNullable: true }),
    effectiveDate: new FormControl(isoDate(new Date()), { nonNullable: true }),
    reason: new FormControl<SalaryChangeReason>('MERIT', { nonNullable: true }),
    bonusType: new FormControl<BonusType>('PERFORMANCE', { nonNullable: true }),
    note: new FormControl('', { nonNullable: true }),
  });

  protected readonly value = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  protected readonly isRaise = computed(() => this.value().kind === 'RAISE');
  protected readonly preview = signal<{ result: BulkPreview; request: BulkChangeRequest; requestId: string } | null>(null);
  protected readonly busy = signal<'preview' | 'apply' | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly hiddenRows = computed(() => {
    const p = this.preview()?.result;
    return p ? p.affected - p.rows.length : 0;
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.preview.set(null);
      this.error.set(null);
    });
  }

  protected runPreview(): void {
    const built = toBulkRequest(this.form.getRawValue(), this.data.currency);
    if (!built.ok) {
      this.error.set(built.error);
      return;
    }
    this.busy.set('preview');
    this.api.bulkPreview(this.data.params, built.request).subscribe({
      next: (result) => {
        this.busy.set(null);
        this.preview.set({ result, request: built.request, requestId: crypto.randomUUID() });
      },
      error: (e: HttpErrorResponse) => {
        this.busy.set(null);
        this.error.set(problemMessage(e));
      },
    });
  }

  protected apply(): void {
    const preview = this.preview();
    if (!preview || this.busy()) return;
    this.busy.set('apply');
    this.api.bulkApply(this.data.params, { ...preview.request, requestId: preview.requestId }).subscribe({
      next: (result) => this.ref.close(result),
      error: (e: HttpErrorResponse) => {
        this.busy.set(null);
        this.error.set(problemMessage(e));
      },
    });
  }

  protected percentOf(from: Money, to: Money): number {
    return ((to.minorUnits - from.minorUnits) * 100) / from.minorUnits;
  }

  protected share(amount: Money, base: Money): number {
    return (amount.minorUnits * 100) / base.minorUnits;
  }

  /** "→ within" when the raise moves someone relative to their band; empty when it doesn't. */
  protected bandMove(row: BulkRow): string {
    return row.bandAfter && row.bandBefore !== row.bandAfter ? `→ ${row.bandAfter.toLowerCase()}` : '';
  }
}
