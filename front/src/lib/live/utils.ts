import type { LiveItem } from './types'

export type LiveStatus = 'UPCOMING' | 'LIVE' | 'ENDED'

export const parseLiveDate = (value: string): Date => {
  if (!value) {
    return new Date(NaN)
  }

  const hasTimezone = /([zZ]|[+-]\d{2}:?\d{2})$/.test(value)
  if (hasTimezone) {
    return new Date(value)
  }

  const [datePart, timePart] = value.includes('T') ? value.split('T') : value.split(' ')
  if (!datePart) {
    return new Date(value)
  }

  const [yearRaw, monthRaw = '1', dayRaw = '1'] = datePart.split('-')
  const year = Number(yearRaw)
  const month = Number(monthRaw)
  const day = Number(dayRaw)
  if ([year, month, day].some((part) => Number.isNaN(part))) {
    return new Date(value)
  }

  const timePieces = (timePart ?? '').split(':')
  const hours = Number(timePieces[0] ?? 0) || 0
  const minutes = Number(timePieces[1] ?? 0) || 0
  const [secPart = '0', msPart = '0'] = (timePieces[2] ?? '0').split('.')
  const seconds = Number(secPart) || 0
  const milliseconds = Number(msPart) || 0

  return new Date(year, month - 1, day, hours, minutes, seconds, milliseconds)
}

export const getLiveStatus = (item: LiveItem, now: Date = new Date()): LiveStatus => {
  const startAt = parseLiveDate(item.startAt)
  const endAt = parseLiveDate(item.endAt)

  if (now < startAt) {
    return 'UPCOMING'
  }
  if (now >= endAt) {
    return 'ENDED'
  }
  return 'LIVE'
}

export const getDayWindow = (baseDate: Date): Date[] => {
  const days: Date[] = []
  const base = new Date(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate())

  for (let offset = -3; offset <= 6; offset += 1) {
    days.push(new Date(base.getFullYear(), base.getMonth(), base.getDate() + offset))
  }

  return days
}

export const isSameLocalDay = (a: Date, b: Date): boolean => {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

export const sortLivesByStartAt = (items: LiveItem[]): LiveItem[] => {
  return [...items].sort(
    (a, b) => parseLiveDate(a.startAt).getTime() - parseLiveDate(b.startAt).getTime(),
  )
}

export const filterLivesByDay = (items: LiveItem[], day: Date): LiveItem[] => {
  return items.filter((item) => isSameLocalDay(parseLiveDate(item.startAt), day))
}
