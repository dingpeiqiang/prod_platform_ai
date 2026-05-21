"""
工作流管理服务
"""
import json
import logging
import uuid
import os
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import desc
from datetime import datetime

from app.models.workflow import Workflow, WorkflowHistory, WorkflowExecution

logger = logging.getLogger("workflow_service")

# 工作流文件存储目录
WORKFLOW_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../config/workflows"))


class WorkflowService:

    @classmethod
    def _list_workflows_from_files(cls) -> List[Dict[str, Any]]:
        """从文件加载工作流列表（不包含完整配置）"""
        workflows = []
        try:
            if os.path.exists(WORKFLOW_DIR):
                for filename in os.listdir(WORKFLOW_DIR):
                    if filename.endswith(".json"):
                        workflow_code = filename.replace(".json", "")
                        # 列表查询不包含完整配置
                        workflow = cls._load_workflow_from_file(workflow_code, include_data=False)
                        if workflow:
                            workflows.append(workflow)
        except Exception as e:
            logger.error(f"Failed to list workflows from files: {e}")
        return workflows

    @classmethod
    def list_workflows(cls, db: Session, category: Optional[str] = None, is_active: Optional[bool] = None) -> Dict[str, Any]:
        """获取工作流列表（合并文件和数据库中的工作流）"""
        try:
            # 从文件读取工作流
            file_workflows = cls._list_workflows_from_files()
            
            # 从数据库读取工作流
            query = db.query(Workflow)
            if category is not None:
                query = query.filter(Workflow.category == category)
            if is_active is not None:
                query = query.filter(Workflow.is_active == is_active)
            
            db_workflows = query.order_by(desc(Workflow.priority), desc(Workflow.created_at)).all()
            db_workflows_list = [workflow.to_dict() for workflow in db_workflows]
            
            # 合并工作流（文件工作流优先，避免重复）
            seen_codes = set()
            all_workflows = []
            
            # 添加文件工作流
            for wf in file_workflows:
                code = wf.get("workflowCode")
                if code and code not in seen_codes:
                    seen_codes.add(code)
                    all_workflows.append(wf)
            
            # 添加数据库工作流（排除已在文件中存在的）
            for wf in db_workflows_list:
                code = wf.get("workflowCode")
                if code and code not in seen_codes:
                    seen_codes.add(code)
                    all_workflows.append(wf)
            
            # 应用过滤条件到合并后的结果
            if category is not None:
                all_workflows = [w for w in all_workflows if w.get("category") == category]
            if is_active is not None:
                active_key = "isActive" if "isActive" in all_workflows[0] else "is_active" if all_workflows else "isActive"
                all_workflows = [w for w in all_workflows if w.get(active_key) == is_active]
            
            logger.info(f"Loaded {len(all_workflows)} workflows (files: {len(file_workflows)}, db: {len(db_workflows_list)})")
            
            return {
                "success": True,
                "total": len(all_workflows),
                "data": all_workflows
            }
        except Exception as e:
            logger.exception(f"Failed to list workflows: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def _load_workflow_from_file(cls, workflow_code: str, include_data: bool = True) -> Optional[Dict[str, Any]]:
        """从文件加载工作流
        
        Args:
            workflow_code: 工作流编码
            include_data: 是否包含完整的工作流配置（列表查询时设为False）
        """
        try:
            filepath = os.path.join(WORKFLOW_DIR, f"{workflow_code}.json")
            if os.path.exists(filepath):
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # 转换为数据库格式（使用驼峰式命名，与模型 to_dict 一致）
                result = {
                    "id": 0,
                    "workflowCode": data.get("id", workflow_code),
                    "workflowName": data.get("name", workflow_code),
                    "description": data.get("description"),
                    "category": data.get("category", "general"),
                    "tags": data.get("tags", []),
                    "priority": 10,
                    "isActive": True,
                    "isInLibrary": False,
                    "version": 1,
                    "executionCount": 0,
                    "lastExecutionAt": None,
                    "lastExecutionStatus": None,
                    "createdBy": None,
                    "updatedBy": None,
                    "createdAt": data.get("createdAt"),
                    "updatedAt": data.get("updatedAt")
                }
                
                # 只有在需要时才包含完整配置
                if include_data:
                    result["workflowData"] = data.get("workflowData", {})
                
                return result
            return None
        except Exception as e:
            logger.error(f"Failed to load workflow from file {workflow_code}: {e}")
            return None

    @classmethod
    def get_workflow(cls, workflow_code: str, db: Session) -> Dict[str, Any]:
        """获取单个工作流（优先从文件读取）"""
        try:
            # 优先从文件读取
            file_workflow = cls._load_workflow_from_file(workflow_code)
            if file_workflow:
                logger.info(f"Loaded workflow {workflow_code} from file")
                return {"success": True, "data": file_workflow}
            
            # 如果文件不存在，从数据库读取
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"Workflow {workflow_code} not found"}
            return {"success": True, "data": workflow.to_dict()}
        except Exception as e:
            logger.exception(f"Failed to get workflow {workflow_code}: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def create_workflow(cls, workflow_data: Dict[str, Any], db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """创建工作流"""
        try:
            workflow_code = workflow_data.get("workflowCode")
            if not workflow_code:
                return {"success": False, "message": "workflowCode is required"}

            existing = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if existing:
                return {"success": False, "message": f"Workflow {workflow_code} already exists"}

            workflow = Workflow(
                workflow_code=workflow_code,
                workflow_name=workflow_data.get("workflowName", workflow_code),
                description=workflow_data.get("description"),
                category=workflow_data.get("category", "general"),
                tags=workflow_data.get("tags", []),
                priority=workflow_data.get("priority", 10),
                is_active=workflow_data.get("isActive", True),
                is_in_library=workflow_data.get("isInLibrary", False),
                workflow_data=workflow_data.get("workflowData", {}),
                version=1,
                created_by=user,
                updated_by=user
            )

            db.add(workflow)
            db.flush()

            # 保存初始版本历史
            history = WorkflowHistory(
                workflow_id=workflow.id,
                workflow_code=workflow.workflow_code,
                version=1,
                workflow_name=workflow.workflow_name,
                description=workflow.description,
                workflow_data=workflow.workflow_data,
                category=workflow.category,
                tags=workflow.tags,
                priority=workflow.priority,
                is_active=workflow.is_active,
                change_note="Initial version",
                created_by=user
            )
            db.add(history)

            db.commit()
            db.refresh(workflow)

            logger.info(f"Created workflow: {workflow_code}")
            return {"success": True, "data": workflow.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to create workflow: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def update_workflow(cls, workflow_code: str, workflow_data: Dict[str, Any], db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """更新工作流"""
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"Workflow {workflow_code} not found"}

            # 保存历史版本
            old_version = workflow.version
            history = WorkflowHistory(
                workflow_id=workflow.id,
                workflow_code=workflow.workflow_code,
                version=old_version,
                workflow_name=workflow.workflow_name,
                description=workflow.description,
                workflow_data=workflow.workflow_data,
                category=workflow.category,
                tags=workflow.tags,
                priority=workflow.priority,
                is_active=workflow.is_active,
                change_note=workflow_data.get("changeNote", f"Updated to version {old_version + 1}"),
                created_by=user
            )
            db.add(history)

            # 更新工作流
            if "workflowName" in workflow_data:
                workflow.workflow_name = workflow_data["workflowName"]
            if "description" in workflow_data:
                workflow.description = workflow_data["description"]
            if "category" in workflow_data:
                workflow.category = workflow_data["category"]
            if "tags" in workflow_data:
                workflow.tags = workflow_data["tags"]
            if "priority" in workflow_data:
                workflow.priority = workflow_data["priority"]
            if "isActive" in workflow_data:
                workflow.is_active = workflow_data["isActive"]
            if "isInLibrary" in workflow_data:
                workflow.is_in_library = workflow_data["isInLibrary"]
            if "workflowData" in workflow_data:
                workflow.workflow_data = workflow_data["workflowData"]

            workflow.version = old_version + 1
            workflow.updated_by = user

            db.commit()
            db.refresh(workflow)

            logger.info(f"Updated workflow: {workflow_code} to version {workflow.version}")
            return {"success": True, "data": workflow.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to update workflow: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def delete_workflow(cls, workflow_code: str, db: Session) -> Dict[str, Any]:
        """删除工作流"""
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"Workflow {workflow_code} not found"}

            db.delete(workflow)
            db.commit()

            logger.info(f"Deleted workflow: {workflow_code}")
            return {"success": True, "message": f"Workflow {workflow_code} deleted successfully"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to delete workflow: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def toggle_workflow(cls, workflow_code: str, db: Session) -> Dict[str, Any]:
        """切换工作流启用状态"""
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"Workflow {workflow_code} not found"}

            workflow.is_active = not workflow.is_active
            db.commit()
            db.refresh(workflow)

            return {"success": True, "data": workflow.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to toggle workflow: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def get_workflow_history(cls, workflow_code: str, db: Session) -> Dict[str, Any]:
        """获取工作流版本历史"""
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"Workflow {workflow_code} not found"}

            history = db.query(WorkflowHistory).filter(
                WorkflowHistory.workflow_code == workflow_code
            ).order_by(desc(WorkflowHistory.version)).all()

            return {
                "success": True,
                "data": [h.to_dict() for h in history]
            }
        except Exception as e:
            logger.exception(f"Failed to get workflow history: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def create_execution(cls, workflow_code: str, execution_data: Dict[str, Any], db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """创建工作流执行记录"""
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"Workflow {workflow_code} not found"}

            execution_id = str(uuid.uuid4())[:8]

            execution = WorkflowExecution(
                workflow_id=workflow.id,
                workflow_code=workflow_code,
                execution_id=execution_id,
                status='pending',
                input_data=execution_data.get('inputData', {}),
                triggered_by=user,
                trigger_type=execution_data.get('triggerType', 'manual'),
                notes=execution_data.get('notes')
            )

            db.add(execution)
            db.flush()

            # 更新工作流统计
            workflow.execution_count += 1

            db.commit()
            db.refresh(execution)

            return {"success": True, "data": execution.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to create execution: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def update_execution_status(cls, execution_id: str, status_data: Dict[str, Any], db: Session) -> Dict[str, Any]:
        """更新执行状态"""
        try:
            execution = db.query(WorkflowExecution).filter(WorkflowExecution.execution_id == execution_id).first()
            if not execution:
                return {"success": False, "message": f"Execution {execution_id} not found"}

            if "status" in status_data:
                execution.status = status_data["status"]
            if "startTime" in status_data:
                execution.start_time = datetime.fromisoformat(status_data["startTime"].replace('Z', '+00:00'))
            if "endTime" in status_data:
                execution.end_time = datetime.fromisoformat(status_data["endTime"].replace('Z', '+00:00'))
            if "durationSeconds" in status_data:
                execution.duration_seconds = status_data["durationSeconds"]
            if "outputData" in status_data:
                execution.output_data = status_data["outputData"]
            if "errorMessage" in status_data:
                execution.error_message = status_data["errorMessage"]
            if "executionLogs" in status_data:
                execution.execution_logs = status_data["executionLogs"]

            db.commit()
            db.refresh(execution)

            # 更新工作流最后执行信息
            workflow = db.query(Workflow).filter(Workflow.id == execution.workflow_id).first()
            if workflow:
                workflow.last_execution_at = datetime.now()
                workflow.last_execution_status = execution.status

            db.commit()

            return {"success": True, "data": execution.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to update execution: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def list_executions(cls, workflow_code: str, db: Session, limit: int = 50) -> Dict[str, Any]:
        """获取工作流执行历史"""
        try:
            executions = db.query(WorkflowExecution).filter(
                WorkflowExecution.workflow_code == workflow_code
            ).order_by(desc(WorkflowExecution.created_at)).limit(limit).all()

            return {
                "success": True,
                "data": [e.to_dict() for e in executions]
            }
        except Exception as e:
            logger.exception(f"Failed to list executions: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def copy_workflow(cls, source_workflow_code: str, new_workflow_code: str, db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """复制工作流"""
        try:
            # 获取源工作流
            source_workflow = db.query(Workflow).filter(Workflow.workflow_code == source_workflow_code).first()
            if not source_workflow:
                return {"success": False, "message": f"源工作流 {source_workflow_code} 不存在"}

            # 检查新工作流代码是否已存在
            existing = db.query(Workflow).filter(Workflow.workflow_code == new_workflow_code).first()
            if existing:
                return {"success": False, "message": f"工作流 {new_workflow_code} 已存在"}

            # 创建新工作流（复制源工作流的配置）
            new_workflow = Workflow(
                workflow_code=new_workflow_code,
                workflow_name=f"{source_workflow.workflow_name} (副本)",
                description=source_workflow.description,
                category=source_workflow.category,
                tags=source_workflow.tags,
                priority=source_workflow.priority,
                is_active=True,  # 复制的工作流默认启用
                is_in_library=False,  # 复制的工作流默认不纳入工作流库
                workflow_data=source_workflow.workflow_data,
                version=1,
                created_by=user,
                updated_by=user
            )

            db.add(new_workflow)
            db.flush()

            # 保存初始版本历史
            history = WorkflowHistory(
                workflow_id=new_workflow.id,
                workflow_code=new_workflow.workflow_code,
                version=1,
                workflow_name=new_workflow.workflow_name,
                description=new_workflow.description,
                workflow_data=new_workflow.workflow_data,
                category=new_workflow.category,
                tags=new_workflow.tags,
                priority=new_workflow.priority,
                is_active=new_workflow.is_active,
                change_note=f"从 {source_workflow_code} 复制",
                created_by=user
            )
            db.add(history)

            db.commit()
            db.refresh(new_workflow)

            logger.info(f"Copied workflow: {source_workflow_code} -> {new_workflow_code}")
            return {"success": True, "data": new_workflow.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to copy workflow: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def get_categories(cls) -> Dict[str, Any]:
        """获取工作流分类列表"""
        return {
            "success": True,
            "data": [
                {"code": "general", "name": "通用"},
                {"code": "ai", "name": "AI应用"},
                {"code": "data", "name": "数据处理"},
                {"code": "integration", "name": "系统集成"},
                {"code": "automation", "name": "自动化"}
            ]
        }
