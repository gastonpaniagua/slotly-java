import type { LocalPublico } from '../api/tipos'
import { linkDeWhatsApp } from '../formato'

type Props = {
  local: Pick<LocalPublico, 'whatsapp' | 'instagram' | 'tiktok' | 'facebook'>
}

type Red = { nombre: string; detalle: string | null; url: string }

// Contacto y redes del local (doc 01 §2.1 A, HU-32 CA1). De las redes se guarda el usuario sin la @ (doc 03), así
// que acá se arma el link a cada perfil. Solo aparece lo que el local cargó; sin nada, no hay sección.
function Contacto({ local }: Props) {
  const redes: Red[] = []
  if (local.whatsapp) {
    redes.push({ nombre: 'WhatsApp', detalle: null, url: linkDeWhatsApp(local.whatsapp) })
  }
  if (local.instagram) {
    redes.push({ nombre: 'Instagram', detalle: `@${local.instagram}`, url: `https://www.instagram.com/${local.instagram}` })
  }
  if (local.tiktok) {
    redes.push({ nombre: 'TikTok', detalle: `@${local.tiktok}`, url: `https://www.tiktok.com/@${local.tiktok}` })
  }
  if (local.facebook) {
    redes.push({ nombre: 'Facebook', detalle: local.facebook, url: `https://www.facebook.com/${local.facebook}` })
  }
  if (redes.length === 0) {
    return null
  }

  return (
    <section className="seccion" aria-labelledby="titulo-contacto">
      <h2 id="titulo-contacto" className="seccion__titulo">Contacto</h2>
      <ul className="contacto">
        {redes.map((red) => (
          <li key={red.nombre}>
            <a className="contacto__link" href={red.url} target="_blank" rel="noopener noreferrer">
              <span className="contacto__red">{red.nombre}</span>
              {red.detalle && <> <span className="contacto__detalle">{red.detalle}</span></>}
            </a>
          </li>
        ))}
      </ul>
    </section>
  )
}

export default Contacto
