/**
 * availability module: Dated cook slots and per-meal capacity with transactional reservation (MB-015, MB-018).
 *
 * <p>Only {@code com.matbakhy.availability.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "availability", stories = {"MB-015", "MB-018"})
package com.matbakhy.availability;

import com.matbakhy.shared.module.DomainModule;
