# Slotly · Front

React + TypeScript + Vite. La home de cada local se abre en su subdominio (`demo.localhost:5173`).

## Comandos

| Comando | Qué hace |
|---|---|
| `npm run dev` | Levanta el front en `http://localhost:5173`. Las llamadas a `/api` van al back (`localhost:8080`) por el proxy de Vite. |
| `npm test` | Corre los tests de componentes (Vitest + Testing Library). |
| `npm run lint` | Revisa el código con ESLint. |
| `npm run build` | Compila TypeScript y arma la versión de producción en `dist/`. |
| `npm run tipos` | Regenera `src/api/esquema.ts` desde la documentación de la API. Necesita el back corriendo. Correrlo cada vez que cambia una respuesta del back. |
