import { HttpParams } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';

import { EmployeeApi } from '../../core/api/employee-api';
import { EmployeeSummary } from '../../core/api/models';
import { countryFlag } from '../../shared/flag';
import { MoneyPipe } from '../../shared/money';
import { openAddEmployee } from '../employees/add/add-employee-dialog';
import { COMMANDS, MIN_SEARCH, PaletteCommand, matchCommands, moveActive } from './palette';

type Item = { kind: 'person'; person: EmployeeSummary } | { kind: 'command'; command: PaletteCommand };

interface PeopleState {
  loading: boolean;
  people: EmployeeSummary[];
}

/**
 * ⌘K: find anyone by name, email or employee code, or jump to a page or action. People come from the directory
 * search API (so it searches all 10,000, including inactive), commands are filtered locally. Keyboard-first:
 * arrows move, Enter opens, Esc closes.
 */
@Component({
  selector: 'pa-command-palette',
  imports: [MatDialogModule, MatIconModule, MoneyPipe],
  templateUrl: './command-palette.html',
  styleUrl: './command-palette.scss',
})
export class CommandPalette {
  private readonly api = inject(EmployeeApi);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly ref = inject(MatDialogRef<CommandPalette>);

  protected readonly flag = countryFlag;
  protected readonly query = signal('');
  protected readonly active = signal(0);

  private readonly peopleState = toSignal(
    toObservable(this.query).pipe(
      map((q) => q.trim()),
      debounceTime(150),
      distinctUntilChanged(),
      switchMap((q) =>
        q.length < MIN_SEARCH
          ? of<PeopleState>({ loading: false, people: [] })
          : this.api.search(new HttpParams().set('search', q).set('size', 8).set('sort', 'name,asc')).pipe(
              map((page): PeopleState => ({ loading: false, people: page.content })),
              catchError(() => of<PeopleState>({ loading: false, people: [] })),
              startWith<PeopleState>({ loading: true, people: [] }),
            ),
      ),
    ),
    { initialValue: { loading: false, people: [] } as PeopleState },
  );

  protected readonly loading = computed(() => this.peopleState().loading);
  protected readonly commands = computed(() => matchCommands(COMMANDS, this.query()));
  protected readonly people = computed(() => this.peopleState().people);

  /** People first when searching (that's usually why the palette is open), then commands. */
  protected readonly items = computed<Item[]>(() => [
    ...this.people().map((person): Item => ({ kind: 'person', person })),
    ...this.commands().map((command): Item => ({ kind: 'command', command })),
  ]);

  protected readonly activeId = computed(() => (this.items().length ? `palette-item-${this.active()}` : null));
  protected readonly searching = computed(() => this.query().trim().length >= MIN_SEARCH);

  protected onInput(value: string): void {
    this.query.set(value);
    this.active.set(0);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.active.update((i) => moveActive(i, event.key === 'ArrowDown' ? 1 : -1, this.items().length));
      document.getElementById(`palette-item-${this.active()}`)?.scrollIntoView?.({ block: 'nearest' });
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const item = this.items()[this.active()];
      if (item) this.choose(item);
    }
  }

  protected choose(item: Item): void {
    this.ref.close();
    if (item.kind === 'person') {
      this.router.navigate(['/employees', item.person.id]);
    } else if (item.command.action === 'add-employee') {
      openAddEmployee(this.dialog, this.router, this.api);
    } else {
      this.router.navigate([item.command.path], { queryParams: item.command.queryParams });
    }
  }

  protected indexOf(item: Item): number {
    return this.items().indexOf(item);
  }
}

/** Opens the palette unless it (or another dialog) is already open. */
export function openCommandPalette(dialog: MatDialog): void {
  if (dialog.openDialogs.length) return;
  dialog.open(CommandPalette, {
    width: '600px',
    maxWidth: 'calc(100vw - 32px)',
    position: { top: '12vh' },
    panelClass: 'pa-palette',
    autoFocus: 'first-tabbable',
    ariaLabel: 'Search people and commands',
  });
}
