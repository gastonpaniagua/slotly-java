import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'

import { guardarLocal, subirImagen, verLocal } from '../api/admin'
import type { ConfiguracionDelLocal } from '../api/tipos'
import { avisoDeContraste } from '../acento'
import { useSesion } from './sesion'

// Los campos de texto, tal cual se escriben (los números también: se convierten al guardar).
type Textos = Record<keyof ConfiguracionDelLocal, string>

const NUMEROS = ['intervaloMin', 'limpiezaMin', 'anticipacionMin', 'topeDias', 'cancelarHastaHoras', 'maxTurnosFuturos'] as const

function aTextos(configuracion: ConfiguracionDelLocal): Textos {
  return Object.fromEntries(Object.entries(configuracion).map(([campo, valor]) => [campo, valor === null ? '' : String(valor)])) as Textos
}

// Lo que se manda: vacío como null, y los números como números (la API marca el error si no lo son).
function aConfiguracion(textos: Textos): ConfiguracionDelLocal {
  const entradas = Object.entries(textos).map(([campo, valor]) => {
    if ((NUMEROS as readonly string[]).includes(campo)) {
      return [campo, valor.trim() === '' || Number.isNaN(Number(valor)) ? null : Number(valor)]
    }
    return [campo, valor.trim() === '' ? null : valor]
  })
  return Object.fromEntries(entradas) as ConfiguracionDelLocal
}

