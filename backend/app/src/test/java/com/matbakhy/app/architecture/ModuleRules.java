package com.matbakhy.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import java.util.Set;

/**
 * Module boundary rules (ADR-001), parameterized by root package so the same rules can be shown to
 * fail on deliberately violating fixtures.
 */
final class ModuleRules {

    private final String root;
    private final Set<String> nonModules;

    ModuleRules(String root, Set<String> nonModules) {
        this.root = root;
        this.nonModules = nonModules;
    }

    /** Name of the module owning a class, or empty for the root, shared code and composition layer. */
    Optional<String> moduleOf(JavaClass javaClass) {
        String name = javaClass.getPackageName();
        if (!name.startsWith(root + ".")) {
            return Optional.empty();
        }
        String first = name.substring(root.length() + 1).split("\\.")[0];
        return nonModules.contains(first) ? Optional.empty() : Optional.of(first);
    }

    /** Another module may only use a module through its {@code <root>.<module>.api} package. */
    ArchRule onlyPublicApiCrossesModules() {
        return classes().that().resideInAPackage(root + "..")
                .should(new ArchCondition<>("only use other modules through their api package") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        Optional<String> from = moduleOf(origin);
                        if (from.isEmpty()) {
                            return;
                        }
                        for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                            Optional<String> to = moduleOf(d.getTargetClass());
                            if (to.isPresent() && !to.equals(from)
                                    && !d.getTargetClass().getPackageName().startsWith(root + "." + to.get() + ".api")) {
                                events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    ArchRule modulesAreFreeOfCycles() {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true);
    }

    ArchRule sharedDependsOnNoModuleOrApp(String sharedPackage, String appPackage) {
        return noClasses().that().resideInAPackage(root + "." + sharedPackage + "..")
                .should().dependOnClassesThat().resideOutsideOfPackages(root + "." + sharedPackage + "..", "java..")
                .allowEmptyShould(true);
    }

    ArchRule modulesDoNotDependOnApp(String appPackage) {
        return noClasses().that().resideInAPackage(root + "..")
                .and().resideOutsideOfPackage(root + "." + appPackage + "..")
                .should().dependOnClassesThat().resideInAPackage(root + "." + appPackage + "..")
                .allowEmptyShould(true);
    }
}
