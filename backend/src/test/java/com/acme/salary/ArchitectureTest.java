package com.acme.salary;

import com.acme.salary.bonus.BonusRules;
import com.acme.salary.bulk.BulkPlanner;
import com.acme.salary.common.Money;
import com.acme.salary.employee.CareerChanges;
import com.acme.salary.employee.VariableTarget;
import com.acme.salary.common.csv.CsvWriter;
import com.acme.salary.insights.Histogram;
import com.acme.salary.insights.InsightsCalculator;
import com.acme.salary.insights.OutlierDetector;
import com.acme.salary.insights.PayStatistics;
import com.acme.salary.payband.CompaRatio;
import com.acme.salary.reference.CurrencyConverter;
import com.acme.salary.salary.SalaryHistory;
import com.acme.salary.seed.EmployeeSeedGenerator;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable architecture rules: the build fails if the design erodes.
 */
@AnalyzeClasses(packages = "com.acme.salary", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule featuresHaveNoDependencyCycles = slices()
            .matching("com.acme.salary.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule domainLogicIsFrameworkFree = noClasses()
            .that().belongToAnyOf(Money.class, CurrencyConverter.class, SalaryHistory.class, PayStatistics.class,
                    Histogram.class, InsightsCalculator.class, OutlierDetector.class, CompaRatio.class,
                    EmployeeSeedGenerator.class, CsvWriter.class, BonusRules.class,
                    VariableTarget.class, CareerChanges.class, BulkPlanner.class)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                    "org.hibernate..")
            .because("business rules must be unit-testable without starting Spring or a database");

    @ArchTest
    static final ArchRule controllersDoNotUseJpaDirectly = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .orShould().dependOnClassesThat().areAssignableTo(EntityManager.class)
            .because("writes go through services; reads go through query repositories (CQRS-lite)");

    @ArchTest
    static final ArchRule entitiesDoNotLeakIntoTheApi = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("the HTTP contract must not change when the persistence model does");

    @ArchTest
    static final ArchRule nothingDependsOnTheWebLayer = noClasses()
            .that().resideOutsideOfPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule controllersLiveInApiPackages = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule noFloatingPointOutsideSeedData = noFields()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..seed..")
            .should().haveRawType(double.class)
            .orShould().haveRawType(float.class)
            .orShould().haveRawType(Double.class)
            .orShould().haveRawType(Float.class)
            .because("money is integer minor units; floating point would introduce rounding errors");
}
