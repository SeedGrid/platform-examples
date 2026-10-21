package br.com.seedgrid.citiesexample.security;

import java.security.Permission;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A REST da extension usa {@code @PermissionsAllowed("CITY_READ")}. Num host real a permissao vem do RBAC (a
 * migration V1203 da cities a liga a role ADMIN quando a security existe); aqui, sem security, quem tem a role
 * {@code reader} ganha a permissao.
 */
@ApplicationScoped
public class CityReaderAugmentor implements SecurityIdentityAugmentor {

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous() || !identity.hasRole("reader")) {
            return Uni.createFrom().item(identity);
        }
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder(identity)
                .addPermissionChecker(requested -> Uni.createFrom().item(isCityRead(requested)))
                .build());
    }

    private static boolean isCityRead(Permission requested) {
        return "CITY_READ".equals(requested.getName());
    }
}
