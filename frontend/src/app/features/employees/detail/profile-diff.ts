import { EmployeeDetail } from '../../../core/api/models';
import { formatMoney } from '../../../shared/money';

export interface FieldChange {
  field: string;
  from: string;
  to: string;
}

const FIELDS: { label: string; value: (e: EmployeeDetail) => string }[] = [
  { label: 'Name', value: (e) => e.fullName },
  { label: 'Email', value: (e) => e.email },
  { label: 'Department', value: (e) => e.department },
  { label: 'Job title', value: (e) => e.jobTitle },
  { label: 'Level', value: (e) => e.level },
  { label: 'Status', value: (e) => (e.status === 'ACTIVE' ? 'Active' : 'Inactive') },
  { label: 'Salary', value: (e) => formatMoney(e.salary) },
];

/** What someone else changed between the copy the user was editing and the latest one (for the 409 banner). */
export function profileChanges(before: EmployeeDetail, after: EmployeeDetail): FieldChange[] {
  return FIELDS.map(({ label, value }) => ({ field: label, from: value(before), to: value(after) }))
    .filter((change) => change.from !== change.to);
}
