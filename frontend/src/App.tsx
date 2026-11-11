import { useEffect, useState } from 'react'

import type { components } from './api/esquema'

// Lo que devuelve GET /api/health, generado desde el OpenAPI del back.
type HealthResponse = components['schemas']['HealthResponse']
function App() {
  const [salud, setSalud] = useState<HealthResponse | null>(null)
  const [sinConexion, setSinConexion] = useState(false)
    
  useEffect(() => {
    async function cargar() {
      try {
        const respuesta = await fetch('/api/health')
        if (respuesta.status !== 200 && respuesta.status !== 503) {
          setSinConexion(true)
          return
        }
        const datos: HealthResponse = await respuesta.json()
        setSalud(datos)
      } catch {
        setSinConexion(true)
      }
    }
    cargar()
  }, [])
   if (sinConexion) {
    return (
      <main>
        <h1>Slotly</h1>
        <p>No pudimos conectar con la API.</p>
      </main>
    )
  }
  if (!salud) {
    return (
      <main>
        <h1>Slotly</h1>
        <p>Cargando...</p>
      </main>
    )
  }
  return (
    <main>
      <h1>Slotly</h1>
      <p>API: {salud.estado}</p>
      <p>Base de datos: {salud.baseDeDatos}</p>
    </main>
  )
}
export default App