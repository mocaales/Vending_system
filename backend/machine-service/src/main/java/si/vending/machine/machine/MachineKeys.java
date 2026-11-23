package si.vending.machine.machine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of machine keys. The secret is random (256 bits), so a fast hash is safe.
 */
final class MachineKeys {

  private static final SecureRandom RANDOM = new SecureRandom();

  private MachineKeys() {}

  /** A new secret: {@code mk_} followed by 43 URL-safe characters. */
  static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return "mk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Hex SHA-256 of the secret (64 characters); this is what is stored and looked up. */
  static String hash(String secret) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The last 4 characters, shown to operators to tell keys apart. */
  static String hint(String secret) {
    return secret.substring(secret.length() - 4);
  }
}
