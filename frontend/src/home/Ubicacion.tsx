type Props = {
  nombre: string
  direccion: string
  ciudad: string | null
  latitud: number | null
  longitud: number | null
}

// Dirección, mapa embebido y botón "Cómo llegar" (HU-02 CA3). Usa las URLs públicas de Google Maps:
// no necesitan clave de API ni tienen costo.
function Ubicacion({ nombre, direccion, ciudad, latitud, longitud }: Props) {
  const textoDireccion = ciudad ? `${direccion}, ${ciudad}` : direccion
  // Con coordenadas el punto es exacto; si no, Google busca la dirección escrita.
  const destino = latitud !== null && longitud !== null ? `${latitud},${longitud}` : textoDireccion
  const destinoUrl = encodeURIComponent(destino)

  return (
    <section className="seccion" aria-labelledby="titulo-ubicacion">
      <h2 id="titulo-ubicacion" className="seccion__titulo">Ubicación</h2>
      <div className="ubicacion">
        <iframe
          className="ubicacion__mapa"
          title={`Mapa con la ubicación de ${nombre}`}
          src={`https://www.google.com/maps?q=${destinoUrl}&output=embed`}
          loading="lazy"
          referrerPolicy="no-referrer-when-downgrade"
        />
        <div className="ubicacion__pie">
          <p className="ubicacion__direccion">{textoDireccion}</p>
          <a
            className="btn btn-primary"
            href={`https://www.google.com/maps/dir/?api=1&destination=${destinoUrl}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            Cómo llegar
          </a>
        </div>
      </div>
    </section>
  )
}

export default Ubicacion
