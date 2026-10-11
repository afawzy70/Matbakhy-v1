package com.matbakhy.shared.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a domain module on its root {@code package-info.java}. Only the module's {@code api}
 * subpackage is visible to other modules (checked by the app module's architecture tests).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface DomainModule {

    /** Module name; must equal the package segment under {@code com.matbakhy}. */
    String value();

    /** Stories that own this module's behavior. */
    String[] stories() default {};
}
