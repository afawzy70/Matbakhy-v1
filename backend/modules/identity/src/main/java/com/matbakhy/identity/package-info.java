/**
 * identity module: Users mapped from OIDC issuer/subject, profile, addresses, phone verification, grants and sessions (MB-009–MB-011).
 *
 * <p>Only {@code com.matbakhy.identity.api} may be used by other modules; every other package is
 * module-internal. Enforced by {@code ModuleBoundaryTest} in the app module and by Maven
 * dependencies (a module can only see modules declared in its pom).
 */
@DomainModule(value = "identity", stories = {"MB-009", "MB-010", "MB-011"})
package com.matbakhy.identity;

import com.matbakhy.shared.module.DomainModule;
