import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useWorkflowDataStore = defineStore('workflowData', () => {
  const ontologies = ref([])
  const mcpTools = ref([])
  const mcpToolMap = ref({})
  const loadingOntologies = ref(false)
  const loadingMcpTools = ref(false)
  const lastLoadedOntologies = ref(0)
  const lastLoadedMcpTools = ref(0)
  const cacheTime = 5 * 60 * 1000

  const groupedTools = computed(() => {
    const groups = {}
    for (const tool of mcpTools.value) {
      const cat = tool.metadata?.category || 'general'
      if (!groups[cat]) groups[cat] = []
      groups[cat].push(tool)
    }
    return groups
  })

  const categories = computed(() => {
    const cats = [...new Set(mcpTools.value.map(t => t.metadata?.category || 'general'))]
    return cats.sort()
  })

  const categoryNames = {
    form: '表单工具',
    kb: '知识库工具',
    llm: 'LLM 工具',
    system: '系统工具',
    tariff: '资费工具',
    workflow: '工作流工具',
    general: '通用工具',
    external: '外部工具'
  }

  const getCategoryDisplayName = (cat) => categoryNames[cat] || cat

  const loadOntologies = async (force = false) => {
    const now = Date.now()
    if (!force && ontologies.value.length > 0 && (now - lastLoadedOntologies.value) < cacheTime) {
      return ontologies.value
    }

    if (loadingOntologies.value) {
      return null
    }

    loadingOntologies.value = true
    try {
      const response = await fetch('/api/v1/ontologies?isActive=true')
      const res = await response.json()
      if (res && res.success) {
        ontologies.value = res.data || res.ontologies || []
      } else if (Array.isArray(res)) {
        ontologies.value = res
      }
      lastLoadedOntologies.value = now
    } catch (e) {
      console.error('加载本体列表失败:', e)
    } finally {
      loadingOntologies.value = false
    }
    return ontologies.value
  }

  const loadMCPTools = async (force = false) => {
    const now = Date.now()
    if (!force && mcpTools.value.length > 0 && (now - lastLoadedMcpTools.value) < cacheTime) {
      return mcpTools.value
    }

    if (loadingMcpTools.value) {
      return null
    }

    loadingMcpTools.value = true
    try {
      const response = await fetch('/api/v1/mcp-management/tools')
      const res = await response.json()
      if (res.success) {
        mcpTools.value = res.tools || []
        const map = {}
        for (const t of mcpTools.value) {
          map[t.name] = t
        }
        mcpToolMap.value = map
      }
      lastLoadedMcpTools.value = now
    } catch (e) {
      console.error('加载 MCP 工具失败:', e)
    } finally {
      loadingMcpTools.value = false
    }
    return mcpTools.value
  }

  const getToolByName = (name) => {
    return mcpToolMap.value[name] || null
  }

  const getOntologyByCode = (code) => {
    return ontologies.value.find(o => o.ontologyCode === code) || null
  }

  const clearCache = () => {
    ontologies.value = []
    mcpTools.value = []
    mcpToolMap.value = {}
    lastLoadedOntologies.value = 0
    lastLoadedMcpTools.value = 0
  }

  return {
    ontologies,
    mcpTools,
    mcpToolMap,
    loadingOntologies,
    loadingMcpTools,
    groupedTools,
    categories,
    categoryNames,
    getCategoryDisplayName,
    loadOntologies,
    loadMCPTools,
    getToolByName,
    getOntologyByCode,
    clearCache
  }
})