import './home.css'

// Esqueleto de la home mientras llegan los datos: misma forma que la página real, así no "salta" al cargar.
function HomeCargando() {
  return (
    <main className="home" aria-busy="true">
      <p className="solo-lectores">Cargando…</p>
      <div className="encabezado" aria-hidden="true">
        <div className="esqueleto esqueleto--circulo" />
        <div className="esqueleto esqueleto--titulo" />
        <div className="esqueleto esqueleto--linea" />
      </div>
      <div className="servicios" aria-hidden="true">
        {[1, 2, 3].map((n) => (
          <div key={n} className="servicio">
            <div className="servicio__datos">
              <div className="esqueleto esqueleto--linea-corta" />
              <div className="esqueleto esqueleto--linea" />
            </div>
          </div>
        ))}
      </div>
    </main>
  )
}

export default HomeCargando
