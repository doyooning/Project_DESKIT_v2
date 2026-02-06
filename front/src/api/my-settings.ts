import { http } from './http'
import type { MySettingsResponse, MySettingsUpdateRequest } from './types/my-settings'

const SETTINGS_PATH = '/api/my/settings'
const withCredentials = { withCredentials: true }

export const getMySettings = async (): Promise<MySettingsResponse> => {
  const response = await http.get<MySettingsResponse>(SETTINGS_PATH, withCredentials)
  return response.data
}

export const updateMySettings = async (
  payload: MySettingsUpdateRequest,
): Promise<MySettingsResponse> => {
  const response = await http.patch<MySettingsResponse>(SETTINGS_PATH, payload, withCredentials)
  return response.data
}
