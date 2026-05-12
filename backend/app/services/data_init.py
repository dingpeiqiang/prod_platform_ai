"""
数据初始化服务
用于初始化系统所需的基础数据（场景、提示词等）
"""
import json
import sys
from pathlib import Path
from typing import Optional
import logging

from app.core.database import SessionLocal
from app.models.scene import Scene
from app.models.prompt import Prompt
from app.models.form import Form

logger = logging.getLogger(__name__)


def init_scenes(db):
    """初始化场景数据 - 场景应从数据库查询，不从文件加载"""
    from app.models.scene import Scene
    
    try:
        # 检查数据库中是否已有场景数据
        scene_count = db.query(Scene).count()
        if scene_count > 0:
            logger.info("数据库中已有 %d 个场景，跳过初始化", scene_count)
            return
        
        # 如果数据库中没有场景数据，提示用户
        logger.warning(
            "数据库中没有场景数据！\n"
            "请执行以下操作之一：\n"
            "1. 运行初始化脚本: python init_new_scene_data.py\n"
            "2. 从备份恢复场景数据\n"
            "3. 通过管理界面手动添加场景"
        )
        # 不再从文件系统加载场景配置
        
    except Exception as e:
        logger.error("检查场景数据失败: %s", e, exc_info=True)


def init_prompts(db):
    """初始化提示词数据"""
    try:
        prompts_dir = Path(__file__).parent.parent.parent / 'config' / 'prompts' / 'scenes'
        if not prompts_dir.exists():
            logger.warning("提示词目录不存在: %s", prompts_dir)
            return
        
        imported_count = 0
        for prompt_file in prompts_dir.glob('*.txt'):
            if prompt_file.name.startswith('_'):
                continue
                
            code = prompt_file.stem
            existing = db.query(Prompt).filter(Prompt.code == code).first()
            if existing:
                continue
            
            with open(prompt_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            category = 'form'
            if 'qa' in code.lower():
                category = 'qa'
            elif 'tool' in code.lower():
                category = 'tool'
            elif 'chat' in code.lower():
                category = 'chat'
            
            name = code.replace('_', ' ').title()
            
            prompt = Prompt(
                code=code,
                name=name,
                description=f"场景提示词: {name}",
                category=category,
                content=content,
                variables=[],
                tools=[],
                is_template=False,
                version=1,
                is_active=True,
                created_by='system'
            )
            db.add(prompt)
            imported_count += 1
            logger.info("导入提示词: %s", code)
        
        if imported_count > 0:
            db.commit()
            logger.info("提示词数据初始化完成，共导入 %d 个提示词", imported_count)
        
    except Exception as e:
        db.rollback()
        logger.error("初始化提示词数据失败: %s", e, exc_info=True)


def init_ontologies(db):
    """初始化本体数据 - 暂不自动导入"""
    # 本体数据通过config_loader自动加载，或通过管理界面导入
    pass


def init_all_data():
    """初始化所有数据"""
    logger.info("=" * 60)
    logger.info("开始初始化系统数据")
    logger.info("=" * 60)
    
    db = SessionLocal()
    try:
        init_scenes(db)
        init_prompts(db)
        init_ontologies(db)
        
        logger.info("=" * 60)
        logger.info("系统数据初始化完成")
        logger.info("=" * 60)
    finally:
        db.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    init_all_data()
