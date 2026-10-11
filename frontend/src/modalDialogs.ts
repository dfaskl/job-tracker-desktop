import { ref } from 'vue'

export type DialogTone = 'neutral' | 'warning' | 'danger'

export type DialogRequest = {
  mode: 'confirm' | 'prompt'
  title: string
  message: string
  label?: string
  value?: string
  confirmText: string
  cancelText: string
  tone: DialogTone
}

export const activeDialog = ref<DialogRequest | null>(null)

let settle: ((value: string | null) => void) | null = null

function requestDialog(request: DialogRequest): Promise<string | null> {
  if (settle) settle(null)
  activeDialog.value = request
  return new Promise(resolve => { settle = resolve })
}

export async function confirmDialog(message: string, options: Partial<Pick<DialogRequest, 'title' | 'confirmText' | 'cancelText' | 'tone'>> = {}): Promise<boolean> {
  const result = await requestDialog({
    mode: 'confirm', title: options.title || '请确认', message,
    confirmText: options.confirmText || '确认', cancelText: options.cancelText || '取消',
    tone: options.tone || 'warning'
  })
  return result === 'confirmed'
}

export function promptDialog(message: string, options: Partial<Pick<DialogRequest, 'title' | 'confirmText' | 'cancelText' | 'tone' | 'label' | 'value'>> = {}): Promise<string | null> {
  return requestDialog({
    mode: 'prompt', title: options.title || '需要确认', message, label: options.label || '请输入确认内容', value: options.value || '',
    confirmText: options.confirmText || '确认', cancelText: options.cancelText || '取消', tone: options.tone || 'danger'
  })
}

export function resolveDialog(value: string | null) {
  const finish = settle
  settle = null
  activeDialog.value = null
  finish?.(value)
}
