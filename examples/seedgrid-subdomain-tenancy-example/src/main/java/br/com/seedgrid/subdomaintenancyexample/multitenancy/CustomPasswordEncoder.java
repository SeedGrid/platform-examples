package br.com.seedgrid.subdomaintenancyexample.multitenancy;

import br.com.seedgrid.subdomaintenancy.spis.PasswordEncoder;
import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Custom implementation of PasswordEncoder for seedgrid-test-quarkus.
 * This overrides the default BCryptPasswordEncoder from seedgrid-subdomain-tenancy.
 * 
 * You can customize the salt rounds or use a different algorithm here.
 */
@ApplicationScoped
public class CustomPasswordEncoder implements PasswordEncoder {

    private static final int SALT_ROUNDS = 12;

    @Override
    public String encode(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(SALT_ROUNDS));
    }

    /**
     * Verifies if a plain password matches the hashed password.
     * 
     * @param plainPassword the plain password
     * @param hashedPassword the hashed password
     * @return true if the password matches, false otherwise
     */
    public boolean matches(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}

