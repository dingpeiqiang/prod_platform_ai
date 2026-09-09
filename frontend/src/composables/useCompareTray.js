/**
 * useCompareTray - 查询→行动中间态：商品对比清单（方案 §6-B4，对应缺口 C5）
 *
 * 查询结果卡片的轻量暂存区：多商品逐个「加入对比」→ 托底在 tray（跨轮次、跨会话查询结果）
 * → 凑满 2 个以上一键进 ComparePanel 横向比对，弥合"查多个再统一比"的断裂路径。
 *
 * 设计约束：
 * - 纯前端本地态（v1 不落库），与 useProductConfig.compareResult 的 rd_scheme_compare
 *   输出结构对齐（comparisons 元素含 offeringId/offeringName/monthlyFee）；
 * - 上限 MAX_TRAY_SIZE=6：超出时提示先移除（防面板表格过载）；
 * - 订阅提醒为可后置项（方案 §6-B4），v1 仅登记意图不实现通知通道。
 */
import { ref, computed } from 'vue'

/** 清单容量上限（比对面板表格可读性约束） */
export const MAX_TRAY_SIZE = 6

/** 模块级单例：跨组件共享同一清单（页面与消息列表组件读写同一份） */
const trayItems = ref([])

export function useCompareTray() {
  /** 清单当前条目（只读视图） */
  const items = computed(() => trayItems.value)

  /** 是否已加入清单 */
  const has = (offeringId) =>
    trayItems.value.some((it) => it.offeringId === offeringId)

  /**
   * 加入对比清单。
   * @returns {{ ok: boolean, reason?: string }} ok=false 时 reason 提示（重复/超限/缺标识）
   */
  function add(item) {
    const offeringId = item?.offeringId || item?.offering_id || item?.code || item?.id
    if (!offeringId) {
      return { ok: false, reason: '该结果缺少商品编码，无法加入对比' }
    }
    if (has(offeringId)) {
      return { ok: false, reason: '已在对比清单中' }
    }
    if (trayItems.value.length >= MAX_TRAY_SIZE) {
      return { ok: false, reason: `对比清单已达上限 ${MAX_TRAY_SIZE} 个，请先移除后再加入` }
    }
    trayItems.value = [
      ...trayItems.value,
      {
        offeringId,
        offeringName: item?.name || item?.offeringName || offeringId,
        monthlyFee: item?.monthlyFee ?? item?.fee ?? null,
        desc: item?.desc || '',
        addedAt: Date.now(),
      },
    ]
    return { ok: true }
  }

  /** 移除单条 */
  function remove(offeringId) {
    trayItems.value = trayItems.value.filter((it) => it.offeringId !== offeringId)
  }

  /** 清空清单 */
  function clear() {
    trayItems.value = []
  }

  /**
   * 清单 → ComparePanel 输入结构（与 rd_scheme_compare 输出对齐）：
   * { comparisons: [...], recommended: null, explanation: '来自查询对比清单' }
   */
  function toCompareResult() {
    return {
      comparisons: trayItems.value.map((it) => ({
        offeringId: it.offeringId,
        offeringName: it.offeringName,
        monthlyFee: it.monthlyFee,
      })),
      recommended: null,
      explanation: '来自查询对比清单（手工暂存，未跑收益测算与合规校验）',
    }
  }

  return {
    items,
    has,
    add,
    remove,
    clear,
    toCompareResult,
  }
}
