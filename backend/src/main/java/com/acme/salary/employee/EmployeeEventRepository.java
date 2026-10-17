package com.acme.salary.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeEventRepository extends JpaRepository<EmployeeEvent, Long> {

    /** Newest first; changes from one edit (same instant) keep the order they were recorded in. */
    List<EmployeeEvent> findByEmployeeIdOrderByOccurredAtDescIdAsc(Long employeeId);
}
