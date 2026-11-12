package ar.com.slotly.reserva;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// El token secreto de un turno: prueba que el apartado (y después "Mi turno") es de quien lo creó.
// Se le da al cliente una sola vez; en la base se guarda solo su hash, así si se filtra la base no se filtran
// los links (doc 03, "Token del turno").
final class TokenSecreto {

    private static final SecureRandom AZAR = new SecureRandom();

    private TokenSecreto() {
    }

    // 32 bytes al azar en Base64 sin relleno: imposible de adivinar y seguro para ir en una URL.
    static String nuevo() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 en hexadecimal: lo que se guarda en turno.token_hash y con lo que se compara.
    static String hash(String token) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 siempre existe en Java", e);
        }
    }
}
