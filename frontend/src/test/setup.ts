// Corre antes de cada archivo de test.
// Suma a expect los chequeos de Testing Library (toBeInTheDocument, toHaveTextContent...).
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Desmonta lo que dibujó cada test, así ninguno ve lo del anterior.
afterEach(() => {
  cleanup()
})
