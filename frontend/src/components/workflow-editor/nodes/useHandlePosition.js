import { computed, ref, watch } from 'vue';
import { Position } from '@vue-flow/core';

export const ANCHOR_MODE_HORIZONTAL = 'horizontal'; // 水平布局 + 水平锚点(top/bottom)
export const ANCHOR_MODE_VERTICAL = 'vertical';     // 垂直布局 + 垂直锚点(left/right)

// 注意：命名语义与布局方向一致
// - 'horizontal' = 水平布局(LR) + 水平锚点(顶部进入/底部离开)
// - 'vertical' = 垂直布局(TB) + 垂直锚点(左侧进入/右侧离开)
export function useHandlePosition(anchorMode = ANCHOR_MODE_VERTICAL) {
  const targetPosition = computed(() => {
    // 水平布局：连接从左侧进入；垂直布局：连接从顶部进入
    return anchorMode.value === ANCHOR_MODE_HORIZONTAL
      ? Position.Left
      : Position.Top;
  });

  const sourcePosition = computed(() => {
    // 水平布局：连接从右侧离开；垂直布局：连接从底部离开
    return anchorMode.value === ANCHOR_MODE_HORIZONTAL
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
  const anchorModeValue = ref(
    props.data?.anchorMode || ANCHOR_MODE_VERTICAL
  );

  // 监听整个props.data对象的变化，确保可靠的响应式更新
  watch(
    () => props.data,
    (newData) => {
      if (newData) {
        const newAnchorMode = newData.anchorMode || ANCHOR_MODE_VERTICAL;
        anchorModeValue.value = newAnchorMode;
        // 调试日志：确认anchorMode更新
        console.log(`[useNodeAnchorMode] anchorMode更新为: ${newAnchorMode}`);
      }
    },
    { immediate: true, deep: true }
  );

  // 创建computed包装器
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