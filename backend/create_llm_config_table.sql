-- LLM 用户配置表创建脚本
-- 在 MySQL 客户端中执行此脚本

USE prodplatformai;

CREATE TABLE IF NOT EXISTS llm_user_configs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_identifier VARCHAR(100) NOT NULL COMMENT '用户标识',
    provider VARCHAR(50) NOT NULL DEFAULT 'custom' COMMENT 'Provider 类型',
    model VARCHAR(100) NOT NULL COMMENT '模型名称',
    api_key TEXT COMMENT 'API Key（加密存储）',
    base_url TEXT COMMENT 'Base URL',
    temperature FLOAT NOT NULL DEFAULT 0.3 COMMENT '温度参数',
    max_tokens INT NOT NULL DEFAULT 2048 COMMENT '最大 token 数',
    thinking BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否启用思考模式',
    max_input_tokens INT DEFAULT 180000 COMMENT '最大输入 token 数',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否为当前激活配置',
    config_name VARCHAR(100) COMMENT '配置名称（可选）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_used_at DATETIME COMMENT '最后使用时间',
    
    INDEX idx_user_identifier (user_identifier),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 用户配置表';

-- 验证表是否创建成功
SHOW TABLES LIKE 'llm_user_configs';
DESCRIBE llm_user_configs;
