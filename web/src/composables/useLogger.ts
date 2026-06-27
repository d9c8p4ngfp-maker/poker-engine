/**
 * Frontend operation logger for real-user testing.
 * Captures all user actions, WS messages, state changes, and errors
 * with timestamps. Saves to localStorage; can be downloaded as JSON.
 */
import { ref, computed } from 'vue'

interface LogEntry {
  ts: number        // Date.now()
  time: string      // HH:MM:SS.mmm
  category: 'action' | 'ws-send' | 'ws-recv' | 'state' | 'error' | 'lifecycle'
  detail: string
  data?: unknown
}

const MAX_ENTRIES = 5000
const STORAGE_KEY = 'poker_oplog'

const entries = ref<LogEntry[]>([])
const sessionId = (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16) })).slice(0, 8)

function fmtTime(ts: number) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}.${String(d.getMilliseconds()).padStart(3,'0')}`
}

function push(entry: Omit<LogEntry, 'ts' | 'time'>) {
  const ts = Date.now()
  const e: LogEntry = { ts, time: fmtTime(ts), ...entry }
  entries.value.push(e)
  // Trim from head if exceeds max
  if (entries.value.length > MAX_ENTRIES) {
    entries.value = entries.value.slice(-MAX_ENTRIES)
  }
  // Persist every 50 entries to avoid excessive writes
  if (entries.value.length % 50 === 0) {
    saveToStorage()
  }
}

function saveToStorage() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.value))
  } catch {
    // quota exceeded — trim half and retry
    entries.value = entries.value.slice(-Math.floor(MAX_ENTRIES / 2))
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.value)) } catch {}
  }
}

function loadFromStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) entries.value = JSON.parse(raw)
  } catch {}
}

// ── Public API ──

export function useLogger() {
  // Restore previous session logs on creation
  if (entries.value.length === 0) loadFromStorage()

  return {
    sessionId,
    entries: computed(() => entries.value),

    /** Record a user-initiated action */
    logAction(label: string, payload?: unknown) {
      push({ category: 'action', detail: label, data: payload })
      console.debug(`[LOG:${sessionId}] ACTION | ${label}`, payload ?? '')
    },

    /** Record an outgoing WS message */
    logWsSend(dest: string, payload?: unknown) {
      push({ category: 'ws-send', detail: dest, data: payload })
    },

    /** Record an incoming WS message */
    logWsRecv(dest: string, raw: unknown) {
      // Try to show summary instead of full body
      let data: unknown = raw
      try {
        const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
        if (typeof parsed === 'object' && parsed !== null) {
          const summary: Record<string, unknown> = {}
          for (const [k, v] of Object.entries(parsed)) {
            if (k === 'type') summary[k] = v
            else if (k === 'error') summary[k] = v
            else if (k === 'winners') summary[k] = `[${(v as unknown[]).length} entries]`
            else if (k === 'players') summary[k] = `[${(v as unknown[]).length} players]`
            else if (k === 'communityCards') summary[k] = `[${(v as unknown[]).length} cards]`
            else summary[k] = v
          }
          data = summary
        } else {
          data = parsed
        }
      } catch {}
      push({ category: 'ws-recv', detail: dest, data })
    },

    /** Record a state transition */
    logState(label: string, detail?: unknown) {
      push({ category: 'state', detail: label, data: detail })
    },

    /** Record an error */
    logError(label: string, err?: unknown) {
      push({ category: 'error', detail: label, data: err instanceof Error ? { message: err.message, stack: err.stack } : err })
      console.error(`[LOG:${sessionId}] ERROR | ${label}`, err)
    },

    /** Record lifecycle event */
    logLifecycle(label: string) {
      push({ category: 'lifecycle', detail: label })
    },

    /** Persist immediately */
    flush() {
      saveToStorage()
    },

    /** Download logs as JSON file */
    download() {
      const blob = new Blob([JSON.stringify(entries.value, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `poker-log-${sessionId}-${new Date().toISOString().slice(0,19).replace(/:/g,'-')}.json`
      a.click()
      URL.revokeObjectURL(url)
    },

    /** Clear all logs */
    clear() {
      entries.value = []
      localStorage.removeItem(STORAGE_KEY)
    },
  }
}
