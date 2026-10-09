<script setup lang="ts">
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import AppButton from '@/components/AppButton.vue'

const props = withDefaults(
  defineProps<{
    /** 待裁剪图片的地址（object URL 或 data URL） */
    src: string
    /** 输出边长，默认 512 */
    outputSize?: number
  }>(),
  { outputSize: 512 },
)

const open = defineModel<boolean>('open', { default: false })

const emit = defineEmits<{
  cropped: [blob: Blob]
  cancel: []
  error: [message: string]
}>()

const imageEl = ref<HTMLImageElement | null>(null)
const busy = ref(false)
const zoom = ref(1)
const ready = ref(false)

let cropper: Cropper | null = null
let previousOverflow = ''

function destroy(): void {
  if (cropper) {
    cropper.destroy()
    cropper = null
  }
  ready.value = false
}

/** JPEG 没有透明通道，导出前用纸面白填底，避免透明区域发黑 */
function paperColor(): string {
  if (typeof window === 'undefined') return '#ffffff'
  const value = window
    .getComputedStyle(document.documentElement)
    .getPropertyValue('--paper-raised')
    .trim()
  return value || '#ffffff'
}

function build(): void {
  const image = imageEl.value
  if (!image || !image.naturalWidth) return
  destroy()
  try {
    cropper = new Cropper(image, {
      aspectRatio: 1,
      viewMode: 1,
      dragMode: 'move',
      autoCropArea: 1,
      background: false,
      modal: true,
      guides: true,
      center: true,
      highlight: false,
      responsive: true,
      rotatable: true,
      scalable: false,
      zoomable: true,
      cropBoxMovable: false,
      cropBoxResizable: true,
      toggleDragModeOnDblclick: false,
      minContainerHeight: 280,
      ready() {
        ready.value = true
        zoom.value = 1
      },
    })
  } catch (error) {
    emit('error', error instanceof Error ? `裁剪器初始化失败：${error.message}` : '裁剪器初始化失败')
  }
}

function onImageLoad(): void {
  if (!open.value) return
  if (imageEl.value?.naturalWidth === 0) {
    emit('error', '图片无法解析，请换一张 JPG 或 PNG')
    return
  }
  build()
}

function onImageError(): void {
  emit('error', '图片读取失败，请重新选择')
}

function onZoomInput(event: Event): void {
  const value = Number((event.target as HTMLInputElement).value)
  zoom.value = value
  cropper?.zoomTo(value)
}

function rotate(degree: number): void {
  cropper?.rotate(degree)
}

function resetView(): void {
  cropper?.reset()
  zoom.value = 1
}

function cancel(): void {
  open.value = false
  emit('cancel')
}

function confirm(): void {
  if (!cropper || !ready.value) {
    emit('error', '裁剪器尚未就绪，请稍候重试')
    return
  }
  busy.value = true
  let canvas: HTMLCanvasElement
  try {
    canvas = cropper.getCroppedCanvas({
      width: props.outputSize,
      height: props.outputSize,
      imageSmoothingEnabled: true,
      imageSmoothingQuality: 'high',
      fillColor: paperColor(),
    })
  } catch (error) {
    busy.value = false
    emit('error', error instanceof Error ? `裁剪失败：${error.message}` : '裁剪失败，请重试')
    return
  }

  if (!canvas) {
    busy.value = false
    emit('error', '裁剪失败：无法生成画布，请换一张图片')
    return
  }

  canvas.toBlob(
    (blob) => {
      busy.value = false
      if (!blob) {
        emit('error', '裁剪结果生成失败，请重试')
        return
      }
      emit('cropped', blob)
      open.value = false
    },
    'image/jpeg',
    0.92,
  )
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    cancel()
  }
}

