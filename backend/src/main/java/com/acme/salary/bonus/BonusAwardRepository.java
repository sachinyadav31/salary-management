package com.acme.salary.bonus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BonusAwardRepository extends JpaRepository<BonusAward, Long> {

    List<BonusAward> findByEmployeeIdOrderByAwardedOnDescIdDesc(Long employeeId);
}
