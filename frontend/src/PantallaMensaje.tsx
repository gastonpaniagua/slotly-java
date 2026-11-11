type Props = {
  titulo: string
  texto?: string
  // Botón opcional, por ejemplo "Probar de nuevo" en la pantalla de error.
  accion?: { texto: string; alHacerClic: () => void }
}

// Pantalla simple centrada para estados que no son la home: local inexistente, error.
function PantallaMensaje({ titulo, texto, accion }: Props) {
  return (
    <main className="pantalla-mensaje">
      <h1 className="pantalla-mensaje__titulo">{titulo}</h1>
      {texto && <p className="pantalla-mensaje__texto">{texto}</p>}
      {accion && (
        <button type="button" className="btn btn-secondary pantalla-mensaje__accion" onClick={accion.alHacerClic}>
          {accion.texto}
        </button>
      )}
    </main>
  )
}

export default PantallaMensaje
