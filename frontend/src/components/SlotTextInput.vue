<template>
  <div class="slot-text-input" :class="{ disabled }">
    <div
      ref="editorEl"
      class="sli-editor"
      contenteditable="true"
      :data-placeholder="placeholder"
      @input="onEditorInput"
      @keydown="onKeydown"
      @keydown.enter.prevent="onEnter"
      @focus="onFocus"
      @blur="onBlur"
      @paste="onPaste"
      @drop.prevent
      spellcheck="false"
    ></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  placeholder: { type: String, default: '描述你想做的事...' },
  disabled: { type: Boolean, default: false },
  // 槽位模板定义：用于把初始模板文本渲染成槽位
  template: { type: Array, default: () => [] },
  // 槽位选项：key=槽位label, value=[可选项]。有选项 → 下拉框；无选项 → 行内可编辑
  slotOptions: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update:modelValue', 'input-state'])

const editorEl = ref(null)

let seq = 0
const genId = () => `slot-${Date.now()}-${seq++}`

function buildTextSegment(text) {
  return { type: 'text', text }
}
function buildSlotSegment(label, value, placeholder, domId) {
  return { type: 'slot', id: domId || genId(), label, value, placeholder }
}

function slotHasOptions(label) {
  const o = props.slotOptions[label]
  return Array.isArray(o) && o.length > 0
}

// ── 渲染初始模型到 DOM（用 modelValue）──
function renderModel() {
  const el = editorEl.value
  if (!el) return
  el.replaceChildren()
  let textBuf = ''
  const flushText = () => {
    if (textBuf) {
      el.appendChild(document.createTextNode(textBuf))
      textBuf = ''
    }
  }
  ;(props.modelValue || []).forEach((seg) => {
    if (seg.type === 'text' && seg.text) {
      textBuf += seg.text
    } else if (seg.type === 'slot') {
      flushText()
      el.appendChild(makeSlotNode(seg))
    }
  })
  flushText()
  syncState()
}

// ── 套用预置模板：始终渲染 props.template（用于产品查询等场景）──
function renderTemplate() {
  const el = editorEl.value
  if (!el) return
  el.replaceChildren()
  let textBuf = ''
  const flushText = () => {
    if (textBuf) {
      el.appendChild(document.createTextNode(textBuf))
      textBuf = ''
    }
  }
  ;(props.template || []).forEach((seg) => {
    if (seg.type === 'text' && seg.text) {
      textBuf += seg.text
    } else if (seg.type === 'slot') {
      flushText()
      el.appendChild(makeSlotNode(seg))
    }
  })
  flushText()
  syncState()
}

// ── 套用预置模板，但把符合 preset.label 的槽位预填为 preset.value ──
// 用于「查存量数据卡：<名称>」话术点击后，把套餐名称预填进套餐名称槽位
function renderTemplateWith(preset) {
  const el = editorEl.value
  if (!el) return
  el.replaceChildren()
  let textBuf = ''
  const flushText = () => {
    if (textBuf) {
      el.appendChild(document.createTextNode(textBuf))
      textBuf = ''
    }
  }
  ;(props.template || []).forEach((seg) => {
    if (seg.type === 'text' && seg.text) {
      textBuf += seg.text
    } else if (seg.type === 'slot') {
      flushText()
      const filled =
        preset && seg.label === preset.label && preset.value
          ? { ...seg, value: preset.value }
          : seg
      el.appendChild(makeSlotNode(filled))
    }
  })
  flushText()
  syncState()
}

function placeCaretAtEnd(el) {
  const range = document.createRange()
  range.selectNodeContents(el)
  range.collapse(false)
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(range)
}

function refreshInlineEmpty(body) {
  const hasVal = (body.textContent || '').replace(/\u200b/g, '').trim().length > 0
  body.classList.toggle('v-empty', !hasVal)
}

