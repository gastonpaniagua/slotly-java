// Lo que se ve al entrar sin subdominio de local (HU-03 CA5): una página simple de Slotly, no la home de un local.
function PaginaSlotly() {
  return (
    <main className="pantalla-mensaje">
      <div className="marca-slotly" aria-hidden="true">S</div>
      <h1 className="pantalla-mensaje__titulo">Slotly</h1>
      <p className="pantalla-mensaje__texto">Turnos online para peluquerías y barberías.</p>
      <p className="pantalla-mensaje__texto">
        ¿Buscás reservar? Entrá desde el link de tu local, por ejemplo <strong>tulocal.slotly.com.ar</strong>.
      </p>
    </main>
  )
}

export default PaginaSlotly