watch(open, async (value) => {
  if (value) {
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKeydown, true)
    await nextTick()
    if (imageEl.value?.complete) onImageLoad()
  } else {
    document.body.style.overflow = previousOverflow
    window.removeEventListener('keydown', onKeydown, true)
    destroy()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown, true)
  document.body.style.overflow = previousOverflow
  destroy()
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay">
      <div class="panel" role="dialog" aria-modal="true" aria-label="裁剪头像">
        <header class="panel__head">
          <div>
            <h2 class="panel__title">裁剪头像</h2>
            <p class="panel__hint mono">输出 {{ outputSize }}×{{ outputSize }} · JPEG</p>
          </div>
          <button class="panel__close" type="button" aria-label="关闭" @click="cancel">×</button>
        </header>

        <div class="panel__stage">
          <img
            ref="imageEl"
            class="panel__image"
            :src="src"
            alt="待裁剪的头像"
            @load="onImageLoad"
            @error="onImageError"
          />
        </div>

        <div class="panel__tools">
          <label class="panel__tool">
            <span class="panel__tool-label">缩放</span>
            <input
              class="panel__range"
              type="range"
              min="1"
              max="3"
              step="0.01"
              :value="zoom"
              :disabled="!ready"
              @input="onZoomInput"
            />
          </label>
          <div class="panel__buttons">
            <AppButton size="sm" variant="secondary" :disabled="!ready" @click="rotate(-90)">
              左转 90°
            </AppButton>
            <AppButton size="sm" variant="secondary" :disabled="!ready" @click="rotate(90)">
              右转 90°
            </AppButton>
            <AppButton size="sm" variant="ghost" :disabled="!ready" @click="resetView">复位</AppButton>
          </div>
        </div>

        <footer class="panel__foot">
          <AppButton variant="secondary" :disabled="busy" @click="cancel">取消</AppButton>
          <AppButton variant="primary" :loading="busy" :disabled="!ready" @click="confirm">
            使用这张
          </AppButton>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
  background: var(--scrim);
}

/* 全站保留的两处动效之一：裁剪面板入场 */
.panel {
  display: flex;
  flex-direction: column;
  width: min(560px, 100%);
  max-height: calc(100vh - var(--sp-8));
  background: var(--paper-raised);
  border: var(--border-strong);
  border-radius: var(--radius-md);
  border-top: var(--border-accent) solid var(--amber);
  box-shadow: var(--shadow-overlay);
  overflow: hidden;
  animation: cropper-panel-in var(--motion) both;
}

@keyframes cropper-panel-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-4) var(--sp-5) var(--sp-3);
  border-bottom: var(--border-hair);
}

.panel__title {
  font-size: var(--text-lg);
}

.panel__hint {
  margin-top: 2px;
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.panel__close {
  width: 26px;
  height: 26px;
  border: var(--border-hair);
  border-radius: var(--radius-xs);
  background: var(--paper-raised);
  color: var(--ink-muted);
  font-size: var(--text-md);
  line-height: 1;
  cursor: pointer;
}

.panel__stage {
  flex: 1;
  min-height: 280px;
  background: var(--paper-sunken);
  overflow: hidden;
}

.panel__image {
  display: block;
  max-width: 100%;
}

.panel__tools {
  display: grid;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-5);
  border-top: var(--border-hair);
}

.panel__tool {
  display: grid;
  grid-template-columns: 40px 1fr;
  align-items: center;
  gap: var(--sp-3);
}

.panel__tool-label {
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-label);
  text-transform: lowercase;
  color: var(--ink-faint);
}

.panel__range {
  width: 100%;
  accent-color: var(--indigo);
}

.panel__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.panel__foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-5) var(--sp-4);
  border-top: var(--border-hair);
  background: var(--paper-tint);
}

:deep(.cropper-view-box) {
  outline: 1px solid var(--indigo);
  outline-color: var(--indigo);
}

:deep(.cropper-line),
:deep(.cropper-point) {
  background-color: var(--indigo);
}

:deep(.cropper-modal) {
  background-color: var(--scrim);
}

@media (max-width: 560px) {
  .overlay {
    padding: 0;
    align-items: stretch;
  }
  .panel {
    width: 100%;
    max-height: 100vh;
    border-radius: 0;
  }
}
</style>
