"""
定时刷新字段统计聚合表

使用方式：
1. 配置到定时任务（如 pg_cron、Celery Beat）
2. 或手动执行：python -m scripts.refresh_field_stats

推荐执行频率：每天凌晨 2:00
"""

import sys
import os
from datetime import datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("refresh_field_stats")

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://postgres:postgres@localhost:5432/prod_platform_ai"
)


def create_aggregate_table_if_not_exists(engine):
    """创建聚合表（如果不存在）"""
    create_table_sql = """
    CREATE TABLE IF NOT EXISTS field_value_stats (
        id SERIAL PRIMARY KEY,
        form_code VARCHAR(100) NOT NULL,
        field_code VARCHAR(100) NOT NULL,
        field_value TEXT NOT NULL,
        total_count INTEGER DEFAULT 0,
        user_counts JSONB DEFAULT '{}',
        last_used_at TIMESTAMP,
        recent_count_7d INTEGER DEFAULT 0,
        recent_count_30d INTEGER DEFAULT 0,
        updated_at TIMESTAMP DEFAULT NOW(),
        UNIQUE(form_code, field_code, field_value)
    );
    """

    create_index_sql = """
    CREATE INDEX IF NOT EXISTS idx_field_value_stats_lookup
    ON field_value_stats(form_code, field_code, total_count DESC);
    """

    with engine.connect() as conn:
        conn.execute(text(create_table_sql))
        conn.execute(text(create_index_sql))
        conn.commit()

    logger.info("[refresh_field_stats] 聚合表检查完成")


def refresh_aggregate_stats(engine, form_codes: list = None):
    """
    刷新聚合统计数据

    策略：
    1. 使用SQL聚合减少Python处理
    2. 分批处理避免锁表
    3. 增量更新已有记录
    """
    now = datetime.now()
    cutoff_7d = now - timedelta(days=7)
    cutoff_30d = now - timedelta(days=30)

    if form_codes is None:
        form_codes_sql = text("""
            SELECT DISTINCT form_code FROM form_instances
            WHERE status = 'submitted' AND submitted_at IS NOT NULL
        """)
        with engine.connect() as conn:
            result = conn.execute(form_codes_sql)
            form_codes = [row[0] for row in result]

    logger.info(f"[refresh_field_stats] 开始刷新 {len(form_codes)} 个表单的统计")

    total_updated = 0

    for form_code in form_codes:
        try:
            upsert_sql = text("""
                INSERT INTO field_value_stats (
                    form_code,
                    field_code,
                    field_value,
                    total_count,
                    user_counts,
                    last_used_at,
                    recent_count_7d,
                    recent_count_30d,
                    updated_at
                )
                SELECT
                    f.form_code,
                    kv.key as field_code,
                    kv.value::text as field_value,
                    COUNT(*) as total_count,
                    jsonb_object_agg(
                        COALESCE(f.user_id, 'anonymous'),
                        COUNT(*)
                    ) FILTER (WHERE f.user_id IS NOT NULL) as user_counts,
                    MAX(f.submitted_at) as last_used_at,
                    SUM(CASE WHEN f.submitted_at >= :cutoff_7d THEN 1 ELSE 0 END) as recent_count_7d,
                    SUM(CASE WHEN f.submitted_at >= :cutoff_30d THEN 1 ELSE 0 END) as recent_count_30d,
                    NOW() as updated_at
                FROM form_instances f,
                     jsonb_each_text(f.data) AS kv
                WHERE f.form_code = :form_code
                    AND f.status = 'submitted'
                    AND f.data IS NOT NULL
                    AND kv.value IS NOT NULL
                    AND kv.value != ''
                GROUP BY f.form_code, kv.key, kv.value
                ON CONFLICT (form_code, field_code, field_value)
                DO UPDATE SET
                    total_count = EXCLUDED.total_count,
                    user_counts = EXCLUDED.user_counts,
                    last_used_at = EXCLUDED.last_used_at,
                    recent_count_7d = EXCLUDED.recent_count_7d,
                    recent_count_30d = EXCLUDED.recent_count_30d,
                    updated_at = NOW();

                -- 清理已删除表单的历史统计
                DELETE FROM field_value_stats
                WHERE form_code = :form_code
                AND NOT EXISTS (
                    SELECT 1 FROM form_instances
                    WHERE form_code = :form_code
                    AND status = 'submitted'
                    AND data IS NOT NULL
                );
            """)

            with engine.connect() as conn:
                result = conn.execute(upsert_sql, {
                    "form_code": form_code,
                    "cutoff_7d": cutoff_7d,
                    "cutoff_30d": cutoff_30d
                })
                conn.commit()

                updated = result.rowcount if result.rowcount else 0
                total_updated += 1
                logger.debug(f"[refresh_field_stats] 表单 {form_code}: 已更新")

        except Exception as e:
            logger.error(f"[refresh_field_stats] 表单 {form_code} 刷新失败: {e}")
            continue

    logger.info(f"[refresh_field_stats] 刷新完成: {total_updated}/{len(form_codes)} 表单已处理")


def cleanup_old_stats(engine, days_threshold: int = 90):
    """清理旧统计数据"""
    cutoff_date = datetime.now() - timedelta(days=days_threshold)

    cleanup_sql = text("""
        DELETE FROM field_value_stats
        WHERE updated_at < :cutoff_date
        AND form_code NOT IN (
            SELECT DISTINCT form_code FROM form_instances
            WHERE submitted_at >= :cutoff_date
        )
    """)

    with engine.connect() as conn:
        result = conn.execute(cleanup_sql, {"cutoff_date": cutoff_date})
        conn.commit()
        deleted = result.rowcount

    logger.info(f"[refresh_field_stats] 清理完成: 删除了 {deleted} 条过期记录")


def main():
    logger.info("[refresh_field_stats] 启动聚合表刷新任务")
    logger.info(f"[refresh_field_stats] 数据库: {DATABASE_URL.split('@')[1] if '@' in DATABASE_URL else 'localhost'}")

    engine = create_engine(DATABASE_URL)

    create_aggregate_table_if_not_exists(engine)

    refresh_aggregate_stats(engine)

    cleanup_old_stats(engine, days_threshold=90)

    logger.info("[refresh_field_stats] 任务完成")


if __name__ == "__main__":
    main()
