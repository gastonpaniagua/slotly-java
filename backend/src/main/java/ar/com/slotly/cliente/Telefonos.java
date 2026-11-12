package ar.com.slotly.cliente;

import java.util.Optional;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

// Normaliza teléfonos al formato internacional (+5491123456789) para que un mismo celular escrito de distintas
// formas sea el mismo cliente (doc 03, HU-10 CA3). Usa libphonenumber, la librería de Google.
// Se asume celular: en Argentina se suele escribir sin el 9 ni el 15 ("11 2345-6789"), y la reserva necesita un
// número de WhatsApp. Si lo escrito es un número fijo válido, se prueba la versión celular (con el 9).
public final class Telefonos {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private Telefonos() {
    }

    // El teléfono normalizado, o vacío si no es un celular válido.
    public static Optional<String> normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        PhoneNumber numero;
        try {
            numero = UTIL.parse(texto, "AR");
        } catch (NumberParseException e) {
            return Optional.empty();
        }
        if (!UTIL.isValidNumber(numero)) {
            return Optional.empty();
        }
        if (numero.getCountryCode() != 54) {
            // De otro país: se acepta tal cual, en formato internacional.
            return Optional.of(UTIL.format(numero, PhoneNumberFormat.E164));
        }
        if (esCelular(numero)) {
            return Optional.of(UTIL.format(numero, PhoneNumberFormat.E164));
        }
        // Escrito como fijo ("11 2345-6789"): la versión celular agrega el 9 adelante del código de área.
        try {
            PhoneNumber comoCelular = UTIL.parse("+549" + UTIL.getNationalSignificantNumber(numero), "AR");
            if (UTIL.isValidNumber(comoCelular) && esCelular(comoCelular)) {
                return Optional.of(UTIL.format(comoCelular, PhoneNumberFormat.E164));
            }
        } catch (NumberParseException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static boolean esCelular(PhoneNumber numero) {
        PhoneNumberType tipo = UTIL.getNumberType(numero);
        return tipo == PhoneNumberType.MOBILE || tipo == PhoneNumberType.FIXED_LINE_OR_MOBILE;
    }
}
