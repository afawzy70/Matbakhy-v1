/**
 * orders module: Quotes, immutable order snapshots, state transitions and handover (MB-017–MB-019).
 *
 * <p>Only {@code com.matbakhy.orders.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "orders", stories = {"MB-017", "MB-018", "MB-019"})
package com.matbakhy.orders;

import com.matbakhy.shared.module.DomainModule;
