import { Routes } from '@angular/router';
import { canLeaveGuard } from './shared/can-leave';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'insights' },
  {
    path: 'insights',
    title: 'Insights · Pay Atlas',
    data: { heading: 'Pay insights', subheading: 'How ACME pays its people, across every country' },
    loadComponent: () => import('./features/insights/insights-page').then((m) => m.InsightsPage),
  },
  {
    path: 'employees',
    title: 'Employees · Pay Atlas',
    data: { heading: 'Employees', subheading: 'Search, filter and compare pay against bands' },
    loadComponent: () => import('./features/employees/employees-page').then((m) => m.EmployeesPage),
    children: [
      {
        path: ':id',
        title: 'Employee · Pay Atlas',
        loadComponent: () => import('./features/employees/detail/employee-drawer').then((m) => m.EmployeeDrawer),
        canDeactivate: [canLeaveGuard],
      },
    ],
  },
  { path: '**', redirectTo: 'insights' },
];
