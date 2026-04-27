-- ===============================================
-- 历史推荐性能优化 - 数据库索引脚本
-- 执行方式: 连接到数据库执行此脚本
-- ===============================================

-- 1. FormInstance 表复合索引
-- 优化查询: WHERE form_code = ? AND status = 'submitted' ORDER BY submitted_at DESC
CREATE INDEX IF NOT EXISTS idx_form_instances_form_status_time
ON form_instances(form_code, status, submitted_at DESC);

-- 优化查询: WHERE form_code = ? AND user_id = ? AND status = 'submitted'
CREATE INDEX IF NOT EXISTS idx_form_instances_form_user_status
ON form_instances(form_code, user_id, status);

-- 优化查询: WHERE form_code = ? AND status = 'submitted' AND submitted_at >= ?
CREATE INDEX IF NOT EXISTS idx_form_instances_form_status_recent
ON form_instances(form_code, status, submitted_at DESC)
WHERE status = 'submitted';

-- 2. FormHistory 表索引（已有基础索引，加强）
-- 优化查询: WHERE form_code = ? AND field_code = ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_form_history_field_time
ON form_history(form_code, field_code, created_at DESC);

-- 优化聚合查询: GROUP BY field_value
CREATE INDEX IF NOT EXISTS idx_form_history_field_value
ON form_history(form_code, field_code, field_value);

-- ===============================================
-- 推荐: 创建聚合表存储预计算的统计结果
-- ===============================================

-- 创建字段频率聚合表
CREATE TABLE IF NOT EXISTS field_value_stats (
    id SERIAL PRIMARY KEY,
    form_code VARCHAR(100) NOT NULL,
    field_code VARCHAR(100) NOT NULL,
    field_value TEXT NOT NULL,
    total_count INTEGER DEFAULT 0,
    user_counts JSONB DEFAULT '{}',  -- {"user_id": count, ...}
    last_used_at TIMESTAMP,
    recent_count_7d INTEGER DEFAULT 0,  -- 近7天计数
    recent_count_30d INTEGER DEFAULT 0, -- 近30天计数
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(form_code, field_code, field_value)
);

-- 创建聚合表索引
CREATE INDEX IF NOT EXISTS idx_field_value_stats_lookup
ON field_value_stats(form_code, field_code, total_count DESC);

CREATE INDEX IF NOT EXISTS idx_field_value_stats_recent
ON field_value_stats(form_code, field_code, recent_count_30d DESC);

-- 定期更新聚合表的任务（建议每天凌晨执行）
-- 可使用 pg_cron 或外部调度器
