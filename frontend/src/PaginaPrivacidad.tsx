// Política de privacidad (HU-10 CA2): el link del consentimiento del paso 5 lleva acá.
// Texto aprobado por Gaston el 2026-11-12. No es asesoramiento legal: revisarlo antes de producción (Ley 25.326).
function PaginaPrivacidad() {
  return (
    <main className="reserva privacidad">
      <h1 className="reserva__titulo">Política de privacidad</h1>

      <h2>Qué datos guardamos</h2>
      <p>
        Cuando reservás un turno guardamos tu nombre, tu teléfono, tu mail (si lo dejás), el turno que reservaste y la
        forma de pago que elegiste.
      </p>

      <h2>Para qué los usamos</h2>
      <p>
        Solo para gestionar tus turnos con el local: confirmarlos, avisarte de cambios y que el local sepa quién viene.
        No los vendemos ni los compartimos con otros locales ni con terceros.
      </p>

      <h2>Quién los ve</h2>
      <p>El local donde reservaste y Slotly, que es el sistema de turnos que usa el local.</p>

      <h2>En tu navegador</h2>
      <p>
        Para que no tengas que escribirlos de nuevo, tu nombre y tu teléfono quedan guardados en este navegador. Los
        podés borrar desde la configuración del navegador.
      </p>

      <h2>Cuánto tiempo</h2>
      <p>Mientras tengas turnos con el local y hasta 2 años después del último.</p>

      <h2>Tus derechos</h2>
      <p>
        Podés pedir ver, corregir o borrar tus datos escribiéndole al local por WhatsApp. Lo establece la Ley 25.326 de
        Protección de Datos Personales. La autoridad de control es la Agencia de Acceso a la Información Pública (AAIP).
      </p>
    </main>
  )
}

export default PaginaPrivacidad
