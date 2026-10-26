package br.com.seedgrid.subdomaintenancyexample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.seedgrid.security.configs.AdminGuardScope;
import br.com.seedgrid.security.configs.IsolationMode;
import br.com.seedgrid.security.configs.SecurityIsolationPolicy;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** T1.1: o subdomain-tenancy declara {@code dedicated-database}; valem os padroes da tabela do plano 3. */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class IsolationPolicyDedicatedDatabaseTest {

    @Inject
    SecurityIsolationPolicy policy;

    @Test
    void subdomainTenancyRunsDedicatedDatabaseWithItsDefaults() {
        assertEquals(IsolationMode.DEDICATED_DATABASE, policy.mode());
        assertTrue(policy.isDedicatedDatabase());
        assertFalse(policy.accountEnabled());
        assertTrue(policy.isPlatformScopeExempt("companies"));
        assertTrue(policy.isPlatformScopeExempt("users"));
        assertTrue(policy.isPlatformScopeExempt("roles"));
        assertFalse(policy.isPlatformScopeExempt("permissions"));
        assertEquals(AdminGuardScope.DATABASE, policy.adminGuardScope());
    }
}