function makeSlotNode(seg) {
  const slot = document.createElement('span')
  slot.className = 'sli-slot'
  slot.setAttribute('contenteditable', 'false')
  if (seg.id) slot.dataset.id = seg.id
  const label = seg.label || ''
  slot.dataset.label = label

  const prefix = document.createElement('span')
  prefix.className = 'sli-slot-prefix'
  prefix.textContent = `【${label}：`
  slot.appendChild(prefix)

  if (slotHasOptions(label)) {
    // 下拉框模式（套餐类型）：原生 select，直接选择
    const select = document.createElement('select')
    select.className = 'sli-slot-select'
    ;(props.slotOptions[label] || []).forEach((opt) => {
      const op = document.createElement('option')
      op.value = opt
      op.textContent = opt
      select.appendChild(op)
    })
    const list = props.slotOptions[label] || []
    select.value = list.includes(seg.value) ? seg.value : list[0]
    select.addEventListener('change', () => {
      syncState()
      autoGrow()
    })
    slot.appendChild(select)
  } else {
    // 行内可编辑模式（套餐名称/套餐编码）：点击直接在槽位中输入
    const body = document.createElement('span')
    body.className = 'sli-slot-inline'
    body.setAttribute('contenteditable', 'true')
    body.dataset.placeholder = seg.placeholder || '请输入'
    body.textContent = seg.value || ''
    refreshInlineEmpty(body)
    body.addEventListener('input', () => {
      refreshInlineEmpty(body)
      syncState()
      autoGrow()
    })
    slot.appendChild(body)
    slot.addEventListener('click', (e) => {
      if (e.target === body) return
      e.preventDefault()
      e.stopPropagation()
      nextTick(() => placeCaretAtEnd(body))
    })
  }

  const suffix = document.createElement('span')
  suffix.className = 'sli-slot-suffix'
  suffix.textContent = '】'
  slot.appendChild(suffix)

  return slot
}

// ── 从 DOM 收集 segments ──
function collectSegments() {
  const el = editorEl.value
  if (!el) return []
  const segs = []
  Array.from(el.childNodes).forEach((child) => {
    if (child.nodeType === Node.TEXT_NODE) {
      if (child.textContent) segs.push(buildTextSegment(child.textContent))
    } else if (child.nodeType === Node.ELEMENT_NODE) {
      if (child.classList && child.classList.contains('sli-slot')) {
        const label = child.dataset.label || ''
        const select = child.querySelector('.sli-slot-select')
        const inline = child.querySelector('.sli-slot-inline')
        let value = ''
        if (select) {
          value = select.value
        } else if (inline) {
          value = (inline.textContent || '').replace(/\u200b/g, '').trim()
        }
        segs.push(buildSlotSegment(label, value, '', child.dataset.id))
      } else {
        Array.from(child.childNodes).forEach((nc) => {
          if (nc.nodeType === Node.TEXT_NODE && nc.textContent) {
            segs.push(buildTextSegment(nc.textContent))
          }
        })
      }
    }
  })
  return segs
}

function syncState() {
  const segs = collectSegments()
  const hasText = segs.some((s) => s.type === 'text' && s.text.trim())
  const hasSlot = segs.some((s) => s.type === 'slot')
  editorEl.value?.classList.toggle('empty', !(hasText || hasSlot))
  emit('update:modelValue', segs)
  emit('input-state', { hasText: hasText || hasSlot, hasSlot })
}

function onEditorInput() {
  syncState()
  autoGrow()
}

function onKeydown(e) {
  if (props.disabled) { e.preventDefault(); return }
  if (e.key === 'Backspace') {
    const sel = window.getSelection()
    if (!sel || sel.rangeCount === 0) return
    const range = sel.getRangeAt(0)
    if (!range.collapsed) return
    const sc = range.startContainer
    // 槽位后按退格 → 删除该槽位
    if (sc && sc.nodeType === Node.TEXT_NODE && range.startOffset === 0) {
      const ps = sc.previousSibling
      if (ps && ps.nodeType === Node.ELEMENT_NODE && ps.classList?.contains('sli-slot')) {
        e.preventDefault()
        removeSlotElement(ps)
        return
      }
    }
  }
  requestAnimationFrame(autoGrow)
}

function onEnter() {
  emit('submit')
}

function onFocus() {
  autoGrow()
}

function onBlur() {
  autoGrow()
}

function autoGrow() {
  const el = editorEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}

function removeSlotElement(span) {
  span.remove()
  syncState()
  autoGrow()
  editorEl.value?.focus()
}

// ── 对外 API ──
function setTemplate() {
  renderTemplate()
}

function focusEditor() {
  if (props.disabled) return
  editorEl.value?.focus()
}

