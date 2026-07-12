<script setup>
defineProps({
  productCount: { type: Number, default: 0 },
})
defineEmits(['open-product-list'])
</script>

<template>
  <header class="header">
    <div class="header-title">
      <div class="icon" aria-hidden="true">
        <i class="fa-solid fa-microchip" />
      </div>
      <h1>产品智能配置助手</h1>
    </div>
    <div class="header-actions">
      <button type="button" class="header-btn" @click="$emit('open-product-list')">
        <i class="fa-solid fa-list" />
        <span class="btn-text">商品列表</span>
        <span class="badge">{{ productCount }}</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.header {
  background: linear-gradient(135deg, var(--primary-color) 0%, var(--primary-dark) 100%);
  color: white;
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: var(--shadow);
  flex-shrink: 0;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title h1 {
  font-size: 20px;
  font-weight: 600;
}

.icon {
  width: 36px;
  height: 36px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.header-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  transition: all 0.2s;
}

.header-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

.badge {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: 4px;
  font-size: 12px;
}

@media (max-width: 767px) {
  .header {
    padding: 12px 14px;
    padding-top: max(12px, env(safe-area-inset-top, 0px));
    gap: 8px;
  }

  .header-title h1 {
    font-size: 16px;
    line-height: 1.3;
  }

  .icon {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }

  .header-btn {
    padding: 8px 10px;
    font-size: 13px;
    min-height: 44px;
  }

  @media (max-width: 380px) {
    .header-btn .btn-text {
      display: none;
    }
  }
}
</style>