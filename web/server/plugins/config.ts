import { loadConfig } from '../lib/config'

// Refuses to start when a setting is missing or too short. The error names every problem.
export default defineNitroPlugin(() => {
  loadConfig(process.env)
})
