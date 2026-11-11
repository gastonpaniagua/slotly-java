type Props = {
  titulo: string
  texto?: string
}

// Pantalla simple centrada para estados que no son la home: cargando, local inexistente, error.
function PantallaMensaje({ titulo, texto }: Props) {
  return (
    <main className="pantalla-mensaje">
      <h1 className="pantalla-mensaje__titulo">{titulo}</h1>
      {texto && <p className="pantalla-mensaje__texto">{texto}</p>}
    </main>
  )
}

export default PantallaMensaje
