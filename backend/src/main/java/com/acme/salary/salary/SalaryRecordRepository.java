package com.acme.salary.salary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {

    List<SalaryRecord> findByEmployeeIdOrderByEffectiveDateDescIdDesc(Long employeeId);
}
