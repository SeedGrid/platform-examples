package br.com.seedgrid.logintenancyexample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.seedgrid.security.configs.AdminGuardScope;
import br.com.seedgrid.security.configs.IsolationMode;
import br.com.seedgrid.security.configs.SecurityIsolationPolicy;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** T1.1: o login-tenancy declara {@code shared-database}; os padroes do security refletem o comportamento atual. */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class IsolationPolicySharedDatabaseTest {

    @Inject
    SecurityIsolationPolicy policy;

    @Test
    void loginTenancyRunsSharedDatabaseWithTodaysDefaults() {
        assertEquals(IsolationMode.SHARED_DATABASE, policy.mode());
        assertTrue(policy.accountEnabled());
        assertTrue(policy.platformScopeExempt().isEmpty());
        assertEquals(AdminGuardScope.COMPANY, policy.adminGuardScope());
        assertFalse(policy.isDedicatedDatabase());
    }
}
