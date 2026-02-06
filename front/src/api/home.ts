import { http } from './http'
import { endpoints } from './endpoints'
import { fetchListTextJsonWithRetry } from './api-text-json'
import { resolveSetupImageUrl } from './setups'

export type HomePopularProduct = {
  product_id: number
  name: string
  price: number
  sold_qty?: number | null
  thumbnail_url?: string | null
}

export type HomePopularSetup = {
  setup_id: number
  name: string
  short_desc?: string | null
  sold_qty?: number | null
  image_url?: string | null
}

export const listPopularProducts = async (limit = 8): Promise<HomePopularProduct[]> => {
  const payload = await fetchListTextJsonWithRetry(http, endpoints.homePopularProducts, {
    params: { limit },
    validateStatus: (status) => (status >= 200 && status < 300) || status === 304,
  })
  return payload as HomePopularProduct[]
}

export const listPopularSetups = async (limit = 6): Promise<HomePopularSetup[]> => {
  const payload = await fetchListTextJsonWithRetry(http, endpoints.homePopularSetups, {
    params: { limit },
    validateStatus: (status) => (status >= 200 && status < 300) || status === 304,
  })
  return (payload as HomePopularSetup[]).map((item) => ({
    ...item,
    image_url: resolveSetupImageUrl(item.image_url ?? undefined),
  }))
}
