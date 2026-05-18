<template>
  <div class="slider-field">
    <input
      :value="modelValue"
      @input="$emit('update:modelValue', toNumber($event.target.value))"
      type="range"
      :min="min"
      :max="max"
      :step="step"
      class="slider-input"
    />
    <div class="slider-controls">
      <input
        :value="modelValue"
        @input="$emit('update:modelValue', toNumber($event.target.value))"
        type="number"
        :min="min"
        :max="max"
        :step="step"
        class="value-input"
      />
      <div v-if="showAdjust" class="adjust-buttons">
        <button @click="adjust(-step)" class="adjust-btn">-</button>
        <button @click="adjust(step)" class="adjust-btn">+</button>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  modelValue: {
    type: Number,
    required: true
  },
  min: {
    type: Number,
    default: 0
  },
  max: {
    type: Number,
    default: 100
  },
  step: {
    type: Number,
    default: 1
  },
  showAdjust: {
    type: Boolean,
    default: true
  }
});

defineEmits(['update:modelValue']);

const toNumber = (value) => {
  const num = parseFloat(value);
  return isNaN(num) ? props.min : num;
};

const adjust = (delta) => {
  const newValue = Math.round((props.modelValue + delta) * 100) / 100;
  const clampedValue = Math.max(props.min, Math.min(props.max, newValue));
  emit('update:modelValue', clampedValue);
};
</script>

<style scoped>
.slider-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slider-input {
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: #e8e8e8;
  outline: none;
  -webkit-appearance: none;
}

.slider-input::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #3b82f6;
  cursor: pointer;
  transition: all 0.2s;
}

.slider-input::-webkit-slider-thumb:hover {
  transform: scale(1.2);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.2);
}

.slider-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.value-input {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  text-align: center;
}

.value-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.adjust-buttons {
  display: flex;
  gap: 4px;
}

.adjust-btn {
  width: 24px;
  height: 24px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.adjust-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #e6f7ff;
}
</style>