// Datos, imagen y reglas de turnos del local (HU-32). Lo que se guarda vale en el acto en la web y las reservas.
function PaginaLocal() {
  const { sesionPerdida } = useSesion()
  const [textos, setTextos] = useState<Textos | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [aviso, setAviso] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    let vigente = true
    verLocal().then((resultado) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'ok') {
        setTextos(aTextos(resultado.datos))
      } else if (resultado.estado === 'sin-sesion') {
        sesionPerdida()
      } else {
        setAviso('No pudimos cargar la configuración.')
      }
    })
    return () => {
      vigente = false
    }
  }, [sesionPerdida])

  function cambiar(campo: keyof ConfiguracionDelLocal, valor: string) {
    setTextos((actuales) => actuales && { ...actuales, [campo]: valor })
  }

  async function elegirLogo(evento: ChangeEvent<HTMLInputElement>) {
    const archivo = evento.target.files?.[0]
    if (!archivo) {
      return
    }
    const resultado = await subirImagen(archivo)
    if (resultado.estado === 'ok') {
      cambiar('logoUrl', resultado.datos.url)
      setErrores((e) => ({ ...e, logoUrl: '' }))
    } else {
      setErrores((e) => ({ ...e, logoUrl: resultado.estado === 'validacion' ? resultado.mensaje : 'No pudimos subir el logo.' }))
    }
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    if (textos === null) {
      return
    }
    setEnviando(true)
    setAviso(null)
    setErrores({})
    const resultado = await guardarLocal(aConfiguracion(textos))
    setEnviando(false)
    if (resultado.estado === 'ok') {
      setTextos(aTextos(resultado.datos))
      setAviso('Guardado. Las reservas nuevas ya usan estos datos; recargá tu web para verlos.')
    } else if (resultado.estado === 'validacion') {
      // CA3: cada valor fuera de rango, junto a su campo.
      setErrores(resultado.errores)
      setAviso('Revisá los datos marcados.')
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      setAviso('No pudimos guardar. Probá de nuevo.')
    }
  }

  if (textos === null) {
    return (
      <section>
        <h1 className="panel__titulo">Local</h1>
        <p className="panel__texto" role={aviso ? 'alert' : 'status'}>{aviso ?? 'Cargando configuración…'}</p>
      </section>
    )
  }

  function campo(id: keyof ConfiguracionDelLocal, etiqueta: string, opciones: { tipo?: string; ayuda?: string } = {}) {
    return (
      <div className="campo">
        <label className="campo__etiqueta" htmlFor={`local-${id}`}>{etiqueta}</label>
        {id === 'descripcion' ? (
          <textarea id={`local-${id}`} value={textos?.[id] ?? ''} onChange={(e) => cambiar(id, e.target.value)}
            aria-invalid={Boolean(errores[id])} />
        ) : (
          <input id={`local-${id}`} type={opciones.tipo ?? 'text'} value={textos?.[id] ?? ''}
            onChange={(e) => cambiar(id, e.target.value)} aria-invalid={Boolean(errores[id])} />
        )}
        {errores[id] ? <p className="campo__error">{errores[id]}</p>
          : opciones.ayuda && <p className="campo__ayuda">{opciones.ayuda}</p>}
      </div>
    )
  }

  // CA4: el aviso de contraste se ve mientras se elige el color, antes de guardar.
  const contraste = avisoDeContraste(textos.colorAcento)

  return (
    <section>
      <h1 className="panel__titulo">Local</h1>
      <form className="formulario admin__formulario local__formulario" onSubmit={guardar} noValidate aria-label="Configuración del local">
        <h2 className="mis-turnos__titulo">Datos y marca</h2>
        {campo('nombre', 'Nombre')}
        {campo('descripcion', 'Descripción', { ayuda: 'Se ve en la home. Hasta 300 caracteres.' })}
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="local-logo">Logo</label>
          {textos.logoUrl && <img className="opcion__foto" src={textos.logoUrl} alt="Logo elegido" />}
          <input id="local-logo" type="file" accept="image/png,image/jpeg,image/webp" onChange={elegirLogo} />
          <p className="campo__ayuda">PNG, JPG o WebP de hasta 500 KB.</p>
          {textos.logoUrl && <button type="button" className="link" onClick={() => cambiar('logoUrl', '')}>Quitar logo</button>}
          {errores.logoUrl && <p className="campo__error" role="alert">{errores.logoUrl}</p>}
        </div>
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="local-colorAcento">Color</label>
          <div className="local__color">
            <input type="color" aria-label="Elegir color" value={/^#[0-9a-fA-F]{6}$/.test(textos.colorAcento) ? textos.colorAcento : '#1f2937'}
              onChange={(e) => cambiar('colorAcento', e.target.value)} />
            <input id="local-colorAcento" type="text" value={textos.colorAcento} placeholder="#2e7d32"
              onChange={(e) => cambiar('colorAcento', e.target.value)} aria-invalid={Boolean(errores.colorAcento)} />
          </div>
          {errores.colorAcento && <p className="campo__error">{errores.colorAcento}</p>}
          {contraste && <p className="campo__error" role="status">{contraste}</p>}
        </div>

        <h2 className="mis-turnos__titulo">Contacto y ubicación</h2>
        {campo('whatsapp', 'WhatsApp', { tipo: 'tel' })}
        <div className="admin__fila">
          {campo('instagram', 'Instagram', { ayuda: 'El usuario, sin la URL.' })}
          {campo('tiktok', 'TikTok')}
          {campo('facebook', 'Facebook')}
        </div>
        <div className="admin__fila">
          {campo('direccion', 'Dirección')}
          {campo('ciudad', 'Ciudad')}
        </div>

        <h2 className="mis-turnos__titulo">Pago por transferencia y reseñas</h2>
        <div className="admin__fila">
          {campo('alias', 'Alias')}
          {campo('cbu', 'CBU', { ayuda: '22 números.' })}
        </div>
        {campo('titularCuenta', 'Titular de la cuenta')}
        {campo('linkResena', 'Link para dejar una reseña en Google', { tipo: 'url' })}

        <h2 className="mis-turnos__titulo">Reglas de turnos</h2>
        <div className="admin__fila">
          {campo('intervaloMin', 'Intervalo (min)', { tipo: 'number', ayuda: 'Cada cuánto se ofrece un horario. De 5 a 60.' })}
          {campo('limpiezaMin', 'Limpieza (min)', { tipo: 'number', ayuda: 'Libre después de cada turno. De 0 a 30.' })}
          {campo('anticipacionMin', 'Anticipación mínima (min)', { tipo: 'number', ayuda: 'No se reserva para dentro de menos.' })}
        </div>
        <div className="admin__fila">
          {campo('topeDias', 'Tope (días)', { tipo: 'number', ayuda: 'Hasta cuántos días hacia adelante.' })}
          {campo('cancelarHastaHoras', 'Cancelar hasta (horas antes)', { tipo: 'number' })}
          {campo('maxTurnosFuturos', 'Turnos por teléfono', { tipo: 'number', ayuda: 'Cuántos turnos futuros puede tener un cliente.' })}
        </div>

        {aviso && <p className={errores && Object.keys(errores).length > 0 ? 'campo__error' : 'reserva__aviso'} role="status">{aviso}</p>}
        <div className="turno__acciones">
          <button type="submit" className="btn btn-primary" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar'}</button>
        </div>
      </form>
    </section>
  )
}

export default PaginaLocal
