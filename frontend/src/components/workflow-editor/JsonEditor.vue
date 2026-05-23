<template>
  <div class="ace-editor-wrapper" ref="wrapperRef">
    <div ref="editorContainer" class="editor-container"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onUnmounted, nextTick } from 'vue';

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  readOnly: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:modelValue', 'change']);

const editorContainer = ref(null);
const wrapperRef = ref(null);
let editor = null;
let resizeObserver = null;

const initEditor = async () => {
  if (!editorContainer.value || !wrapperRef.value) return;

  try {
    const ace = await import('ace-builds');
    await import('ace-builds/src-noconflict/mode-json');
    await import('ace-builds/src-noconflict/theme-monokai');
    await import('ace-builds/src-noconflict/ext-language_tools');
    await import('ace-builds/src-noconflict/ext-searchbox');

    ace.config.set('basePath', '');
    ace.config.set('workerPath', '');
    ace.config.set('useWorker', false);

    editor = ace.default.edit(editorContainer.value);

    editor.setOptions({
      mode: 'ace/mode/json',
      theme: 'ace/theme/monokai',
      readOnly: props.readOnly,
      fontSize: 14,
      fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
      showLineNumbers: true,
      scrollPastEnd: false,
      highlightActiveLine: true,
      highlightGutterLine: true,
      wrap: true,
      autoScrollEditorIntoView: true,
      enableBasicAutocompletion: true,
      enableLiveAutocompletion: true,
      enableSnippets: true,
      tabSize: 2,
      useSoftTabs: true,
      displayIndentGuides: true,
      showPrintMargin: false
    });

    editor.setValue(props.modelValue || '{}');
    editor.clearSelection();

    editor.on('change', () => {
      const value = editor.getValue();
      emit('update:modelValue', value);
      emit('change', value);
    });

    const updateSize = () => {
      if (editor && wrapperRef.value) {
        const rect = wrapperRef.value.getBoundingClientRect();
        editorContainer.value.style.height = `${rect.height}px`;
        editor.resize();
      }
    };

    updateSize();

    resizeObserver = new ResizeObserver(() => {
      updateSize();
    });
    resizeObserver.observe(wrapperRef.value);

  } catch (error) {
    console.error('Failed to initialize Ace Editor:', error);
    editorContainer.value.innerHTML = `
      <div style="padding: 20px; color: #ff6b6b; background: #1e1e1e; height: 100%; display: flex; align-items: center; justify-content: center;">
        <div>
          <div style="font-size: 18px; font-weight: bold; margin-bottom: 8px;">编辑器加载失败</div>
          <div style="font-size: 14px; color: #888;">${error.message}</div>
        </div>
      </div>
    `;
  }
};

const setValue = (value) => {
  if (editor && value !== editor.getValue()) {
    editor.setValue(value);
    editor.clearSelection();
  }
};

watch(() => props.modelValue, (newValue) => {
  nextTick(() => {
    setValue(newValue);
  });
});

watch(() => props.readOnly, (newValue) => {
  if (editor) {
    editor.setReadOnly(newValue);
  }
});

defineExpose({
  setValue,
  getValue: () => editor?.getValue() || ''
});

onMounted(() => {
  nextTick(() => {
    initEditor();
  });
});

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (editor) {
    editor.destroy();
    editor = null;
  }
});
</script>

<style scoped>
.ace-editor-wrapper {
  width: 100%;
  height: 100%;
  min-height: 300px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-container {
  flex: 1;
  width: 100%;
  min-height: 300px;
  touch-action: none;
}
</style>
