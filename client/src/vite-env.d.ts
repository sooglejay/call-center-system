/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BASE_PATH: string
  // 更多环境变量...
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
