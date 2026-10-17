import { HttpParams } from '@angular/common/http';
import { Params } from '@angular/router';
import { BandPosition, EmployeeStatus, JobLevel } from '../../core/api/models';

/**
 * Everything that defines a directory view. The URL is the source of truth: filters, sort and page live in
 * query params, so views can be bookmarked, shared and navigated with Back. Param names match the API's,
 * so outlier cards' `filter` objects deep-link here unchanged.
 */
export interface DirectoryQuery {
  search: string;
  countries: string[];
  departments: number[];
  levels: JobLevel[];
  title: string | null;
  /** ACTIVE by default (what HR looks at day to day); ALL means no status filter. */
  status: EmployeeStatus | 'ALL';
  bandPosition: BandPosition | null;
  compaMin: number | null;
  compaMax: number | null;
  sort: string;
  page: number;
  size: number;
}

export const PAGE_SIZES = [10, 25, 50, 100] as const;
const LEVELS: readonly JobLevel[] = ['L1', 'L2', 'L3', 'L4', 'L5', 'L6'];
const POSITIONS: readonly BandPosition[] = ['BELOW', 'WITHIN', 'ABOVE'];
const SORT_FIELDS = ['name', 'employeeCode', 'country', 'department', 'jobTitle', 'level', 'hireDate', 'salaryUsd', 'compaRatio'];

export const DEFAULT_QUERY: DirectoryQuery = {
  search: '',
  countries: [],
  departments: [],
  levels: [],
  title: null,
  status: 'ACTIVE',
  bandPosition: null,
  compaMin: null,
  compaMax: null,
  sort: 'name,asc',
  page: 0,
  size: 25,
};

/** Tolerant parsing: anything unknown or malformed in a hand-edited URL falls back to the default. */
export function fromQueryParams(params: Params): DirectoryQuery {
  const list = (key: string): string[] => {
    const value = params[key];
    return value === undefined ? [] : Array.isArray(value) ? value.map(String) : [String(value)];
  };
  const text = (key: string): string | null => {
    const value = list(key)[0]?.trim();
    return value ? value : null;
  };
  const decimal = (key: string): number | null => {
    const value = Number(text(key));
    return text(key) !== null && Number.isFinite(value) && value >= 0 ? value : null;
  };
  const status = text('status');
  const sort = text('sort');
  const size = Number(text('size'));
  const page = Number(text('page'));

  return {
    search: text('search') ?? '',
    countries: list('country').filter((c) => /^[A-Z]{2}$/.test(c)),
    departments: list('department').map(Number).filter((d) => Number.isInteger(d) && d > 0),
    levels: list('level').filter((l): l is JobLevel => LEVELS.includes(l as JobLevel)),
    title: text('title'),
    status: status === 'INACTIVE' || status === 'ALL' ? status : 'ACTIVE',
    bandPosition: POSITIONS.find((p) => p === text('bandPosition')) ?? null,
    compaMin: decimal('compaMin'),
    compaMax: decimal('compaMax'),
    sort: sort && SORT_FIELDS.includes(sort.split(',')[0]) ? normaliseSort(sort) : DEFAULT_QUERY.sort,
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: (PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_QUERY.size,
  };
}

/** Only non-default values, so URLs stay short and readable. Arrays become repeated params. */
export function toQueryParams(query: DirectoryQuery): Params {
  const params: Params = {};
  if (query.search) params['search'] = query.search;
  if (query.countries.length) params['country'] = query.countries;
  if (query.departments.length) params['department'] = query.departments.map(String);
  if (query.levels.length) params['level'] = query.levels;
  if (query.title) params['title'] = query.title;
  if (query.status !== DEFAULT_QUERY.status) params['status'] = query.status;
  if (query.bandPosition) params['bandPosition'] = query.bandPosition;
  if (query.compaMin !== null) params['compaMin'] = String(query.compaMin);
  if (query.compaMax !== null) params['compaMax'] = String(query.compaMax);
  if (query.sort !== DEFAULT_QUERY.sort) params['sort'] = query.sort;
  if (query.page > 0) params['page'] = String(query.page);
  if (query.size !== DEFAULT_QUERY.size) params['size'] = String(query.size);
  return params;
}

/** Backend params for search and export (export ignores page/size). */
export function toApiParams(query: DirectoryQuery, { paged = true } = {}): HttpParams {
  let params = new HttpParams();
  const add = (key: string, value: string | number) => (params = params.append(key, String(value)));

  if (query.search) add('search', query.search);
  query.countries.forEach((c) => add('country', c));
  query.departments.forEach((d) => add('department', d));
  query.levels.forEach((l) => add('level', l));
  if (query.title) add('title', query.title);
  if (query.status !== 'ALL') add('status', query.status);
  if (query.bandPosition) add('bandPosition', query.bandPosition);
  if (query.compaMin !== null) add('compaMin', query.compaMin);
  if (query.compaMax !== null) add('compaMax', query.compaMax);
  add('sort', query.sort);
  if (paged) {
    add('page', query.page);
    add('size', query.size);
  }
  return params;
}

/** True when any filter (not sort/paging) differs from the default view. */
export function hasFilters(query: DirectoryQuery): boolean {
  return Object.keys(toQueryParams({ ...query, sort: DEFAULT_QUERY.sort, page: 0, size: DEFAULT_QUERY.size })).length > 0;
}

function normaliseSort(sort: string): string {
  const [field, direction] = sort.split(',');
  return `${field},${direction === 'desc' ? 'desc' : 'asc'}`;
}
