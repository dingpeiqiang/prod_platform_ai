import { computed, ref, watch } from 'vue';
import { Position } from '@vue-flow/core';

export const ANCHOR_MODE_VERTICAL = 'vertical';
export const ANCHOR_MODE_HORIZONTAL = 'horizontal';

export function useHandlePosition(anchorMode = ANCHOR_MODE_VERTICAL) {
  const targetPosition = computed(() => {
    return anchorMode.value === ANCHOR_MODE_VERTICAL
      ? Position.Left
      : Position.Top;
  });

  const sourcePosition = computed(() => {
    return anchorMode.value === ANCHOR_MODE_VERTICAL
      ? Position.Right
      : Position.Bottom;
  });

  const isVertical = computed(() => {
    return anchorMode.value === ANCHOR_MODE_VERTICAL;
  });

  return {
    targetPosition,
    sourcePosition,
    isVertical,
    ANCHOR_MODE_VERTICAL,
    ANCHOR_MODE_HORIZONTAL
  };
}

export function useNodeAnchorMode(props) {
  // 使用ref来存储anchorMode值，确保响应式更新
  // 当node.data.anchorMode被直接赋值时，Vue的computed无法检测到嵌套属性变化
  // 因此需要通过watch来显式监听变化
  const anchorModeValue = ref(
    props.data?.anchorMode || ANCHOR_MODE_VERTICAL
  );

  // 监听props.data.anchorMode的变化
  // 当在LangChainEditor.vue中执行 node.data.anchorMode = newMode 时触发
  watch(
    () => props.data?.anchorMode,
    (newVal) => {
      anchorModeValue.value = newVal || ANCHOR_MODE_VERTICAL;
    },
    { immediate: true }
  );

  // 创建computed包装器，确保useHandlePosition能正确响应变化
  const anchorMode = computed(() => anchorModeValue.value);

  const { targetPosition, sourcePosition, isVertical } = useHandlePosition(anchorMode);

  return {
    anchorMode,
    targetPosition,
    sourcePosition,
    isVertical,
    ANCHOR_MODE_VERTICAL,
    ANCHOR_MODE_HORIZONTAL
  };
}