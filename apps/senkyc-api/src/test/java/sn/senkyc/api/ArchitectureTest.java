package sn.senkyc.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Règles de l'architecture hexagonale pragmatique (voir docs/adr/0001-monolithe-modulaire-hexagonal.md). */
@AnalyzeClasses(packages = "sn.senkyc.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "com.fasterxml..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule platformDoesNotDependOnBusinessModules = noClasses()
            .that()
            .resideInAPackage("sn.senkyc.api.platform..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("sn.senkyc.api.tenant..");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotTouchOutboundAdapters = noClasses()
            .that()
            .resideInAPackage("..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter.out..");

    @ArchTest
    static final ArchRule registryRoutingIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("sn.senkyc.api.registry.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..");

    @ArchTest
    static final ArchRule storagePortIsProviderNeutral = noClasses()
            .that()
            .resideInAPackage("sn.senkyc.api.storage.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("software.amazon..", "org.springframework..");
}
