/**
 * finance module: Cash collection, balanced ledger, refunds and settlements (MB-020, MB-025).
 *
 * <p>Only {@code com.matbakhy.finance.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "finance", stories = {"MB-020", "MB-025"})
package com.matbakhy.finance;

import com.matbakhy.shared.module.DomainModule;
