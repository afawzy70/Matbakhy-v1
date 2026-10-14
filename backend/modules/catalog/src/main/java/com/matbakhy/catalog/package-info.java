/**
 * catalog module: Meals, media and serviceable discovery (MB-013, MB-014, MB-016).
 *
 * <p>Only {@code com.matbakhy.catalog.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "catalog", stories = {"MB-013", "MB-014", "MB-016"})
package com.matbakhy.catalog;

import com.matbakhy.shared.module.DomainModule;
