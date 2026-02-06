<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { createAddress, deleteAddress, getMyAddresses, updateAddress } from '../api/addresses'
import type { AddressResponse } from '../api/types/addresses'

const router = useRouter()
const MAX_ADDRESS_COUNT = 3

const addresses = ref<AddressResponse[]>([])
const isLoading = ref(false)
const errorMessage = ref('')
const isFormOpen = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  receiver: '',
  postcode: '',
  address1: '',
  address2: '',
  isDefault: false,
})

const errors = reactive({
  receiver: '',
  postcode: '',
  address1: '',
})

let postcodeScriptPromise: Promise<void> | null = null

const canAdd = computed(() => addresses.value.length < MAX_ADDRESS_COUNT)
const isEditing = computed(() => editingId.value !== null)

const loadAddresses = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const response = await getMyAddresses()
    addresses.value = Array.isArray(response) ? response : []
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      router.push('/login').catch(() => {})
      return
    }
    errorMessage.value = '배송지 정보를 불러오지 못했습니다.'
    addresses.value = []
  } finally {
    isLoading.value = false
  }
}

const openCreateForm = () => {
  if (!canAdd.value) {
    window.alert('배송지는 최대 3개까지 입력 가능해요.')
    return
  }
  editingId.value = null
  resetForm()
  isFormOpen.value = true
}

const openEditForm = (address: AddressResponse) => {
  editingId.value = address.address_id
  form.receiver = address.receiver
  form.postcode = address.postcode
  form.address1 = address.addr_detail
  form.address2 = ''
  form.isDefault = Boolean(address.is_default)
  errors.receiver = ''
  errors.postcode = ''
  errors.address1 = ''
  errorMessage.value = ''
  isFormOpen.value = true
}

const closeForm = () => {
  isFormOpen.value = false
  editingId.value = null
  resetForm()
}

const resetForm = () => {
  form.receiver = ''
  form.postcode = ''
  form.address1 = ''
  form.address2 = ''
  form.isDefault = false
  errors.receiver = ''
  errors.postcode = ''
  errors.address1 = ''
  errorMessage.value = ''
}

const validate = () => {
  errors.receiver = ''
  errors.postcode = ''
  errors.address1 = ''

  const receiver = form.receiver.trim()
  const postcode = form.postcode.trim()
  const address1 = form.address1.trim()

  if (!receiver) {
    errors.receiver = '수령인을 입력해주세요.'
  } else if (!/^[a-zA-Z가-힣]{1,20}$/.test(receiver)) {
    errors.receiver = '이름은 영문/한글 20글자 이내로 입력해주세요.'
  }

  if (!postcode) {
    errors.postcode = '우편번호를 입력해주세요.'
  } else if (!/^[0-9]{5}$/.test(postcode)) {
    errors.postcode = '우편번호는 5자리 숫자입니다.'
  }

  if (!address1) {
    errors.address1 = '주소를 입력해주세요.'
  }

  form.receiver = receiver
  form.postcode = postcode
  form.address1 = address1
  form.address2 = form.address2.trim()

  return !errors.receiver && !errors.postcode && !errors.address1
}

const loadPostcodeScript = () => {
  if (typeof window === 'undefined') return Promise.reject(new Error('window missing'))
  if ((window as any).daum?.Postcode) return Promise.resolve()
  if (!postcodeScriptPromise) {
    postcodeScriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = '//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'
      script.async = true
      script.onload = () => resolve()
      script.onerror = () => reject(new Error('postcode script load failed'))
      document.head.appendChild(script)
    })
  }
  return postcodeScriptPromise
}

const openPostcode = async () => {
  await loadPostcodeScript()
  const daumPostcode = (window as any).daum?.Postcode
  if (!daumPostcode) return

  new daumPostcode({
    oncomplete: (data: any) => {
      const roadAddr = data.roadAddress || ''
      const jibunAddr = data.jibunAddress || ''
      let extraRoadAddr = ''

      if (data.bname && /[가-힣]/.test(data.bname)) {
        extraRoadAddr += data.bname
      }
      if (data.buildingName && data.apartment === 'Y') {
        extraRoadAddr += extraRoadAddr ? `, ${data.buildingName}` : data.buildingName
      }
      if (extraRoadAddr) {
        extraRoadAddr = ` (${extraRoadAddr})`
      }

      const address1 = roadAddr ? `${roadAddr}${extraRoadAddr}` : jibunAddr
      form.postcode = data.zonecode
      form.address1 = address1
    },
  }).open()
}

