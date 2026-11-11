/// <reference types="vite/client" />

// Variables de entorno propias del front (se leen al compilar).
interface ImportMetaEnv {
  readonly VITE_DOMINIO_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
