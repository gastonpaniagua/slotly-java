package ar.com.slotly.reserva;

// Con qué prueba el cliente que un turno es suyo: el token del link de "Mi turno" (HU-19) o el acceso que recibió
// al entrar con su teléfono y un código (HU-22). Viene uno de los dos, cada uno en su header.
public record Credencial(String token, String acceso) {

    // El header del acceso del cliente (el del token del link es ConfirmacionController.HEADER_TOKEN).
    public static final String HEADER_ACCESO = "X-Acceso-Cliente";
}