const handleSubmit = async () => {
  if (!isEditing.value && !canAdd.value) {
    errorMessage.value = '배송지는 최대 3개까지 등록할 수 있습니다.'
    return
  }
  if (!validate()) return

  const addrDetail = [form.address1, form.address2]
    .map((value) => value.trim())
    .filter((value) => value.length > 0)
    .join(' ')

  try {
    if (isEditing.value && editingId.value !== null) {
      await updateAddress(editingId.value, {
        receiver: form.receiver,
        postcode: form.postcode,
        addr_detail: addrDetail,
        is_default: form.isDefault,
      })
    } else {
      await createAddress({
        receiver: form.receiver,
        postcode: form.postcode,
        addr_detail: addrDetail,
        is_default: form.isDefault,
      })
    }
    await loadAddresses()
    closeForm()
  } catch (error: any) {
    const status = error?.response?.status
    const serverMessage = String(error?.response?.data?.message ?? '').trim()
    if (status === 400 && serverMessage) {
      if (serverMessage.includes('address limit exceeded')) {
        errorMessage.value = '배송지는 최대 3개까지 등록할 수 있습니다.'
      } else if (serverMessage.includes('receiver required')) {
        errorMessage.value = '수령인을 입력해주세요.'
      } else if (serverMessage.includes('postcode invalid')) {
        errorMessage.value = '우편번호를 확인해주세요.'
      } else if (serverMessage.includes('addr_detail required')) {
        errorMessage.value = '주소를 입력해주세요.'
      } else {
        errorMessage.value = '배송지 등록에 실패했습니다.'
      }
      return
    }
    if (status === 400) {
      errorMessage.value = '배송지 등록에 실패했습니다.'
      return
    }
    if (status === 401 || status === 403) {
      router.push('/login').catch(() => {})
      return
    }
    errorMessage.value = '배송지 등록에 실패했습니다.'
  }
}

const handleDelete = async (address: AddressResponse) => {
  if (!window.confirm('배송지를 삭제하시겠습니까?')) {
    return
  }
  try {
    await deleteAddress(address.address_id)
    await loadAddresses()
    if (editingId.value === address.address_id) {
      closeForm()
    }
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      router.push('/login').catch(() => {})
      return
    }
    errorMessage.value = '배송지 삭제에 실패했습니다.'
  }
}

