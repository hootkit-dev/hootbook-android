import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

/** Source-launched by JDK 21. Never prints credentials, aliases, or provider errors. */
class SigningPreflight {
    public static void main(String[] args) {
        char[] storePassword = System.getenv("KEYSTORE_PASSWORD").toCharArray();
        char[] keyPassword = System.getenv("KEY_PASSWORD").toCharArray();
        int status;
        try {
            status = verify(Path.of(args[0]), Path.of(args[1]),
                    System.getenv("KEY_ALIAS"), storePassword, keyPassword);
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
        if (status != 0) System.exit(status);
    }

    private static int verify(Path keystorePath, Path probeDirectory, String alias,
                              char[] storePassword, char[] keyPassword) {
        KeyStore keystore;
        try {
            // Probe the actual file format instead of assuming .keystore means
            // JKS. Both historical JKS and modern PKCS12 files are supported.
            keystore = KeyStore.getInstance(keystorePath.toFile(), storePassword);
            if (!keystore.getType().equalsIgnoreCase("JKS")
                    && !keystore.getType().equalsIgnoreCase("PKCS12")) return 10;
        } catch (Exception ignored) {
            return 10;
        }
        try {
            if (!keystore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) return 11;
        } catch (Exception ignored) {
            return 11;
        }
        PrivateKey key;
        try {
            // Use KEY_PASSWORD explicitly, even when the store password works.
            // This catches a wrong separate JKS/PKCS12 private-key password.
            key = (PrivateKey) keystore.getKey(alias, keyPassword);
            if (key == null) return 12;
        } catch (Exception ignored) {
            return 12;
        }
        Certificate certificate;
        try {
            certificate = keystore.getCertificate(alias);
            if (certificate == null) return 14;
        } catch (Exception ignored) {
            return 14;
        }
        String expectedFingerprint = System.getenv("RELEASE_CERT_SHA256");
        if (expectedFingerprint != null && !expectedFingerprint.isEmpty()) {
            if (!expectedFingerprint.matches("(?i)[0-9a-f]{64}")) return 18;
            try {
                String fingerprint = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
                if (!fingerprint.equalsIgnoreCase(expectedFingerprint)) return 17;
            } catch (Exception ignored) {
                return 17;
            }
        }
        String algorithm = switch (key.getAlgorithm()) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA256withECDSA";
            case "DSA" -> "SHA256withDSA";
            default -> null;
        };
        if (algorithm == null) return 13;
        Path payload = probeDirectory.resolve("readiness-payload.bin");
        Path signaturePath = probeDirectory.resolve("readiness-signature.bin");
        try {
            Files.writeString(payload, "Hootbook signing readiness probe\n" + UUID.randomUUID());
            Signature signer = Signature.getInstance(algorithm);
            signer.initSign(key);
            signer.update(Files.readAllBytes(payload));
            Files.write(signaturePath, signer.sign());
        } catch (Exception ignored) {
            return 15;
        }
        try {
            // Verify actual payload bytes against this alias's certificate.
            // Do not impose a public CA trust chain: Android release signing
            // commonly uses a self-signed certificate. This is not APK signing.
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(Files.readAllBytes(payload));
            if (!verifier.verify(Files.readAllBytes(signaturePath))) return 16;
        } catch (Exception ignored) {
            return 16;
        }
        return 0;
    }
}
