import { ref, readonly } from 'vue'
import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { API_BASE_URL } from '../config'

const connected = ref(false)
const lastMessage = ref<string | null>(null)

let stompClient: Client | null = null
const activeSubscriptions = new Map<string, any>()

function getWsUrl(): string {
  // 生产环境: 用 API_BASE_URL 构造完整 WebSocket 地址
  // 本地开发: 用相对路径，走 Vite proxy
  const base = API_BASE_URL || window.location.origin
  const url = new URL('/ws', base)
  url.searchParams.set('playerId', userPlayerId)
  // SockJS 需要完整 URL（含 http(s)://）
  if (API_BASE_URL) return url.toString()
  return url.pathname + url.search
}

let userPlayerId = ''

// Optional logger — set via setLogger() before connect()
let wsLogger: { logWsSend: (dest: string, payload?: unknown) => void; logWsRecv: (dest: string, raw: unknown) => void; logError: (label: string, err?: unknown) => void } | null = null

export function setWsLogger(logger: typeof wsLogger) {
  wsLogger = logger
}

export function useWebSocket(playerId?: string) {
  if (playerId) userPlayerId = playerId
  function connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (stompClient?.active) {
        resolve()
        return
      }

      let resolved = false
      stompClient = new Client({
        webSocketFactory: () => new SockJS(getWsUrl()),
        reconnectDelay: 2000,
        heartbeatIncoming: 30000,
        heartbeatOutgoing: 30000,
        onConnect: () => {
          connected.value = true
          console.log('[WS] Connected to server')
          if (!resolved) { resolved = true; resolve() }
        },
        onDisconnect: () => {
          connected.value = false
          console.log('[WS] Disconnected, reconnecting...')
          if (!resolved) { resolved = true; reject(new Error('Disconnected')) }
        },
        onStompError: (frame) => {
          console.error('[WS] STOMP error:', frame.headers['message'])
          wsLogger?.logError('ws-stomp-error', frame.headers['message'])
          if (!resolved) { resolved = true; reject(new Error(frame.headers['message'])) }
        },
      })

      stompClient.activate()
    })
  }

  function disconnect() {
    if (stompClient?.active) {
      stompClient.deactivate()
    }
    stompClient = null
    connected.value = false
  }

  function subscribe(destination: string, callback: (message: IMessage) => void) {
    if (!stompClient?.active) {
      console.warn('[WS] Cannot subscribe: not connected')
      return
    }
    const existing = activeSubscriptions.get(destination)
    if (existing) {
      try { existing.unsubscribe() } catch (_) { /* ignore */ }
      activeSubscriptions.delete(destination)
    }
    const sub = stompClient.subscribe(destination, (msg) => {
      wsLogger?.logWsRecv(destination, msg.body)
      callback(msg)
    })
    activeSubscriptions.set(destination, sub)
  }

  function send(destination: string, body: string | object = {}) {
    if (!stompClient?.active) {
      console.warn('[WS] Cannot send: not connected')
      return
    }
    const payload = typeof body === 'string' ? body : JSON.stringify(body)
    wsLogger?.logWsSend(destination, typeof body === 'string' ? body : body)
    stompClient.publish({
      destination,
      body: payload,
    })
  }

  return {
    connected: readonly(connected),
    lastMessage: readonly(lastMessage),
    connect,
    disconnect,
    subscribe,
    send,
  }
}
