package br.com.seedgrid.subdomaintenancyexample.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 1)
public class LoginAuthorizationStripFilter implements ContainerRequestFilter {

    private static final String LOGIN_PATH = "public/auth/login";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (!LOGIN_PATH.equals(path)) {
            return;
        }

        requestContext.getHeaders().keySet()
            .removeIf(HttpHeaders.AUTHORIZATION::equalsIgnoreCase);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        int start = 0;
        int end = path.length();

        while (start < end && path.charAt(start) == '/') {
            start++;
        }

        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }

        return path.substring(start, end);
    }
}
