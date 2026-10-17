package com.acme.salary.employee.api;

import com.acme.salary.employee.EmployeeCommands.CreateEmployee;
import com.acme.salary.employee.EmployeeCommands.UpdateProfile;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.reference.JobLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** HTTP request bodies. Bean Validation checks shape; business rules live in the service. */
public final class EmployeeRequests {

    private EmployeeRequests() {
    }

    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO country code like IN") String countryCode,
            @NotNull Long departmentId,
            @NotBlank @Size(max = 100) String jobTitle,
            @NotNull JobLevel level,
            @NotNull LocalDate hireDate,
            @NotNull @Positive BigDecimal salaryAmount) {

        CreateEmployee toCommand() {
            return new CreateEmployee(firstName, lastName, email, countryCode, departmentId, jobTitle, level,
                    hireDate, salaryAmount);
        }
    }

    public record UpdateEmployeeRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 200) String email,
            @NotNull Long departmentId,
            @NotBlank @Size(max = 100) String jobTitle,
            @NotNull JobLevel level,
            @NotNull Long version) {

        UpdateProfile toCommand() {
            return new UpdateProfile(firstName, lastName, email, departmentId, jobTitle, level, version);
        }
    }

    public record ChangeStatusRequest(@NotNull EmployeeStatus status, @NotNull Long version) {
    }
}
