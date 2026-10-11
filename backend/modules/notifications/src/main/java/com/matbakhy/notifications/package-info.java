/**
 * notifications module: Outbox delivery, in-app inbox, email and push adapters (MB-021).
 *
 * <p>Only {@code com.matbakhy.notifications.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "notifications", stories = {"MB-021"})
package com.matbakhy.notifications;

import com.matbakhy.shared.module.DomainModule;
