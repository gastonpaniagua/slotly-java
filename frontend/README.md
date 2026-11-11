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

## Variables de entorno

| Variable | Por defecto | Qué es |
|---|---|---|
| `VITE_DOMINIO_BASE` | `localhost` | Dominio del que cuelgan los locales. Al entrar sin subdominio se muestra la página de Slotly. En producción se compila con `VITE_DOMINIO_BASE=slotly.com.ar` (el mismo valor que `slotly.dominio-base` del back). |
