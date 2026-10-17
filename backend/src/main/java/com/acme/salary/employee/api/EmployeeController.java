package com.acme.salary.employee.api;

import com.acme.salary.common.NotFoundException;
import com.acme.salary.common.csv.CsvWriter;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.api.EmployeeRequests.ChangeStatusRequest;
import com.acme.salary.employee.api.EmployeeRequests.CreateEmployeeRequest;
import com.acme.salary.employee.api.EmployeeRequests.UpdateEmployeeRequest;
import com.acme.salary.employee.query.EmployeeDetail;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.employee.query.EmployeeSearchCriteria;
import com.acme.salary.employee.query.EmployeeSummary;
import jakarta.servlet.http.HttpServletResponse;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

@RestController
@Tag(name = "Employees", description = "Directory, profiles and status")
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeQueryRepository queries;
    private final EmployeeService service;
    private final Clock clock;

    public EmployeeController(EmployeeQueryRepository queries, EmployeeService service, Clock clock) {
        this.queries = queries;
        this.service = service;
        this.clock = clock;
    }

    /**
     * Paged directory. Sort is {@code field[,asc|desc]} with field one of name, employeeCode, country,
     * department, jobTitle, level, hireDate, salaryUsd, compaRatio. {@code compaMax=0.85} finds people paid
     * 15%+ below band midpoint.
     */
    @Operation(summary = "Search the directory (paged, filtered, sorted)")
    @GetMapping
    public PageResponse<EmployeeSummary> search(@ParameterObject EmployeeFilterParams filter,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "" + EmployeeSearchCriteria.DEFAULT_SIZE) int size) {
        return queries.search(filter.toCriteria().page(page).size(size).build());
    }

    /**
     * Same filters and sort as the directory, all matching rows, streamed as CSV (UTF-8 with BOM for Excel).
     * What you see is what you export: both endpoints share one filter definition and one query builder.
     */
    @Operation(summary = "Export the filtered directory as CSV")
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public void export(@ParameterObject EmployeeFilterParams filter, HttpServletResponse response) throws IOException {
        EmployeeSearchCriteria criteria = filter.toCriteria().build();

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("employees-" + LocalDate.now(clock) + ".csv").build().toString());
        var writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        writer.write('\uFEFF'); // BOM: makes Excel read the file as UTF-8 (names like Müller, 佐藤)
        CsvWriter csv = new CsvWriter(writer);
        EmployeeCsvFormat.writeHeader(csv);
        queries.stream(criteria, row -> EmployeeCsvFormat.writeRow(csv, row));
        csv.flush();
    }

    @Operation(summary = "Get one employee (includes version for edits)")
    @GetMapping("/{id}")
    public EmployeeDetail get(@PathVariable long id) {
        return queries.findDetail(id).orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
    }

    /** Commands go through the service; the response is read back from the query side. */
    @Operation(summary = "Hire an employee (records the HIRE salary)")
    @PostMapping
    public ResponseEntity<EmployeeDetail> create(@Valid @RequestBody CreateEmployeeRequest request) {
        long id = service.create(request.toCommand());
        return ResponseEntity.created(URI.create("/api/employees/" + id)).body(get(id));
    }

    @Operation(summary = "Update an employee profile")
    @PutMapping("/{id}")
    public EmployeeDetail update(@PathVariable long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        service.updateProfile(id, request.toCommand());
        return get(id);
    }

    @Operation(summary = "Deactivate or reactivate an employee")
    @PatchMapping("/{id}/status")
    public EmployeeDetail changeStatus(@PathVariable long id, @Valid @RequestBody ChangeStatusRequest request) {
        service.changeStatus(id, request.status(), request.version());
        return get(id);
    }
}
