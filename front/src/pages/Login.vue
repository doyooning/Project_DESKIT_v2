<script setup lang="ts">
import { useRouter } from 'vue-router'
import PageContainer from '../components/PageContainer.vue'
import PageHeader from '../components/PageHeader.vue'
import { clearClientAuthState, requestLogout } from '../lib/auth'

type Provider = 'kakao' | 'naver' | 'google'

const router = useRouter()
const oauthBase = import.meta.env.VITE_OAUTH_BASE_URL || window.location.origin

const handleLogin = async (provider: Provider) => {
  clearClientAuthState()
  await requestLogout().catch(() => {})
  window.location.href = `${oauthBase}/oauth2/authorization/${provider}`
}
</script>

<template>
  <PageContainer>
    <PageHeader eyebrow="DESKIT" title="로그인" />
    <div class="login-wrap">
      <section class="login-card" aria-label="소셜 로그인">
        <div class="card-top">
          <p class="lead">소셜 로그인으로만 이용할 수 있어요.</p>
          <p class="sub">
            로그인하면 <strong>주문내역</strong>, <strong>장바구니</strong>, <strong>맞춤 추천</strong>을 사용할 수 있어요.
          </p>
        </div>

        <div class="social-list">
          <button type="button" class="social-btn kakao" @click="handleLogin('kakao')">
            <span class="brand-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M12 4c-4.7 0-8.5 2.9-8.5 6.6 0 2.4 1.6 4.5 4 5.7l-1 3.6c-.1.4.3.7.6.5l4.2-2.8c.2 0 .5.1.7.1 4.7 0 8.5-2.9 8.5-6.6S16.7 4 12 4z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span class="btn-text">카카오로 시작하기</span>
          </button>

          <button type="button" class="social-btn naver" @click="handleLogin('naver')">
            <span class="brand-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M7 6h4.2l5.8 8.2V6H21v12h-4.2L11 9.8V18H7V6z" fill="currentColor" />
              </svg>
            </span>
            <span class="btn-text">네이버로 시작하기</span>
          </button>

          <button type="button" class="social-btn google" @click="handleLogin('google')">
            <span class="brand-ico google" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path
                  d="M21.6 12.3c0-.7-.1-1.2-.2-1.8H12v3.4h5.4c-.1.9-.7 2.2-2 3.1v2.2h3.2c1.9-1.7 3-4.3 3-7.9z"
                  fill="currentColor"
                />
                <path
                  d="M12 22c2.7 0 5-.9 6.6-2.5l-3.2-2.2c-.9.6-2 .9-3.4.9-2.6 0-4.8-1.7-5.6-4.1H3v2.3C4.7 19.8 8.1 22 12 22z"
                  fill="currentColor"
                  opacity=".6"
                />
                <path
                  d="M6.4 14.1c-.2-.6-.3-1.2-.3-1.8s.1-1.2.3-1.8V8.2H3C2.4 9.4 2 10.7 2 12.3c0 1.6.4 2.9 1 4.1l3.4-2.3z"
                  fill="currentColor"
                  opacity=".4"
                />
                <path
                  d="M12 6.7c1.9 0 3.2.8 3.9 1.5l2.9-2.8C17 3.8 14.7 2.7 12 2.7 8.1 2.7 4.7 4.9 3 8.2l3.4 2.3c.8-2.4 3-3.8 5.6-3.8z"
                  fill="currentColor"
                  opacity=".8"
                />
              </svg>
            </span>
            <span class="btn-text">구글로 시작하기</span>
          </button>
        </div>

        <div class="card-footer">
          <p class="legal">
            로그인 시 <span class="legal-strong">이용약관</span> 및 <span class="legal-strong">개인정보 처리방침</span>에 동의한 것으로 간주합니다.
          </p>

          <div class="footer-actions">
            <button type="button" class="link-btn" @click="router.back()">취소 / 돌아가기</button>
          </div>
        </div>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.login-wrap {
  max-width: 560px;
  margin: 0 auto;
  padding-top: 12px;
}

.login-card {
  position: relative;
  border: 1px solid var(--border-color);
  background: var(--surface);
  border-radius: 14px;
  padding: 18px 16px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.login-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: 14px;
  background: radial-gradient(circle at 20% 0%, rgba(34, 197, 94, 0.08), transparent 55%),
  radial-gradient(circle at 90% 30%, rgba(17, 24, 39, 0.06), transparent 55%);
}

.login-card > * {
  position: relative;
  z-index: 1;
}

.card-top {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lead {
  margin: 0;
  color: var(--text-strong);
  font-weight: 900;
  font-size: 14px;
}

.sub {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 13px;
  line-height: 1.45;
}

.sub strong {
  color: var(--text-strong);
  font-weight: 900;
}

.social-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.social-btn {
  width: 100%;
  border-radius: 12px;
  padding: 12px 12px;
  border: 1px solid var(--border-color);
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 900;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease, border-color 0.15s ease;
}

.social-btn:hover,
.social-btn:focus-visible {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.06);
  outline: none;
}

.brand-ico {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  font-size: 13px;
  background: rgba(0, 0, 0, 0.08);
  color: inherit;
  flex: 0 0 auto;
}

.brand-ico svg {
  display: block;
}

.social-btn.kakao {
  background: #fee500;
  color: #111827;
  border-color: #f7d100;
}

.social-btn.kakao .brand-ico {
  background: rgba(17, 24, 39, 0.12);
}

.social-btn.naver {
  background: #03c75a;
  color: #ffffff;
  border-color: #03c75a;
}

.social-btn.naver .brand-ico {
  background: rgba(255, 255, 255, 0.18);
}

.social-btn.google {
  background: #ffffff;
  color: var(--text-strong);
  border-color: var(--border-color);
}

.social-btn.google .brand-ico {
  background: #f3f4f6;
}

.btn-text {
  letter-spacing: -0.01em;
}

.card-footer {
  border-top: 1px solid var(--border-color);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.legal {
  margin: 0;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 12.5px;
  line-height: 1.45;
}

.legal-strong {
  color: var(--text-strong);
  font-weight: 900;
}

.footer-actions {
  display: flex;
  justify-content: flex-start;
}

.link-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-weight: 800;
  cursor: pointer;
  padding: 4px 0;
}

.link-btn:hover,
.link-btn:focus-visible {
  color: var(--text-strong);
  text-decoration: underline;
  outline: none;
}
</style>
