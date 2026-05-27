"""
LLM 用户配置管理 API
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel
from app.core.logger import get_logger

logger = get_logger(__name__)

from app.core.database import get_db
from app.models.llm_user_config import LLMUserConfig


router = APIRouter(prefix="/api/v1/llm-config", tags=["llm-config"])


class LLMConfigRequest(BaseModel):
    """LLM 配置请求模型"""
    user_identifier: str
    provider: str = "custom"
    model: str
    api_key: Optional[str] = None
    base_url: Optional[str] = None
    temperature: float = 0.3
    max_tokens: int = 2048
    thinking: bool = False
    max_input_tokens: Optional[int] = 180000
    config_name: Optional[str] = None


class LLMConfigResponse(BaseModel):
    """LLM 配置响应模型"""
    success: bool
    message: str
    config: Optional[dict] = None
    configs: Optional[List[dict]] = None


@router.post("/save", response_model=LLMConfigResponse)
async def save_llm_config(request: LLMConfigRequest, db: Session = Depends(get_db)):
    """保存 LLM 配置
    
    - 如果用户已有激活配置，先停用
    - 保存新配置并设为激活状态
    - 自动刷新缓存
    """
    try:
        # 停用该用户的其他激活配置
        db.query(LLMUserConfig).filter(
            LLMUserConfig.user_identifier == request.user_identifier,
            LLMUserConfig.is_active == True
        ).update({"is_active": False})
        
        # 创建新配置
        new_config = LLMUserConfig(
            user_identifier=request.user_identifier,
            provider=request.provider,
            model=request.model,
            api_key=request.api_key,
            base_url=request.base_url,
            temperature=request.temperature,
            max_tokens=request.max_tokens,
            thinking=request.thinking,
            max_input_tokens=request.max_input_tokens,
            is_active=True,
            config_name=request.config_name
        )
        
        db.add(new_config)
        db.commit()
        db.refresh(new_config)
        
        logger.info(f"LLM config saved for user: {request.user_identifier}, model: {request.model}")
        
        # 刷新全局缓存
        from app.services.llm_service import llm_service
        if llm_service.refresh_config():
            logger.info("LLM 缓存已刷新")
        
        return {
            "success": True,
            "message": "配置保存成功",
            "config": new_config.to_dict()
        }
    
    except Exception as e:
        db.rollback()
        logger.error(f"Failed to save LLM config: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"保存失败: {str(e)}")


@router.get("/active/{user_identifier}", response_model=LLMConfigResponse)
async def get_active_config(user_identifier: str, db: Session = Depends(get_db)):
    """获取用户的激活配置"""
    try:
        config = db.query(LLMUserConfig).filter(
            LLMUserConfig.user_identifier == user_identifier,
            LLMUserConfig.is_active == True
        ).first()
        
        if not config:
            return {
                "success": False,
                "message": "未找到激活配置",
                "config": None
            }
        
        # 更新最后使用时间
        from datetime import datetime
        config.last_used_at = datetime.utcnow()
        db.commit()
        
        return {
            "success": True,
            "message": "获取成功",
            "config": config.to_full_dict()
        }
    
    except Exception as e:
        logger.error(f"Failed to get active config: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"获取失败: {str(e)}")


@router.get("/list/{user_identifier}", response_model=LLMConfigResponse)
async def list_user_configs(
    user_identifier: str,
    limit: int = 10,
    db: Session = Depends(get_db)
):
    """获取用户的所有配置列表"""
    try:
        configs = db.query(LLMUserConfig).filter(
            LLMUserConfig.user_identifier == user_identifier
        ).order_by(
            LLMUserConfig.updated_at.desc()
        ).limit(limit).all()
        
        return {
            "success": True,
            "message": f"获取到 {len(configs)} 个配置",
            "configs": [config.to_dict() for config in configs]
        }
    
    except Exception as e:
        logger.error(f"Failed to list configs: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"获取失败: {str(e)}")


@router.delete("/{config_id}", response_model=LLMConfigResponse)
async def delete_config(config_id: int, db: Session = Depends(get_db)):
    """删除指定配置"""
    try:
        config = db.query(LLMUserConfig).filter(
            LLMUserConfig.id == config_id
        ).first()
        
        if not config:
            raise HTTPException(status_code=404, detail="配置不存在")
        
        db.delete(config)
        db.commit()
        
        logger.info(f"LLM config deleted: id={config_id}")
        
        return {
            "success": True,
            "message": "配置删除成功"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        logger.error(f"Failed to delete config: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"删除失败: {str(e)}")


@router.post("/activate/{config_id}", response_model=LLMConfigResponse)
async def activate_config(config_id: int, db: Session = Depends(get_db)):
    """激活指定配置"""
    try:
        # 获取要激活的配置
        target_config = db.query(LLMUserConfig).filter(
            LLMUserConfig.id == config_id
        ).first()
        
        if not target_config:
            raise HTTPException(status_code=404, detail="配置不存在")
        
        # 停用该用户的其他激活配置
        db.query(LLMUserConfig).filter(
            LLMUserConfig.user_identifier == target_config.user_identifier,
            LLMUserConfig.is_active == True,
            LLMUserConfig.id != config_id
        ).update({"is_active": False})
        
        # 激活目标配置
        target_config.is_active = True
        db.commit()
        db.refresh(target_config)
        
        logger.info(f"LLM config activated: id={config_id}")
        
        return {
            "success": True,
            "message": "配置已激活",
            "config": target_config.to_full_dict()
        }
    
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        logger.error(f"Failed to activate config: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"激活失败: {str(e)}")
