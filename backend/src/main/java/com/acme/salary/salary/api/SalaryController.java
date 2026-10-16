package com.acme.salary.salary.api;

import com.acme.salary.common.NotFoundException;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.salary.SalaryService;
import com.acme.salary.salary.api.SalaryApiModels.RecordSalaryChangeRequest;
import com.acme.salary.salary.api.SalaryApiModels.SalaryChangeResponse;
import com.acme.salary.salary.api.SalaryApiModels.SalaryHistoryItem;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}")
public class SalaryController {

    private final SalaryService salaryService;
    private final EmployeeQueryRepository employeeQueries;

    public SalaryController(SalaryService salaryService, EmployeeQueryRepository employeeQueries) {
        this.salaryService = salaryService;
        this.employeeQueries = employeeQueries;
    }

    /** Newest first, each with status (CURRENT / PAST / SCHEDULED) and % change from the previous entry. */
    @GetMapping("/salary-history")
    public List<SalaryHistoryItem> history(@PathVariable long employeeId) {
        return salaryService.timeline(employeeId).stream().map(SalaryHistoryItem::from).toList();
    }

    @PostMapping("/salary-changes")
    public ResponseEntity<SalaryChangeResponse> recordChange(@PathVariable long employeeId,
                                                             @Valid @RequestBody RecordSalaryChangeRequest request) {
        salaryService.recordChange(employeeId, request.toCommand());
        var employee = employeeQueries.findDetail(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee " + employeeId + " not found"));
        return ResponseEntity.created(URI.create("/api/employees/" + employeeId + "/salary-history"))
                .body(new SalaryChangeResponse(employee, history(employeeId)));
    }
}
