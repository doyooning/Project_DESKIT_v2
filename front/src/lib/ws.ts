const joinBasePath = (basePath: string, path: string): string => {
    const normalizedBase = basePath.replace(/\/+$/, '')
    const normalizedPath = path.startsWith('/') ? path : `/${path}`
    return `${normalizedBase}${normalizedPath}`
}

const resolveBaseUrl = (apiBase?: string): URL | null => {
    if (!apiBase) return null
    try {
        if (apiBase.startsWith('/')) {
            if (typeof window !== 'undefined') {
                return new URL(apiBase, window.location.origin)
            }
            return new URL(`http://localhost${apiBase}`)
        }
        return new URL(apiBase)
    } catch (error) {
        console.warn('[ws] invalid apiBase, fallback to location', error)
        return null
    }
}

export const resolveWsUrl = (apiBase?: string, path = '/ws'): string => {
    const baseUrl = resolveBaseUrl(apiBase)
    if (baseUrl) {
        const protocol = baseUrl.protocol === 'https:' ? 'wss:' : 'ws:'
        const basePath = baseUrl.pathname && baseUrl.pathname !== '/' ? baseUrl.pathname : ''
        return `${protocol}//${baseUrl.host}${joinBasePath(basePath, path)}`
    }

    if (typeof window !== 'undefined') {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        return `${protocol}//${window.location.host}${path}`
    }

    return `ws://localhost${path}`
}

export const resolveSockJsUrl = (apiBase?: string, path = '/ws-public'): string => {
    const baseUrl = resolveBaseUrl(apiBase)
    if (baseUrl) {
        const protocol = baseUrl.protocol === 'https:' ? 'https:' : 'http:'
        const basePath = baseUrl.pathname && baseUrl.pathname !== '/' ? baseUrl.pathname : ''
        return `${protocol}//${baseUrl.host}${joinBasePath(basePath, path)}`
    }

    if (typeof window !== 'undefined') {
        const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:'
        return `${protocol}//${window.location.host}${path}`
    }

    return `http://localhost${path}`
}
