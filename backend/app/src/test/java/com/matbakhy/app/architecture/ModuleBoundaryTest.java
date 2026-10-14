package com.matbakhy.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** TC-106: module boundaries hold for production code and the rules fail on a violating fixture. */
class ModuleBoundaryTest {

    private static final ModuleRules PRODUCTION = new ModuleRules("com.matbakhy", Set.of("app", "shared"));
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.matbakhy");

    private static final ModuleRules FIXTURE = new ModuleRules("com.matbakhy.archfixture", Set.of());
    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages("com.matbakhy.archfixture");

    @Test
    void every_module_is_declared_and_imported() {
        java.util.Set<String> declared = PRODUCTION_CLASSES.stream()
                .filter(c -> c.getSimpleName().equals("package-info"))
                .filter(c -> c.isAnnotatedWith(com.matbakhy.shared.module.DomainModule.class))
                .peek(c -> assertThat(c.getAnnotationOfType(com.matbakhy.shared.module.DomainModule.class).value())
                        .as("module name matches package").isEqualTo(PRODUCTION.moduleOf(c).orElseThrow()))
                .map(c -> PRODUCTION.moduleOf(c).orElseThrow())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrder("identity", "cooks", "catalog", "availability", "orders",
                "finance", "support", "notifications", "operations");
    }

    @Test
    void production_modules_only_cross_through_api() {
        PRODUCTION.onlyPublicApiCrossesModules().check(PRODUCTION_CLASSES);
    }

    @Test
    void production_modules_are_free_of_cycles() {
        PRODUCTION.modulesAreFreeOfCycles().check(PRODUCTION_CLASSES);
    }

    @Test
    void shared_depends_on_no_module_or_app() {
        PRODUCTION.sharedDependsOnNoModuleOrApp("shared", "app").check(PRODUCTION_CLASSES);
    }

    @Test
    void modules_do_not_depend_on_the_composition_layer() {
        PRODUCTION.modulesDoNotDependOnApp("app").check(PRODUCTION_CLASSES);
    }

    @Test
    void forbidden_internal_dependency_in_fixture_is_detected() {
        EvaluationResult result = FIXTURE.onlyPublicApiCrossesModules().evaluate(FIXTURE_CLASSES);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(d -> d.contains("finance.internal.Leak") && d.contains("orders.internal.Secret"))
                .as("using another module's api package is allowed and must not be reported")
                .allMatch(d -> d.contains("orders.internal.Secret"));
    }

    @Test
    void module_cycle_in_fixture_is_detected() {
        assertThat(FIXTURE.modulesAreFreeOfCycles().evaluate(FIXTURE_CLASSES).hasViolation()).isTrue();
    }
}
