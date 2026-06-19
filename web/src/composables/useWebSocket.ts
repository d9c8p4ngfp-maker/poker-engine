import { ref, readonly } from 'vue'
import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const connected = ref(false)
const lastMessage = ref<string | null>(null)

let stompClient: Client | null = null

function getWsUrl(): string {
  const loc = window.location
  const protocol = loc.protocol === 'https:' ? 'https:' : 'http:'
  const host = import.meta.env.DEV ? `${loc.hostname}:8080` : loc.host
  return `${protocol}//${host}/ws`
}

export function useWebSocket() {
  function connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (stompClient?.active) {
        resolve()
        return
      }

      stompClient = new Client({
        webSocketFactory: () => new SockJS(getWsUrl()),
        reconnectDelay: 3000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          connected.value = true
          console.log('[WS] Connected to server')
          resolve()
        },
        onDisconnect: () => {
          connected.value = false
          console.log('[WS] Disconnected from server')
        },
        onStompError: (frame) => {
          console.error('[WS] STOMP error:', frame.headers['message'])
          reject(new Error(frame.headers['message']))
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
    stompClient.subscribe(destination, callback)
  }

  function send(destination: string, body: string | object = {}) {
    if (!stompClient?.active) {
      console.warn('[WS] Cannot send: not connected')
      return
    }
    const payload = typeof body === 'string' ? body : JSON.stringify(body)
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