onMounted(() => {
  loadAddresses()
})
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="배송지 관리" />

    <div class="address-wrap">
      <section class="address-panel">
        <div class="panel-head">
          <div>
            <h3 class="panel-title">등록된 배송지</h3>
            <p class="panel-sub">총 {{ addresses.length }}개 / 최대 {{ MAX_ADDRESS_COUNT }}개</p>
          </div>
          <button type="button" class="btn ghost" @click="openCreateForm">배송지 등록</button>
        </div>

        <div v-if="isLoading" class="empty-card">
          <p>배송지 정보를 불러오는 중입니다.</p>
        </div>
        <div v-else-if="!addresses.length" class="empty-card">
          <p>등록된 배송지가 없습니다.</p>
          <p class="muted">배송지를 등록해 빠르게 주문해보세요.</p>
        </div>
        <div v-else class="address-grid">
          <article v-for="address in addresses" :key="address.address_id" class="address-card">
            <div class="card-head">
              <div>
                <p class="receiver">{{ address.receiver }}</p>
                <p class="postcode">{{ address.postcode }}</p>
              </div>
              <div class="card-actions">
                <span v-if="address.is_default" class="badge">기본</span>
                <button type="button" class="icon-btn" @click="handleDelete(address)">X</button>
              </div>
            </div>
            <p class="addr-detail">{{ address.addr_detail }}</p>
            <div class="card-footer">
              <button type="button" class="btn ghost" @click="openEditForm(address)">수정</button>
            </div>
          </article>
        </div>
      </section>

      <section v-if="isFormOpen" class="address-panel">
        <div class="panel-head">
          <div>
            <h3 class="panel-title">{{ isEditing ? '배송지 수정' : '새 배송지 등록' }}</h3>
            <p class="panel-sub">기본 배송지는 주문 시 자동으로 선택됩니다.</p>
          </div>
          <span v-if="!canAdd" class="limit-pill">최대 등록 완료</span>
        </div>

        <div class="form">
          <div class="field">
            <label for="receiver">수령인</label>
            <input
              id="receiver"
              v-model="form.receiver"
              type="text"
              placeholder="이름을 입력해주세요"
              maxlength="20"
              @blur="validate"
            />
            <p v-if="errors.receiver" class="error">{{ errors.receiver }}</p>
          </div>

          <div class="field">
            <label for="postcode">우편번호</label>
            <div class="field-row">
              <input
                id="postcode"
                v-model="form.postcode"
                type="text"
                placeholder="12345"
                maxlength="5"
                inputmode="numeric"
                pattern="\\d{5}"
                readonly
                @blur="validate"
              />
              <button type="button" class="btn ghost btn-inline" @click="openPostcode">
                우편번호 찾기
              </button>
            </div>
            <p v-if="errors.postcode" class="error">{{ errors.postcode }}</p>
          </div>

          <div class="field">
            <label for="address1">주소</label>
            <input
              id="address1"
              v-model="form.address1"
              type="text"
              placeholder="주소를 입력해주세요"
              readonly
              @blur="validate"
            />
            <p v-if="errors.address1" class="error">{{ errors.address1 }}</p>
          </div>

          <div class="field">
            <label for="address2">상세주소</label>
            <input
              id="address2"
              v-model="form.address2"
              type="text"
              placeholder="예) 101동 101호"
            />
          </div>

          <div class="field field--checkbox">
            <label class="checkbox">
              <input v-model="form.isDefault" type="checkbox" />
              <span>기본 배송지로 설정</span>
            </label>
          </div>

          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

          <div class="actions">
            <button type="button" class="btn ghost" @click="closeForm">닫기</button>
            <button
              type="button"
              class="btn primary"
              :disabled="!isEditing && !canAdd"
              @click="handleSubmit"
            >
              {{ isEditing ? '수정 완료' : '배송지 등록' }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.address-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 960px;
  margin: 0 auto;
}

.address-panel {
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 900;
  color: var(--text-strong);
}

.panel-sub {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
}

.limit-pill {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--surface-weak);
  font-size: 12px;
  font-weight: 800;
  color: var(--text-muted);
}

.empty-card {
  border: 1px dashed var(--border-color);
  border-radius: 12px;
  padding: 14px;
  color: var(--text-muted);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.muted {
  margin: 0;
  font-weight: 700;
}

.address-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.address-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 12px;
  background: var(--surface-weak);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.card-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.receiver {
  margin: 0;
  font-weight: 900;
  color: var(--text-strong);
}

.postcode {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 700;
}

.badge {
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(34, 197, 94, 0.4);
  color: #16a34a;
  background: rgba(34, 197, 94, 0.1);
  font-weight: 800;
  font-size: 12px;
}

.addr-detail {
  margin: 0;
  color: var(--text-strong);
  font-weight: 700;
  line-height: 1.4;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field label {
  display: block;
  margin-bottom: 6px;
  font-weight: 800;
  color: var(--text-strong);
}

.field input {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--surface-weak);
  color: var(--text-strong);
  outline: none;
  box-sizing: border-box;
}

.field-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.field-row input {
  flex: 1;
}

.btn-inline {
  white-space: nowrap;
}

.field input:focus {
  border-color: var(--primary-color);
  background: var(--surface);
}

.field--checkbox {
  margin-top: 4px;
}

.checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  color: var(--text-strong);
}

.checkbox input {
  width: 16px;
  height: 16px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}

.btn {
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface);
  font-weight: 900;
  cursor: pointer;
}

.btn.ghost {
  color: var(--text-strong);
}

.btn.primary {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #fff;
}

.icon-btn {
  width: 26px;
  height: 26px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--surface);
  font-weight: 900;
  color: var(--text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.icon-btn:hover,
.icon-btn:focus-visible {
  color: var(--text-strong);
  border-color: var(--primary-color);
  outline: none;
}

.btn.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.error {
  margin: 6px 0 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--danger-color, #dc2626);
}

@media (max-width: 720px) {
  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }
  .actions {
    justify-content: flex-start;
  }
}
</style>
