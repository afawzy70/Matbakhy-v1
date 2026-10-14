/**
 * operations module: Staff controls, audit, moderation and operating configuration (MB-011, MB-023, MB-026).
 *
 * <p>Only {@code com.matbakhy.operations.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "operations", stories = {"MB-011", "MB-023", "MB-026"})
package com.matbakhy.operations;

import com.matbakhy.shared.module.DomainModule;
