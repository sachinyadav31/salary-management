package com.acme.salary.employee.api;

import com.acme.salary.common.NotFoundException;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.api.EmployeeRequests.ChangeStatusRequest;
import com.acme.salary.employee.api.EmployeeRequests.CreateEmployeeRequest;
import com.acme.salary.employee.api.EmployeeRequests.UpdateEmployeeRequest;
import com.acme.salary.employee.query.EmployeeDetail;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.employee.query.EmployeeSearchCriteria;
import com.acme.salary.employee.query.EmployeeSortField;
import com.acme.salary.employee.query.EmployeeSummary;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeQueryRepository queries;
    private final EmployeeService service;

    public EmployeeController(EmployeeQueryRepository queries, EmployeeService service) {
        this.queries = queries;
        this.service = service;
    }

    /**
     * Paged directory. Repeat a parameter to filter by several values, e.g. {@code ?country=IN&country=JP}.
     * Sort is {@code field[,asc|desc]} with field one of name, employeeCode, country, department, jobTitle,
     * level, hireDate, salaryUsd.
     */
    @GetMapping
    public PageResponse<EmployeeSummary> search(
            @RequestParam(required = false) String search,
            @RequestParam(name = "country", required = false) List<String> countries,
            @RequestParam(name = "department", required = false) List<Long> departmentIds,
            @RequestParam(name = "title", required = false) String jobTitle,
            @RequestParam(name = "level", required = false) List<JobLevel> levels,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + EmployeeSearchCriteria.DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort) {
        return queries.search(EmployeeSearchCriteria.builder()
                .search(search)
                .countries(countries)
                .departmentIds(departmentIds)
                .jobTitle(jobTitle)
                .levels(levels)
                .status(status)
                .page(page)
                .size(size)
                .sort(EmployeeSortField.parse(sort))
                .build());
    }

    @GetMapping("/{id}")
    public EmployeeDetail get(@PathVariable long id) {
        return queries.findDetail(id).orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
    }

    /** Commands go through the service; the response is read back from the query side. */
    @PostMapping
    public ResponseEntity<EmployeeDetail> create(@Valid @RequestBody CreateEmployeeRequest request) {
        long id = service.create(request.toCommand());
        return ResponseEntity.created(URI.create("/api/employees/" + id)).body(get(id));
    }

    @PutMapping("/{id}")
    public EmployeeDetail update(@PathVariable long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        service.updateProfile(id, request.toCommand());
        return get(id);
    }

    @PatchMapping("/{id}/status")
    public EmployeeDetail changeStatus(@PathVariable long id, @Valid @RequestBody ChangeStatusRequest request) {
        service.changeStatus(id, request.status(), request.version());
        return get(id);
    }
}
