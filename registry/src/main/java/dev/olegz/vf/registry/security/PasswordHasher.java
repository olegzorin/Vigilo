package dev.olegz.vf.registry.security;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import dev.olegz.vf.common.ApplicationFailureException;

/**
 * Utility class for secure password hashing and verification using the Argon2id algorithm.
 *
 * This class implements password hashing following OWASP recommendations with the following parameters:
 * - Memory: 19 MiB
 * - Iterations: 2
 * - Parallelism: 1
 *
 * The implementation uses a thread-safe object pool for Argon2 instances to optimize performance
 * in multi-threaded environments by reusing hasher instances across multiple operations.
 *
 * All methods in this class are thread-safe and can be used concurrently.
 */
public class PasswordHasher {
    // OWASP-recommended Argon2id baseline for password storage: m=19 MiB, t=2, p=1.
    private static final int PASSWORD_ITERATIONS = 2;
    private static final int PASSWORD_MEMORY = 19 << 10; // 19 MiB, expressed in KiB
    private static final int PARALLELISM = 1;

    private static final ConcurrentLinkedQueue<Argon2> argon2Pool = new ConcurrentLinkedQueue<>();

    /**
     * Generates a secure password hash using the Argon2id algorithm with OWASP-recommended parameters.
     *
     * This method applies the following configuration:
     * - Algorithm: Argon2id
     * - Iterations: 2
     * - Memory: 19 MiB
     * - Parallelism: 1
     *
     * The implementation uses a pooled Argon2 instance for optimal performance in concurrent environments.
     * This method is thread-safe and can be called concurrently from multiple threads.
     *
     * @param password the plaintext password to hash
     * @return the generated Argon2id hash string, or null if the password is null
     * @throws ApplicationFailureException if an error occurs during hash generation
     */
    public static String passwordHash(String password) {
        return hash(password, PASSWORD_ITERATIONS, PASSWORD_MEMORY);
    }

    /**
     * Generates a secure password hash using the Argon2id algorithm with custom parameters.
     *
     * This method uses a pooled Argon2 instance from the internal pool to generate a hash
     * with the specified number of iterations and memory allocation. The parallelism factor
     * is fixed to the class constant PARALLELISM. The password is encoded using UTF-8 charset.
     * The Argon2 instance is properly returned to the pool after use, ensuring efficient
     * resource management in concurrent environments.
     *
     * @param password the plaintext password to hash, may be null
     * @param iterations the number of iterations to perform during hashing
     * @param memory the amount of memory in MiB to use during hashing
     * @return the generated Argon2id hash string, or null if the password is null
     * @throws ApplicationFailureException if an error occurs during hash generation
     */
    private static String hash(String password, int iterations, int memory) {
        if (password == null) return null;

        Argon2 argon2 = acquireArgon2();
        try {
            return argon2.hash(iterations, memory, PARALLELISM, password.toCharArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            throw new ApplicationFailureException("Exception in generating hash", e);
        } finally {
            releaseArgon2(argon2);
        }
    }

    /**
     * Verifies a plaintext password against an Argon2id hash.
     *
     * This method uses a pooled Argon2 instance to verify whether the provided plaintext password
     * matches the given hash. The password is encoded using UTF-8 charset before verification.
     * The Argon2 instance is properly returned to the pool after use, ensuring efficient resource
     * management in concurrent environments. This method is thread-safe and can be called concurrently
     * from multiple threads.
     *
     * @param password the plaintext password to verify, returns false if null
     * @param hash the Argon2id hash to verify against, returns false if null
     * @return true if the password matches the hash, false otherwise
     * @throws ApplicationFailureException if an error occurs during verification
     */
    public static boolean verifyPassword(String password, String hash) {
        if ((hash == null) || (password == null)) return false;

        Argon2 argon2 = acquireArgon2();
        try {
            return argon2.verify(hash, password.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable e) {
            throw new ApplicationFailureException("Exception in verifying hash", e);
        } finally {
            releaseArgon2(argon2);
        }
    }

    private static Argon2 acquireArgon2() {
        Argon2 argon2 = argon2Pool.poll();
        return argon2 != null ? argon2 : Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    }

    private static void releaseArgon2(Argon2 argon2) {
        argon2Pool.add(argon2);
    }
}
