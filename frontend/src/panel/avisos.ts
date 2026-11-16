import { useEffect, useState } from 'react'
import { useLocation } from 'react-router'

import { marcarAvisosLeidos, verAvisos } from '../api/notificaciones'
import type { Aviso, BandejaDeAvisos } from '../api/tipos'
import { fechaLarga } from '../fechas'

// Los avisos del profesional (HU-37): se vuelven a pedir en cada cambio de página, sin consultar cada tantos segundos.
// Los muestran BotonAvisos y ListaAvisos (AvisosDelPanel.tsx).

export type Avisos = {
  bandeja: BandejaDeAvisos | null
  abierto: boolean
  alternar: () => void
}

export function useAvisos(activo: boolean): Avisos {
  const { pathname } = useLocation()
  const [bandeja, setBandeja] = useState<BandejaDeAvisos | null>(null)
  // En qué página se abrió la lista: al cambiar de página queda cerrada sola.
  const [abiertoEn, setAbiertoEn] = useState<string | null>(null)
  const abierto = abiertoEn === pathname

  useEffect(() => {
    if (!activo) {
      return
    }
    let vigente = true
    // Si falla, el panel sigue andando sin avisos; la sesión perdida ya la resuelve el marco del panel.
    verAvisos().then((resultado) => {
      if (vigente && resultado.estado === 'ok') {
        setBandeja(resultado.datos)
      }
    })
    return () => {
      vigente = false
    }
  }, [activo, pathname])

  function alternar() {
    const abrir = !abierto
    setAbiertoEn(abrir ? pathname : null)
    // CA2: al abrir la lista se leen todos y el contador vuelve a cero. Cada aviso conserva su marca de "nuevo"
    // mientras la lista está abierta, para ver cuáles eran.
    if (abrir && bandeja !== null && bandeja.sinLeer > 0) {
      setBandeja({ ...bandeja, sinLeer: 0 })
      void marcarAvisosLeidos()
    }
  }

  return { bandeja, abierto, alternar }
}

// CA1: qué pasó, con quién y cuándo es el turno (y cuándo era, si cambió de horario).
export function textoDelAviso(aviso: Aviso): string {
  const cuando = `el ${fechaLarga(aviso.fecha)} a las ${aviso.hora}`
  const antes = aviso.fechaAnterior && aviso.horaAnterior
    ? `del ${fechaLarga(aviso.fechaAnterior)} a las ${aviso.horaAnterior}`
    : ''
  switch (aviso.tipo) {
    case 'reserva':
      return `Nuevo turno de ${aviso.clienteNombre}: ${aviso.servicios.join(' + ')}, ${cuando}.`
    case 'cancelacion':
      return `Se canceló el turno de ${aviso.clienteNombre} del ${fechaLarga(aviso.fecha)} a las ${aviso.hora}.`
    case 'reprogramacion':
      return `${aviso.clienteNombre} cambió su turno ${antes} al ${fechaLarga(aviso.fecha)} a las ${aviso.hora}.`
    case 'movido':
      return `Se movió el turno de ${aviso.clienteNombre} ${antes} al ${fechaLarga(aviso.fecha)} a las ${aviso.hora}.`
    default:
      return `Cambió el turno de ${aviso.clienteNombre} ${cuando}.`
  }
}