function clearContent() {
  if (editorEl.value) editorEl.value.replaceChildren()
  syncState()
  autoGrow()
}

function insertText(text) {
  focusEditor()
  const el = editorEl.value
  if (!el) return
  const sel = window.getSelection()
  if (sel && sel.rangeCount > 0) {
    const range = sel.getRangeAt(0)
    range.deleteContents()
    const tn = document.createTextNode(text)
    range.insertNode(tn)
    range.setStartAfter(tn)
    range.collapse(true)
    sel.removeAllRanges()
    sel.addRange(range)
  } else {
    el.appendChild(document.createTextNode(text))
  }
  syncState()
  autoGrow()
}

function insertSlot(label, value) {
  focusEditor()
  const el = editorEl.value
  if (!el) return
  const seg = buildSlotSegment(label, value, '')
  const node = makeSlotNode(seg)
  const sel = window.getSelection()
  if (sel && sel.rangeCount > 0) {
    const range = sel.getRangeAt(0)
    range.deleteContents()
    range.insertNode(node)
    const space = document.createTextNode('\u200b')
    node.parentNode.insertBefore(space, node.nextSibling)
    range.setStartAfter(space)
    range.collapse(true)
    sel.removeAllRanges()
    sel.addRange(range)
  } else {
    el.appendChild(node)
    el.appendChild(document.createTextNode('\u200b'))
  }
  syncState()
  autoGrow()
}

function onPaste(e) {
  if (props.disabled) { e.preventDefault(); return }
  e.preventDefault()
  const text = (e.clipboardData || window.clipboardData)?.getData('text') || ''
  if (text) insertText(text)
}

defineExpose({
  focus: focusEditor,
  insertText,
  insertSlot,
  clearContent,
  setTemplate,
  renderTemplateWith,
})

onMounted(() => {
  renderModel()
  autoGrow()
})
onBeforeUnmount(() => {})
</script>

<style>
.slot-text-input {
  position: relative;
  width: 100%;
}
.sli-editor {
  width: 100%;
  min-height: 60px;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.9;
  padding: 2px 2px 8px;
  word-break: break-word;
  white-space: pre-wrap;
}
.sli-editor:empty::before,
.sli-editor.empty::before {
  content: attr(data-placeholder);
  color: var(--text-tertiary);
  pointer-events: none;
}

.sli-slot {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  margin: 0 2px;
  padding: 0 2px;
  border-radius: 6px;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
  font-size: 14px;
  vertical-align: baseline;
  white-space: normal;
  user-select: none;
  transition: box-shadow 0.15s ease, border-color 0.15s ease;
  max-width: 100%;
}
.sli-slot:hover {
  border-color: #93c5fd;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.18);
}

.sli-slot-prefix,
.sli-slot-suffix {
  font-weight: 500;
  color: #1d4ed8;
}

/* 行内可编辑（套餐名称/套餐编码）：直接在槽位中输入 */
.sli-slot-inline {
  display: inline-block;
  min-width: 4em;
  max-width: 100%;
  padding: 0 2px;
  border-bottom: 1px solid #93c5fd;
  font-weight: 500;
  color: #1d4ed8;
  outline: none;
  white-space: normal;
  word-break: break-all;
  user-select: text;
  cursor: text;
}
.sli-slot-inline:focus {
  border-bottom-color: #2563eb;
  background: rgba(59, 130, 246, 0.08);
}
.sli-slot-inline.v-empty::before {
  content: attr(data-placeholder);
  color: #93c5fd;
  font-style: italic;
  font-weight: 400;
}

/* 下拉框（套餐类型）：原生 select */
.sli-slot-select {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  outline: none;
  border: none;
  background-color: #eaf2ff;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='8' height='8' viewBox='0 0 8 8'><path fill='%231d4ed8' d='M1 2.5l3 3 3-3z'/></svg>");
  background-repeat: no-repeat;
  background-position: right 4px center;
  background-size: 8px 8px;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  padding: 2px 14px 2px 6px;
  border-radius: 5px;
  cursor: pointer;
  max-width: 12em;
}
.sli-slot-select:hover {
  background-color: #dceaff;
}
.sli-slot-select option {
  color: #1d4ed8;
}
</style>
