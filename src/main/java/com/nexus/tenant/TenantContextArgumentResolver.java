package com.nexus.tenant;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@code @CurrentTenant TenantContext} controller parameters. Runs
 * inside normal MVC argument resolution — after Spring Security's filter
 * chain has already established (or rejected) authentication for the
 * request, so {@link SecurityContextHolder} is reliably populated by the
 * time this runs. The {@code X-Tenant-Id} header check in
 * {@link TenantContextResolver} is deliberately the only real dependency
 * here; the anonymous/null checks below are defense in depth, not the
 * primary enforcement (that's the security filter chain's
 * {@code anyRequest().authenticated()} rule).
 */
public class TenantContextArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantContextResolver tenantContextResolver;

    public TenantContextArgumentResolver(TenantContextResolver tenantContextResolver) {
        this.tenantContextResolver = tenantContextResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentTenant.class)
                && TenantContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new TenantAccessDeniedException("No authenticated principal is available for this request.");
        }
        String tenantIdHeader = webRequest.getHeader(TENANT_HEADER);
        return tenantContextResolver.resolve(authentication.getName(), tenantIdHeader);
    }
}
