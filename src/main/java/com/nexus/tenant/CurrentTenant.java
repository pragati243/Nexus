package com.nexus.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter to be resolved by
 * {@link TenantContextArgumentResolver} — the only sanctioned way a
 * controller obtains a {@link TenantContext}. There is deliberately no
 * other constructor for one outside tests.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentTenant {
}
