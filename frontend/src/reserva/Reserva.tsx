import { useCallback, useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router'

import { obtenerProfesionales } from '../api/publico'
import { apartar, confirmar, type ResultadoApartar, type ResultadoConfirmar } from '../api/reserva'
import type { ApartadoCreado, LocalPublico, ProfesionalPublico, TurnoPublico } from '../api/tipos'
import { CUALQUIERA } from './constantes'
import { DATOS_VACIOS, type DatosCliente } from './datosCliente'
import PasoConfirmacion from './PasoConfirmacion'
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

// El asistente de reserva. Lo que el cliente va eligiendo vive acá, arriba de los pasos:
// así, al volver con "Atrás" (o con el botón del navegador), la selección sigue marcada (HU-05 CA3).
function Reserva({ local }: Props) {
  const navegar = useNavigate()
  const [serviciosElegidos, setServiciosElegidos] = useState<string[]>([])
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
  // El horario apartado al pasar al paso 5 (HU-11), los datos del formulario y el turno ya confirmado.
  const [apartado, setApartado] = useState<ApartadoVigente | null>(null)
  const [datos, setDatos] = useState<DatosCliente>(DATOS_VACIOS)
  const [turno, setTurno] = useState<TurnoPublico | null>(null)

  // Si cambian los servicios, lo elegido después ya no vale: se vuelve a pedir y a elegir.
  function cambiarServicios(ids: string[]) {
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
      setTurno(resultado.turno)
      navegar('/reservar/confirmacion', { replace: true })
    }
    return resultado
  }

  const nombreElegido = profesionalElegido === CUALQUIERA
    ? 'Cualquiera disponible'
    : profesionales?.find((p) => p.id === profesionalElegido)?.nombre ?? null

  // La hora que este cliente tiene apartada ese día: se le sigue mostrando aunque para los demás esté ocupada.
  const horaApartada = apartado !== null && apartado.apartado.fecha === fechaElegida ? apartado.apartado.hora : null

  return (
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
      <Route
        path="confirmacion"
        element={turno === null ? <Navigate to="/reservar/servicios" replace /> : <PasoConfirmacion turno={turno} />}
      />
      <Route path="*" element={<Navigate to="/reservar/servicios" replace />} />
    </Routes>
  )
}

export default Reserva
