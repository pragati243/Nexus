package com.nexus;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * One real rule today (SLF4J only, no {@code java.util.logging}); the
 * tenant-scoping and module-boundary rules described in
 * docs/build-plan.md Phase 2 land once there are repositories and modules
 * to check.
 */
@AnalyzeClasses(packages = "com.nexus")
class ArchitectureTest {

    @ArchTest
    static final ArchRule classes_should_not_use_java_util_logging =
            noClasses().should().accessClassesThat().resideInAPackage("java.util.logging..");
}
