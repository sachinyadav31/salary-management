import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, scan, startWith, switchMap } from 'rxjs';

import { EmployeeApi } from '../../core/api/employee-api';
import { MetaApi } from '../../core/api/meta-api';
import { EmployeeSummary, PageResponse } from '../../core/api/models';
import { BandBar } from '../../shared/band-bar';
import { CompaBadge } from '../../shared/compa-badge';
import { countryFlag } from '../../shared/flag';
import { MoneyPipe } from '../../shared/money';
import { DEFAULT_QUERY, DirectoryQuery, PAGE_SIZES, fromQueryParams, hasFilters, toApiParams, toQueryParams } from './directory-query';

interface LoadState {
  loading: boolean;
  failed: boolean;
  /** Last successful page; kept while the next one loads so the table doesn't flicker. */
  page: PageResponse<EmployeeSummary> | null;
}

type QuickFilterId = 'all' | 'below' | 'above' | 'underpaid';

const QUICK_FILTERS: { id: QuickFilterId; label: string; patch: Partial<DirectoryQuery> }[] = [
  { id: 'all', label: 'Everyone', patch: { bandPosition: null, compaMin: null, compaMax: null } },
  { id: 'below', label: 'Below band', patch: { bandPosition: 'BELOW', compaMin: null, compaMax: null } },
  { id: 'above', label: 'Above band', patch: { bandPosition: 'ABOVE', compaMin: null, compaMax: null } },
  { id: 'underpaid', label: 'Underpaid (< 0.85)', patch: { bandPosition: null, compaMin: null, compaMax: 0.85 } },
];

@Component({
  selector: 'pa-employees-page',
  imports: [
    DecimalPipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatSortModule, MatTableModule, MatTooltipModule,
    BandBar, CompaBadge, MoneyPipe,
  ],
  templateUrl: './employees-page.html',
  styleUrl: './employees-page.scss',
})
export class EmployeesPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(EmployeeApi);

  protected readonly meta = toSignal(inject(MetaApi).meta$);
  protected readonly query = toSignal(this.route.queryParams.pipe(map(fromQueryParams)), { initialValue: DEFAULT_QUERY });

  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      switchMap((query) =>
        this.api.search(toApiParams(query)).pipe(
          map((page): LoadState => ({ loading: false, failed: false, page })),
          catchError(() => of<LoadState>({ loading: false, failed: true, page: null })),
          startWith<LoadState>({ loading: true, failed: false, page: null }),
        ),
      ),
      scan((previous, next) => ({ ...next, page: next.page ?? (next.failed ? null : previous.page) })),
    ),
    { initialValue: { loading: true, failed: false, page: null } as LoadState },
  );

  protected readonly rows = computed(() => this.state().page?.content ?? []);
  protected readonly total = computed(() => this.state().page?.totalElements ?? 0);
  protected readonly filtered = computed(() => hasFilters(this.query()));
  protected readonly exportUrl = computed(() => this.api.exportUrl(toApiParams(this.query(), { paged: false })));
  protected readonly sortField = computed(() => this.query().sort.split(',')[0]);
  protected readonly sortDirection = computed(() => this.query().sort.split(',')[1] as 'asc' | 'desc');

  protected readonly quickFilters = QUICK_FILTERS;
  protected readonly activeQuickFilter = computed<QuickFilterId | null>(() => {
    const { bandPosition, compaMin, compaMax } = this.query();
    if (compaMin !== null) return null;
    if (compaMax === null) return bandPosition === 'BELOW' ? 'below' : bandPosition === 'ABOVE' ? 'above' : bandPosition ? null : 'all';
    return compaMax === 0.85 && !bandPosition ? 'underpaid' : null;
  });

  protected readonly pageSizes = PAGE_SIZES;
  protected readonly columns = ['employee', 'country', 'role', 'salary', 'band', 'compaRatio', 'status'];
  protected readonly skeletonRows = Array.from({ length: 8 }, (_, i) => i);
  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly flag = countryFlag;

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(300), map((value) => value.trim()), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((search) => this.update({ search }, { replaceUrl: true }));

    // Keep the box in sync when the URL changes from outside (Back button, deep links, Clear).
    effect(() => {
      const search = this.query().search;
      if (this.searchControl.value.trim() !== search) {
        this.searchControl.setValue(search, { emitEvent: false });
      }
    });
  }

  /** Every interaction becomes a URL change; the URL drives loading. Filter changes go back to page 1. */
  protected update(patch: Partial<DirectoryQuery>, options: { keepPage?: boolean; replaceUrl?: boolean } = {}): void {
    const next = { ...this.query(), ...patch, ...(options.keepPage ? {} : { page: 0 }) };
    this.router.navigate([], { relativeTo: this.route, queryParams: toQueryParams(next), replaceUrl: options.replaceUrl });
  }

  protected onSort(sort: Sort): void {
    this.update({ sort: sort.direction ? `${sort.active},${sort.direction}` : DEFAULT_QUERY.sort });
  }

  protected onPage(event: PageEvent): void {
    this.update({ page: event.pageIndex, size: event.pageSize }, { keepPage: true });
  }

  protected clearFilters(): void {
    this.update({ ...DEFAULT_QUERY, sort: this.query().sort, size: this.query().size });
  }

  protected readonly trackById = (_: number, employee: EmployeeSummary) => employee.id;

  protected initials(employee: EmployeeSummary): string {
    return (employee.firstName[0] + employee.lastName[0]).toUpperCase();
  }

  /** Stable, distinct avatar colour per person. */
  protected avatarHue(employee: EmployeeSummary): number {
    let hash = 0;
    for (const char of employee.employeeCode) {
      hash = (hash * 31 + char.charCodeAt(0)) % 360;
    }
    return hash;
  }
}
