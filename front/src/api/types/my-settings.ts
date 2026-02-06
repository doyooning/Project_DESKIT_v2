export type MySettingsResponse = {
  mbti: string
  job_category: string
  marketing_agreed: boolean
}

export type MySettingsUpdateRequest = {
  mbti?: string
  job_category?: string
  marketing_agreed?: boolean
}
