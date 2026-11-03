// The VITE_* settings from the .env files (see .env.example), typed so typos are caught.
interface ImportMetaEnv {
  readonly VITE_API_MOCKING?: string
  readonly VITE_API_BASE_URL?: string
  /** delivery tracking maps (src/config/tracking.ts) */
  readonly VITE_MAP_TILE_URL?: string
  readonly VITE_MAP_ATTRIBUTION?: string
  readonly VITE_MAP_DEFAULT_CENTER?: string
}
