import { HttpParams, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { BulkPreview } from '../../../core/api/models';
import { BulkChangeData, BulkChangeDialog } from './bulk-change-dialog';

const DATA: BulkChangeData = {
  params: new HttpParams().append('country', 'IN').append('status', 'ACTIVE'),
  total: 2,
  selection: 'India',
  currency: 'INR',
};

const PREVIEW: BulkPreview = {
  kind: 'RAISE', matched: 2, affected: 1, scheduled: 0,
  totalUsd: { minorUnits: 45600, currency: 'USD', amount: '456.00' },
  totalsByCurrency: [{ minorUnits: 3800000, currency: 'INR', amount: '38000.00' }],
  bandMoves: { intoBand: 1, intoAboveBand: 0, stillBelowBand: 0 },
  rows: [{
    employeeId: 201, employeeCode: 'E00201', name: 'Tom Agarwal',
    from: { minorUnits: 76000000, currency: 'INR', amount: '760000.00' },
    to: { minorUnits: 79800000, currency: 'INR', amount: '798000.00' }, amount: null, bandBefore: 'BELOW', bandAfter: 'WITHIN',
  }],
  skipped: [{ reason: 'Joined after 2026-10-17', count: 1, examples: ['Ann Rao'] }],
};

describe('BulkChangeDialog', () => {
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(() => {
    close.mockReset();
    TestBed.configureTestingModule({
      imports: [BulkChangeDialog],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: DATA },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(BulkChangeDialog);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const input = (label: string) => [...el.querySelectorAll('mat-form-field')]
      .find((f) => f.textContent?.includes(label))!.querySelector('input') as HTMLInputElement;
    const button = (text: string) => [...el.querySelectorAll('button')].find((b) => b.textContent?.includes(text)) as HTMLButtonElement;
    return { fixture, el, input, button };
  }

  it('previews with the directory filters, then applies once with the preview request id', () => {
    const { fixture, el, input, button } = render();
    const percent = input('Raise');
    percent.value = '5';
    percent.dispatchEvent(new Event('input'));
    button('Preview').click();

    const preview = http.expectOne((r) => r.url === '/api/bulk-changes/preview');
    expect(preview.request.params.getAll('country')).toEqual(['IN']);
    expect(preview.request.body).toMatchObject({ kind: 'RAISE', percent: '5', reason: 'MERIT' });
    preview.flush(PREVIEW);
    fixture.detectChanges();

    expect(el.textContent).toContain('1 skipped');
    expect(el.textContent).toContain('Joined after 2026-10-17');
    expect(el.textContent).toMatch(/Tom Agarwal.*→ within/);

    button('Apply to 1 person').click();
    fixture.detectChanges();
    button('Applying').click();
    const apply = http.expectOne('/api/bulk-changes?country=IN&status=ACTIVE');
    expect(apply.request.body.requestId).toMatch(/^[0-9a-f-]{36}$/);
    apply.flush({ requestId: apply.request.body.requestId, kind: 'RAISE', applied: 1, skipped: 1, alreadyApplied: false });
    http.verify();
    expect(close).toHaveBeenCalledWith(expect.objectContaining({ applied: 1 }));
  });

  it('discards the preview when an input changes, so what is applied is what was seen', () => {
    const { fixture, el, input, button } = render();
    const percent = input('Raise');
    percent.value = '5';
    percent.dispatchEvent(new Event('input'));
    button('Preview').click();
    http.expectOne((r) => r.url === '/api/bulk-changes/preview').flush(PREVIEW);
    fixture.detectChanges();

    percent.value = '6';
    percent.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(button('Apply')).toBeUndefined();
    expect(el.textContent).not.toContain('Tom Agarwal');
  });

  it('shows invalid input without calling the API', () => {
    const { fixture, el, button } = render();
    button('Preview').click();
    fixture.detectChanges();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Enter a percentage');
    http.verify();
  });
});
