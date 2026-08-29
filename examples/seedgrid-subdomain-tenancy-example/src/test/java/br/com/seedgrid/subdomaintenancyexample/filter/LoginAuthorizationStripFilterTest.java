package br.com.seedgrid.subdomaintenancyexample.filter;

import java.io.IOException;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LoginAuthorizationStripFilterTest {

    private final LoginAuthorizationStripFilter filter = new LoginAuthorizationStripFilter();

    @Test
    void removesAuthorizationHeaderOnLogin() throws IOException {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer token-antigo");

        filter.filter(requestContext("/public/auth/login/", headers));

        assertFalse(headers.containsKey(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void keepsAuthorizationHeaderOutsideLogin() throws IOException {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer token-antigo");

        filter.filter(requestContext("public/auth/refresh", headers));

        assertEquals("Bearer token-antigo", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    private ContainerRequestContext requestContext(String path, MultivaluedMap<String, String> headers) {
        UriInfo uriInfo = (UriInfo) Proxy.newProxyInstance(
            UriInfo.class.getClassLoader(),
            new Class<?>[] { UriInfo.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getPath" -> path;
                case "toString" -> "UriInfoProxy[" + path + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });

        return (ContainerRequestContext) Proxy.newProxyInstance(
            ContainerRequestContext.class.getClassLoader(),
            new Class<?>[] { ContainerRequestContext.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getUriInfo" -> uriInfo;
                case "getHeaders" -> headers;
                case "toString" -> "ContainerRequestContextProxy[" + path + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
