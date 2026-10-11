/**
 * support module: Support cases and order exceptions (MB-024).
 *
 * <p>Only {@code com.matbakhy.support.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "support", stories = {"MB-024"})
package com.matbakhy.support;

import com.matbakhy.shared.module.DomainModule;
