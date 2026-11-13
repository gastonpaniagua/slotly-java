import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router'

import type { Resultado } from '../api/publico'
import {
  obtenerDiasParaReprogramar, obtenerHorasParaReprogramar, obtenerTurno, reprogramarTurno, type ResultadoTurno,
} from '../api/reserva'
import type { HorasLibres } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { credencialDelTurno, linkDelTurno, reemplazarTurno } from './memoria'
import PasoDia from './PasoDia'
import PasoHora from './PasoHora'
import './reserva.css'

// Los pasos de día y hora piden servicios y profesional, pero al reprogramar los datos salen del turno (la API
// los conoce): van vacíos y fijos, para que no cambien en cada render.
const SIN_SERVICIOS: string[] = []
const DEL_TURNO = ''

// Reprogramar (HU-21): /turno/{id}/reprogramar/dia y /hora, con el token después del # como en "Mi turno".
// Reusa el calendario y la lista de horas de la reserva, con los mismos servicios y profesional del turno (CA1).
function PaginaReprogramar() {
  const { id = '' } = useParams()
  const ubicacion = useLocation()
  const navegar = useNavigate()
  const token = new URLSearchParams(ubicacion.hash.replace(/^#/, '')).get('t') ?? ''
  // Sin token en la dirección, vale el acceso del cliente (HU-22); entonces los links no llevan #.
  const credencial = useMemo(() => credencialDelTurno(token), [token])
  const conToken = token ? `#t=${encodeURIComponent(token)}` : ''
  const linkDeEstePaso = (paso: 'dia' | 'hora') => `/turno/${id}/reprogramar/${paso}${conToken}`

  const [actual, setActual] = useState<ResultadoTurno | null>(null)
  const [fecha, setFecha] = useState<string | null>(null)
  // La hora elegida, con su día: si después se elige otro día, esa hora ya no vale.
  const [hora, setHora] = useState<{ fecha: string; hora: string } | null>(null)
  const horaDelDia = hora !== null && hora.fecha === fecha ? hora.hora : null

  useEffect(() => {
    let vigente = true
    obtenerTurno(id, credencial).then((resultado) => {
      if (vigente) {
        setActual(resultado)
      }
    })
    return () => {
      vigente = false
    }
  }, [id, credencial])

  const cargarDias = useCallback((desde?: string) => obtenerDiasParaReprogramar(id, credencial, desde), [id, credencial])
  const cargarHoras = useCallback((): Promise<Resultado<HorasLibres>> => (
    fecha === null ? Promise.resolve({ estado: 'error' }) : obtenerHorasParaReprogramar(id, credencial, fecha)
  ), [id, credencial, fecha])
  const elegirHora = useCallback((elegida: string | null) => {
    setHora(elegida === null || fecha === null ? null : { fecha, hora: elegida })
  }, [fecha])

  // "Confirmar cambio": si sale bien, este navegador cambia el link viejo por el nuevo y muestra el turno nuevo.
  async function confirmar() {
    if (fecha === null || horaDelDia === null) {
      return { estado: 'error' as const }
    }
    const resultado = await reprogramarTurno(id, credencial, fecha, horaDelDia)
    switch (resultado.estado) {
      case 'ok':
        reemplazarTurno(id, { id: resultado.id, token: resultado.token, fecha: resultado.turno.fecha, hora: resultado.turno.hora })
        navegar(linkDelTurno(resultado), { replace: true, state: { reprogramado: true } })
        return { estado: 'ok' as const }
      case 'ocupado':
        // HU-21 CA3: el turno viejo sigue igual; PasoHora avisa y actualiza la lista.
        return { estado: 'ocupado' as const }
      case 'fuera-de-plazo':
        return { estado: 'error' as const, mensaje: 'Ya falta poco para tu turno y no se puede cambiar desde acá. Escribile al local.' }
      default:
        return { estado: 'error' as const, mensaje: 'No pudimos cambiar el turno. Probá de nuevo.' }
    }
  }

  if (actual === null) {
    return <main className="reserva"><p className="reserva__ayuda" role="status">Buscando tu turno…</p></main>
  }
  // HU-21 CA4: si el link no sirve o el turno ya no se puede cambiar, se vuelve a "Mi turno", que explica por qué.
  if (actual.estado !== 'ok' || !actual.turno.puedeCambiar) {
    return (
      <main className="reserva">
        <h1 className="reserva__titulo">Este turno no se puede cambiar desde acá</h1>
        <Link className="btn btn-secondary" to={`/turno/${id}${conToken}`}>Ver mi turno</Link>
      </main>
    )
  }

  const { turno } = actual
  return (
    <>
      <p className="reprogramar__actual" role="note">
        Estás cambiando tu turno del {fechaLarga(turno.fecha)}, {turno.hora}. Si no confirmás, queda como está.
      </p>
      <Routes>
        <Route
          path="dia"
          element={
            <PasoDia
              servicios={SIN_SERVICIOS}
              profesional={DEL_TURNO}
              nombreProfesional={turno.profesionalNombre}
              atras={`/turno/${id}${conToken}`}
              fechaElegida={fecha}
              alElegirFecha={setFecha}
              cargarDias={cargarDias}
              siguiente={linkDeEstePaso('hora')}
              paso={null}
              sinLugar="No hay lugar en las próximas semanas. Si querés, escribile al local."
            />
          }
        />
        <Route
          path="hora"
          element={
            fecha === null
              ? <Navigate to={linkDeEstePaso('dia')} replace />
              : (
                <PasoHora
                  servicios={SIN_SERVICIOS}
                  profesional={DEL_TURNO}
                  nombreProfesional={turno.profesionalNombre}
                  fecha={fecha}
                  horaElegida={horaDelDia}
                  horaApartada={null}
                  alElegirHora={elegirHora}
                  alAvanzar={confirmar}
                  cargarHoras={cargarHoras}
                  atras={linkDeEstePaso('dia')}
                  paso={null}
                  textos={{ avanzar: 'Confirmar cambio', avanzando: 'Cambiando…' }}
                />
              )
          }
        />
        <Route path="*" element={<Navigate to={linkDeEstePaso('dia')} replace />} />
      </Routes>
    </>
  )
}

export default PaginaReprogramar
