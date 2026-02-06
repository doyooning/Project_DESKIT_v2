type StompHeaders = Record<string, string>

type StompSubscription = {
  destination: string
  callback: (body: string, headers: StompHeaders) => void
}

const buildFrame = (command: string, headers: StompHeaders, body?: string): string => {
  const headerLines = Object.entries(headers)
    .map(([key, value]) => `${key}:${value}`)
    .join('\n')
  const payload = body ?? ''
  return `${command}\n${headerLines}\n\n${payload}\u0000`
}

export class SimpleStompClient {
  private socket: WebSocket | null = null
  private connected = false
  private connecting: Promise<void> | null = null
  private subscriptions = new Map<string, Set<StompSubscription['callback']>>()
  private pendingSubs: StompSubscription[] = []
  private url: string

  constructor(url: string) {
    this.url = url
  }

  isConnected(): boolean {
    return this.connected
  }

  isConnecting(): boolean {
    return this.connecting !== null
  }

  connect(): Promise<void> {
    if (this.connected) return Promise.resolve()
    if (this.connecting) return this.connecting

    this.connecting = new Promise((resolve, reject) => {
      this.socket = new WebSocket(this.url)
      this.socket.onopen = () => {
        if (!this.socket) return
        this.socket.send(
          buildFrame('CONNECT', {
            'accept-version': '1.2',
            'heart-beat': '10000,10000',
          }),
        )
      }
      this.socket.onmessage = (event) => this.handleMessage(event.data, resolve)
      this.socket.onerror = () => {
        this.connected = false
        this.connecting = null
        reject(new Error('WebSocket connection failed'))
      }
      this.socket.onclose = () => {
        this.connected = false
        this.connecting = null
      }
    })

    return this.connecting
  }

  subscribe(destination: string, callback: (body: string, headers: StompHeaders) => void): void {
    if (this.connected) {
      this.sendFrame('SUBSCRIBE', {
        id: `sub-${Date.now()}-${Math.random()}`,
        destination,
      })
      this.storeSubscription(destination, callback)
      return
    }

    this.pendingSubs.push({ destination, callback })
  }

  send(destination: string, body: string): void {
    this.sendFrame('SEND', { destination, 'content-type': 'application/json' }, body)
  }

  disconnect(): void {
    if (!this.socket) return
    if (this.isSocketOpen()) {
      this.sendFrame('DISCONNECT', {})
    }
    if (
      this.socket.readyState === WebSocket.OPEN ||
      this.socket.readyState === WebSocket.CONNECTING
    ) {
      this.socket.close()
    }
    this.socket = null
    this.connected = false
    this.connecting = null
    this.subscriptions.clear()
    this.pendingSubs = []
  }

  private storeSubscription(destination: string, callback: StompSubscription['callback']): void {
    const set = this.subscriptions.get(destination) ?? new Set()
    set.add(callback)
    this.subscriptions.set(destination, set)
  }

  private sendFrame(command: string, headers: StompHeaders, body?: string): void {
    if (!this.socket || !this.isSocketOpen()) return
    this.socket.send(buildFrame(command, headers, body))
  }

  private handleMessage(data: string, resolve: () => void): void {
    const payload = data.replace(/\r\n/g, '\n')
    const frames = payload.split('\u0000').filter((frame) => frame.trim().length > 0)
    frames.forEach((frame) => {
      const lines = frame.split('\n')
      const command = lines[0]
      let index = 1
      const headers: StompHeaders = {}
      for (; index < lines.length; index += 1) {
        const line = lines[index]
        if (line === undefined) continue
        if (line === '') {
          index += 1
          break
        }
        const [key, ...rest] = line.split(':')
        if (!key) continue
        headers[key] = rest.join(':')
      }
      const body = lines.slice(index).join('\n')

      if (command === 'CONNECTED') {
        this.connected = true
        const destinations = new Set<string>()
        for (const destination of this.subscriptions.keys()) {
          destinations.add(destination)
        }
        this.pendingSubs.forEach((sub) => {
          this.storeSubscription(sub.destination, sub.callback)
          destinations.add(sub.destination)
        })
        this.pendingSubs = []
        destinations.forEach((destination) => {
          this.sendFrame('SUBSCRIBE', {
            id: `sub-${Date.now()}-${Math.random()}`,
            destination,
          })
        })
        resolve()
        return
      }

      if (command === 'MESSAGE') {
        const destination = headers.destination
        if (!destination) return
        const callbacks = this.subscriptions.get(destination)
        if (!callbacks) return
        callbacks.forEach((cb) => cb(body, headers))
      }
    })
  }

  private isSocketOpen(): boolean {
    return this.socket?.readyState === WebSocket.OPEN
  }
}
