// Corre antes de cada archivo de test.
// Suma a expect los chequeos de Testing Library (toBeInTheDocument, toHaveTextContent...).
import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// findBy y waitFor esperan hasta que aparezca lo buscado, con un máximo. El de Testing Library (1 s) queda justo
// cuando corren todos los archivos en paralelo; con 3 s hay margen. No es una espera fija: si aparece antes, sigue.
configure({ asyncUtilTimeout: 3000 })

// Desmonta lo que dibujó cada test y borra lo que guardó en el navegador, así ninguno ve lo del anterior.
afterEach(() => {
  cleanup()
  localStorage.clear()
})
