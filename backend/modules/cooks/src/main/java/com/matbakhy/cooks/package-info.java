/**
 * cooks module: Cook applications, private verification documents and kitchens (MB-012).
 *
 * <p>Only {@code com.matbakhy.cooks.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "cooks", stories = {"MB-012"})
package com.matbakhy.cooks;

import com.matbakhy.shared.module.DomainModule;
