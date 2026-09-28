package dev.example.payments;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

/** Gate: architecture. Controllers talk to services, services talk to repositories. */
@AnalyzeClasses(packages = "dev.example.payments", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotReachRepositories = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAnnotatedWith(Repository.class);

    @ArchTest
    static final ArchRule repositoriesDoNotReachUpwards = noClasses()
            .that().areAnnotatedWith(Repository.class)
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Service")
            .orShould().dependOnClassesThat().areAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule noPackageCycles = slices()
            .matching("dev.example.payments.(*)..")
            .should().beFreeOfCycles();
}
