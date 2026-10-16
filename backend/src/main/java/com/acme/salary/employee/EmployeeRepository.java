package com.acme.salary.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Highest numeric part of codes like E00042. SQLite's single writer makes max+1 safe inside a transaction. */
    @Query(value = "select coalesce(max(cast(substr(employee_code, 2) as integer)), 0) from employee", nativeQuery = true)
    long maxEmployeeNumber();
}
