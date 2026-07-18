/**
 * 本体推理环节：紧凑本体推理链
 */
<template>
  <div v-if="chain" class="onto-reason">
    <OntologyChainViz
      :chain="chainForViz"
      :reveal-count="chainRevealCount"
      :streaming="streaming"
      compact
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import OntologyChainViz from './OntologyChainViz.vue'

const props = defineProps({
  preview: { type: Object, default: null },
  chain: { type: Object, default: null },
  chainRevealCount: { type: Number, default: 0 },
  streaming: { type: Boolean, default: false },
})

const chainForViz = computed(() => {
  if (!props.chain) return null
  if (props.preview) {
    return {
      ...props.chain,
      summary: props.preview.summary || props.chain.summary,
      compliancePass: props.preview.compliancePass,
      blocked: props.preview.blocked,
    }
  }
  return props.chain
})
</script>

<style scoped>
.onto-reason {
  margin-top: 4px;
}
</style>
