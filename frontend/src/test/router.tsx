import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'

// Dibuja un componente dentro de un router en memoria, parado en esa dirección.
// Hace falta para todo lo que usa Link, useNavigate o Routes.
export function renderConRouter(ui: ReactElement, ruta = '/') {
  return render(<MemoryRouter initialEntries={[ruta]}>{ui}</MemoryRouter>)
}
