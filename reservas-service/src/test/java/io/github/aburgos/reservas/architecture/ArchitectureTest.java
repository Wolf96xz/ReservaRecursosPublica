package io.github.aburgos.reservas.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Regla de dependencias: el dominio no conoce ni la aplicación ni la infraestructura ni Spring/Kafka.
 * JPA (jakarta.persistence) está permitido en el dominio por pragmatismo.
 */
@AnalyzeClasses(packages = "io.github.aburgos.reservas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dominioNoDependeDeAplicacionNiInfraestructura =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule dominioNoDependeDeSpringNiKafka =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.apache.kafka..");
}
