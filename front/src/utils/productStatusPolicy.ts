export type ProductStatus =
  | 'DRAFT'
  | 'READY'
  | 'ON_SALE'
  | 'LIMITED_SALE'
  | 'SOLD_OUT'
  | 'PAUSED'
  | 'HIDDEN'
  | 'DELETED'

export const isVisibleToUser = (status?: ProductStatus): boolean =>
  status === 'ON_SALE' ||
  status === 'LIMITED_SALE' ||
  status === 'SOLD_OUT' ||
  status === 'PAUSED'

export const isVisibleToSeller = (status?: ProductStatus): boolean =>
  status === 'DRAFT' ||
  status === 'READY' ||
  status === 'ON_SALE' ||
  status === 'LIMITED_SALE' ||
  status === 'SOLD_OUT' ||
  status === 'PAUSED' ||
  status === 'HIDDEN'

export const canEdit = (status?: ProductStatus): boolean =>
  status === 'DRAFT' ||
  status === 'READY' ||
  status === 'PAUSED' ||
  status === 'HIDDEN'

export const canStartSale = (status?: ProductStatus): boolean =>
  status === 'READY' || status === 'PAUSED' || status === 'SOLD_OUT'

export const canPause = (status?: ProductStatus): boolean => status === 'ON_SALE'

export const canResume = (status?: ProductStatus): boolean =>
  status === 'PAUSED' || status === 'SOLD_OUT'

export const isSoldOut = (status?: ProductStatus): boolean => status === 'SOLD_OUT'

export const isLimitedSale = (status?: ProductStatus): boolean => status === 'LIMITED_SALE'
