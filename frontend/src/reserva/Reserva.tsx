import { useCallback, useEffect, useRef, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router'

import { obtenerProfesionales } from '../api/publico'
import { apartar, confirmar, type ResultadoApartar, type ResultadoConfirmar } from '../api/reserva'
import type { ApartadoCreado, LocalPublico, ProfesionalPublico } from '../api/tipos'
import { CUALQUIERA } from './constantes'
import { DATOS_VACIOS, type DatosCliente } from './datosCliente'
import { leerCliente, linkDelTurno, recordarCliente, recordarTurno } from './memoria'
import PasoDatos from './PasoDatos'
import PasoDia from './PasoDia'
import PasoHora from './PasoHora'
import PasoProfesional from './PasoProfesional'
import PasoServicios from './PasoServicios'
import './reserva.css'

type Props = {
  local: LocalPublico
}

// El apartado vigente y cuándo se creó en este navegador (para la cuenta regresiva, sin depender de su reloj).
type ApartadoVigente = { apartado: ApartadoCreado; creadoEnMs: number }

// "Reservar lo mismo" (HU-23): llega en el estado de la navegación desde "Mis turnos".
type Repetir = { servicioIds: string[]; profesionalId: string }

// El asistente de reserva. Lo que el cliente va eligiendo vive acá, arriba de los pasos:
// así, al volver con "Atrás" (o con el botón del navegador), la selección sigue marcada (HU-05 CA3).
function Reserva({ local }: Props) {
  const navegar = useNavigate()
  const ubicacion = useLocation()
  // HU-23: si viene de "Reservar lo mismo", los servicios que siguen activos quedan elegidos (se lee una sola vez).
  const [repetir] = useState<Repetir | null>(
    () => (ubicacion.state as { repetir?: Repetir } | null)?.repetir ?? null)
  const [serviciosElegidos, setServiciosElegidos] = useState<string[]>(() => (repetir === null ? []
    : repetir.servicioIds.filter((id) => local.servicios.some((s) => s.id === id))))
  // HU-23 CA3: si algún servicio ya no está, se arranca en el paso 1 con los que quedan y un aviso.
  const [avisoRepetir, setAvisoRepetir] = useState<string | null>(() => (
    repetir !== null && repetir.servicioIds.some((id) => !local.servicios.some((s) => s.id === id))
      ? 'Algún servicio de ese turno ya no está disponible. Revisá lo que querés reservar.'
      : null))
  const yaSeRepitio = useRef(false)
  // Quiénes hacen todos los servicios elegidos (null = todavía no se pidieron para esta selección).
  const [profesionales, setProfesionales] = useState<ProfesionalPublico[] | null>(null)
  // El id del profesional elegido, CUALQUIERA, o null si todavía no eligió.
  const [profesionalElegido, setProfesionalElegido] = useState<string | null>(null)
  // true si había un solo profesional y el paso 2 se salteó (HU-06 CA3).
  const [pasoProfesionalSalteado, setPasoProfesionalSalteado] = useState(false)
  // El día elegido en el paso 3 ("2026-11-17"), o null.
  const [fechaElegida, setFechaElegida] = useState<string | null>(null)
  // La hora elegida en el paso 4, junto con su día: si después se elige otro día, esa hora ya no vale.
  const [horaElegida, setHoraElegida] = useState<{ fecha: string; hora: string } | null>(null)
  const horaDelDia = horaElegida !== null && horaElegida.fecha === fechaElegida ? horaElegida.hora : null
  const elegirHora = useCallback((hora: string | null) => {
    setHoraElegida(hora === null || fechaElegida === null ? null : { fecha: fechaElegida, hora })
  }, [fechaElegida])
  const [buscando, setBuscando] = useState(false)
  const [errorAlBuscar, setErrorAlBuscar] = useState(false)
  // El horario apartado al pasar al paso 5 (HU-11) y los datos del formulario.
  // HU-10 CA9: si ya reservó desde este navegador, nombre y celular vienen precargados.
  const [apartado, setApartado] = useState<ApartadoVigente | null>(null)
  const [datos, setDatos] = useState<DatosCliente>(() => ({ ...DATOS_VACIOS, ...leerCliente() }))

  // HU-23 CA2: con todos los servicios activos, se piden los profesionales y, si el de ese turno sigue, se pasa
  // directo al paso 3 (día). Si ya no está, se elige otro en el paso 2.
  useEffect(() => {
    if (repetir === null || avisoRepetir !== null || yaSeRepitio.current) {
      return
    }
    yaSeRepitio.current = true
    obtenerProfesionales(repetir.servicioIds).then((resultado) => {
      if (resultado.estado === 'error') {
        return // queda en el paso 1 con los servicios elegidos
      }
      setProfesionales(resultado.profesionales)
      if (resultado.profesionales.some((p) => p.id === repetir.profesionalId)) {
        setProfesionalElegido(repetir.profesionalId)
        setPasoProfesionalSalteado(resultado.profesionales.length === 1)
        navegar('/reservar/dia', { replace: true })
      } else {
        setAvisoRepetir('El profesional de ese turno ya no está disponible para esos servicios. Elegí otro.')
        navegar('/reservar/profesional', { replace: true })
      }
    })
  }, [repetir, avisoRepetir, navegar])

  // Si cambian los servicios, lo elegido después ya no vale: se vuelve a pedir y a elegir.
  function cambiarServicios(ids: string[]) {
    setAvisoRepetir(null)
    setServiciosElegidos(ids)
    setProfesionales(null)
    setProfesionalElegido(null)
    setPasoProfesionalSalteado(false)
    setFechaElegida(null)
  }

  // Con otro profesional cambian los días con lugar: el día se vuelve a elegir.
  function elegirProfesional(id: string) {
    if (id !== profesionalElegido) {
      setProfesionalElegido(id)
      setFechaElegida(null)
    }
  }

  // "Siguiente" en el paso 1: se piden los profesionales antes de avanzar, para saber si hay que saltar el paso 2.
  async function avanzarDesdeServicios() {
    setBuscando(true)
    setErrorAlBuscar(false)
    const resultado = await obtenerProfesionales(serviciosElegidos)
    setBuscando(false)
    if (resultado.estado === 'error') {
      setErrorAlBuscar(true)
      return
    }
    setProfesionales(resultado.profesionales)
    if (resultado.profesionales.length === 1) {
      // HU-06 CA3: con un solo profesional no hay nada que elegir; queda elegido y se pasa al día.
      setProfesionalElegido(resultado.profesionales[0].id)
      setPasoProfesionalSalteado(true)
      navegar('/reservar/dia')
    } else {
      setPasoProfesionalSalteado(false)
      navegar('/reservar/profesional')
    }
  }

  // "Siguiente" en el paso 4: se aparta la hora antes de pasar al formulario (HU-11 CA1). Si ya había un apartado
  // (volvió y eligió otra hora), se manda para liberarlo (CA3).
  async function avanzarDesdeHora(): Promise<ResultadoApartar> {
    if (profesionalElegido === null || fechaElegida === null || horaDelDia === null) {
      return { estado: 'error' }
    }
    const resultado = await apartar({
      servicios: serviciosElegidos,
      profesional: profesionalElegido,
      fecha: fechaElegida,
      hora: horaDelDia,
      anterior: apartado ? { id: apartado.apartado.id, token: apartado.apartado.token } : undefined,
    })
    if (resultado.estado === 'ok') {
      setApartado({ apartado: resultado.apartado, creadoEnMs: Date.now() })
      navegar('/reservar/datos')
    }
    return resultado
  }

  // "Confirmar" en el paso 5.
  async function confirmarReserva(): Promise<ResultadoConfirmar> {
    if (apartado === null) {
      return { estado: 'vencido' }
    }
    const resultado = await confirmar(apartado.apartado.id, {
      token: apartado.apartado.token,
      nombre: datos.nombre.trim(),
      telefono: datos.telefono.trim(),
      email: datos.email.trim() === '' ? null : datos.email.trim(),
      formaPago: datos.formaPago,
      consentimiento: datos.consentimiento,
    })
    if (resultado.estado === 'ok') {
      // HU-10 CA9: el navegador recuerda al cliente y el link de su turno.
      const { id, token } = apartado.apartado
      recordarCliente({ nombre: datos.nombre.trim(), telefono: datos.telefono.trim() })
      recordarTurno({ id, token, fecha: resultado.turno.fecha, hora: resultado.turno.hora })
      // La confirmación es "Mi turno": recargarla vuelve a pedir el mismo turno (CA7). replace: "Atrás" no vuelve
      // al formulario de un turno ya confirmado.
      navegar(linkDelTurno({ id, token }), { replace: true, state: { recienConfirmado: true } })
    }
    return resultado
  }

  const nombreElegido = profesionalElegido === CUALQUIERA
    ? 'Cualquiera disponible'
    : profesionales?.find((p) => p.id === profesionalElegido)?.nombre ?? null

  // La hora que este cliente tiene apartada ese día: se le sigue mostrando aunque para los demás esté ocupada.
  const horaApartada = apartado !== null && apartado.apartado.fecha === fechaElegida ? apartado.apartado.hora : null

  return (
    <>
      {avisoRepetir && <p className="reprogramar__actual" role="status">{avisoRepetir}</p>}
      <Routes>
        <Route
          path="servicios"
          element={
            <PasoServicios
              servicios={local.servicios}
              elegidos={serviciosElegidos}
              alCambiarElegidos={cambiarServicios}
              alAvanzar={avanzarDesdeServicios}
              buscando={buscando}
              error={errorAlBuscar}
            />
          }
        />
        <Route
          path="profesional"
          element={
            // Si se entra directo (o se recarga) sin haber pasado por el paso 1, se vuelve ahí.
            profesionales === null
              ? <Navigate to="/reservar/servicios" replace />
              : (
                <PasoProfesional
                  profesionales={profesionales}
                  elegido={profesionalElegido}
                  alElegir={elegirProfesional}
                />
              )
          }
        />
        <Route
          path="dia"
          element={
            nombreElegido === null || profesionalElegido === null
              ? <Navigate to="/reservar/servicios" replace />
              : (
                <PasoDia
                  // Si cambian los servicios o el profesional, el calendario arranca de cero.
                  key={`${profesionalElegido}|${serviciosElegidos.join(',')}`}
                  servicios={serviciosElegidos}
                  profesional={profesionalElegido}
                  nombreProfesional={nombreElegido}
                  // HU-06 CA3: si el paso 2 se salteó, "Atrás" vuelve al paso 1.
                  atras={pasoProfesionalSalteado ? '/reservar/servicios' : '/reservar/profesional'}
                  fechaElegida={fechaElegida}
                  alElegirFecha={setFechaElegida}
                />
              )
          }
        />
        <Route
          path="hora"
          element={
            nombreElegido === null || profesionalElegido === null || fechaElegida === null
              ? <Navigate to="/reservar/servicios" replace />
              : (
                <PasoHora
                  servicios={serviciosElegidos}
                  profesional={profesionalElegido}
                  nombreProfesional={nombreElegido}
                  fecha={fechaElegida}
                  horaElegida={horaDelDia}
                  horaApartada={horaApartada}
                  alElegirHora={elegirHora}
                  alAvanzar={avanzarDesdeHora}
                />
              )
          }
        />
        <Route
          path="datos"
          element={
            apartado === null || fechaElegida === null || horaDelDia === null
              ? <Navigate to="/reservar/servicios" replace />
              : (
                <PasoDatos
                  apartado={apartado.apartado}
                  creadoEnMs={apartado.creadoEnMs}
                  servicios={local.servicios.filter((s) => serviciosElegidos.includes(s.id))}
                  datos={datos}
                  alCambiarDatos={setDatos}
                  alConfirmar={confirmarReserva}
                />
              )
          }
        />
        <Route path="*" element={<Navigate to="/reservar/servicios" replace />} />
      </Routes>
    </>
  )
}

export default Reserva
