import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Observable, map } from 'rxjs';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirm: string;
  cancel?: string;
  /** Destructive actions use the "below band" coral to signal caution. */
  danger?: boolean;
}

@Component({
  selector: 'pa-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content><p>{{ data.message }}</p></mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">{{ data.cancel ?? 'Cancel' }}</button>
      <button mat-flat-button type="button" [class.danger]="data.danger" [mat-dialog-close]="true" cdkFocusInitial>
        {{ data.confirm }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    p { margin: 0; color: var(--pa-muted); line-height: 1.5; }
    .danger { --mat-button-filled-container-color: var(--pa-below); --mat-button-filled-label-text-color: #fff; }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmOptions>(MAT_DIALOG_DATA);
}

/** Opens a confirmation and emits true only if the user confirms. */
export function confirm(dialog: MatDialog, options: ConfirmOptions): Observable<boolean> {
  return dialog
    .open(ConfirmDialog, { data: options, width: '420px', autoFocus: 'dialog' })
    .afterClosed()
    .pipe(map((result) => result === true));
}
