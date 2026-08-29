package br.com.seedgrid.subdomaintenancyexample.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility class to generate BCrypt password hashes for database seed scripts.
 *
 * The system uses a pattern of BCrypt(userId + plainPassword) for additional security.
 */
public class PasswordHashGenerator {

    /**
     * Generates a BCrypt hash for a password with a given user ID.
     *
     * Pattern: BCrypt.hashpw(userId + plainPassword)
     *
     * @param userId the user ID
     * @param plainPassword the plain text password
     * @return BCrypt hash
     */
    public static String generateHash(Long userId, String plainPassword) {
        String combined = userId + plainPassword;
        return BCrypt.hashpw(combined, BCrypt.gensalt());
    }

    /**
     * Main method to generate password hashes for seed scripts.
     * Run this class to generate the hash for the admin user.
     */
    public static void main(String[] args) {
        // Admin user will have ID=1 (first user in the database)
        Long adminUserId = 1L;
        String adminPassword = "12345678";

        String hash = generateHash(adminUserId, adminPassword);

        System.out.println("=".repeat(80));
        System.out.println("Password Hash Generator for Seed Scripts");
        System.out.println("=".repeat(80));
        System.out.println("User ID:       " + adminUserId);
        System.out.println("Password:      " + adminPassword);
        System.out.println("Combined:      " + adminUserId + adminPassword);
        System.out.println("-".repeat(80));
        System.out.println("BCrypt Hash:   " + hash);
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Use this hash in your V2002__seed_admin_user.sql migration:");
        System.out.println("password = '" + hash + "'");
        System.out.println("=".repeat(80));
    }
}
