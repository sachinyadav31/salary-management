import { DEFAULT_QUERY, fromQueryParams, hasFilters, toApiParams, toQueryParams } from './directory-query';

describe('directory query', () => {
  it('defaults to active employees sorted by name, 25 per page', () => {
    expect(fromQueryParams({})).toEqual(DEFAULT_QUERY);
    expect(toApiParams(DEFAULT_QUERY).toString()).toBe('status=ACTIVE&sort=name,asc&page=0&size=25');
  });

  it('round-trips every filter through the URL', () => {
    const query = {
      ...DEFAULT_QUERY,
      search: 'sharma',
      countries: ['IN', 'JP'],
      departments: [1, 8],
      levels: ['L3' as const],
      title: 'Software Engineer',
      status: 'ALL' as const,
      bandPosition: 'BELOW' as const,
      compaMin: 0.5,
      compaMax: 0.85,
      sort: 'compaRatio,desc',
      page: 3,
      size: 50,
    };
    expect(fromQueryParams(toQueryParams(query))).toEqual(query);
  });

  it('keeps URLs short by omitting defaults', () => {
    expect(toQueryParams(DEFAULT_QUERY)).toEqual({});
    expect(toQueryParams({ ...DEFAULT_QUERY, countries: ['IN'], page: 2 })).toEqual({ country: ['IN'], page: '2' });
  });

  it('accepts an outlier card filter as-is', () => {
    const cardFilter = { country: 'IN', title: 'Operations Analyst', bandPosition: 'BELOW', sort: 'compaRatio,asc' };

    const query = fromQueryParams(cardFilter);

    expect(query).toMatchObject({ countries: ['IN'], title: 'Operations Analyst', bandPosition: 'BELOW', sort: 'compaRatio,asc' });
    expect(toApiParams(query).toString())
      .toBe('country=IN&title=Operations%20Analyst&status=ACTIVE&bandPosition=BELOW&sort=compaRatio,asc&page=0&size=25');
  });

  it('ignores malformed or unknown values from hand-edited URLs', () => {
    const query = fromQueryParams({
      country: ['IN', 'india', 'xx'], level: ['L3', 'L9'], department: ['2', 'abc', '-1'], status: 'FIRED',
      bandPosition: 'SIDEWAYS', compaMax: 'cheap', sort: 'password,desc', page: '-4', size: '1000',
    });

    expect(query).toEqual({ ...DEFAULT_QUERY, countries: ['IN'], levels: ['L3'], departments: [2] });
  });

  it('drops paging for export and the status filter for ALL', () => {
    const params = toApiParams({ ...DEFAULT_QUERY, status: 'ALL', page: 4 }, { paged: false });
    expect(params.toString()).toBe('sort=name,asc');
  });

  it('knows when a view is filtered', () => {
    expect(hasFilters(DEFAULT_QUERY)).toBe(false);
    expect(hasFilters({ ...DEFAULT_QUERY, sort: 'salaryUsd,desc', page: 3 })).toBe(false);
    expect(hasFilters({ ...DEFAULT_QUERY, compaMax: 0.85 })).toBe(true);
  });
});
