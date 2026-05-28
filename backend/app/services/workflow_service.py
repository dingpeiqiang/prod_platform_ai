"""
工作流管理服务
"""
from app.core.logger import get_logger

logger = get_logger(__name__)
import uuid
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import desc, or_, and_, func
from datetime import datetime

from app.models.workflow import Workflow, WorkflowHistory, WorkflowExecution



class WorkflowService:

    @classmethod
    def list_workflows(
        cls, 
        db: Session, 
        category: Optional[str] = None, 
        is_active: Optional[bool] = None,
        keyword: Optional[str] = None,
        workflow_code: Optional[str] = None,
        tags: Optional[List[str]] = None,
        created_by: Optional[str] = None,
        min_execution_count: Optional[int] = None,
        max_execution_count: Optional[int] = None,
        sort_by: Optional[str] = None,
        sort_order: Optional[str] = "desc",
        page: Optional[int] = 1,
        page_size: Optional[int] = 20
    ) -> Dict[str, Any]:
        """
        获取工作流列表（支持按条件检索和分页）
        
        Args:
            db: 数据库会话
            category: 分类过滤
            is_active: 启用状态过滤
            keyword: 关键词搜索（匹配名称、编码、描述）
            workflow_code: 工作流编码精确匹配
            tags: 标签过滤（包含任一标签）
            created_by: 创建者过滤
            min_execution_count: 最小执行次数
            max_execution_count: 最大执行次数
            sort_by: 排序字段（created_at, updated_at, priority, execution_count）
            sort_order: 排序方向（asc, desc）
            page: 页码（从1开始）
            page_size: 每页数量
        
        Returns:
            工作流列表及分页信息
        """
        try:
            query = db.query(Workflow)
            
            # 分类过滤
            if category is not None:
                query = query.filter(Workflow.category == category)
            
            # 启用状态过滤
            if is_active is not None:
                query = query.filter(Workflow.is_active == is_active)
            
            # 工作流编码精确匹配
            if workflow_code is not None:
                query = query.filter(Workflow.workflow_code == workflow_code)
            
            # 关键词搜索（匹配名称、编码、描述）
            if keyword is not None and keyword.strip():
                keyword_pattern = f"%{keyword.strip()}%"
                query = query.filter(or_(
                    Workflow.workflow_code.like(keyword_pattern),
                    Workflow.workflow_name.like(keyword_pattern),
                    Workflow.description.like(keyword_pattern)
                ))
            
            # 标签过滤（包含任一标签）
            if tags is not None and len(tags) > 0:
                for tag in tags:
                    query = query.filter(Workflow.tags.any(tag))
            
            # 创建者过滤
            if created_by is not None:
                query = query.filter(Workflow.created_by == created_by)
            
            # 执行次数范围过滤
            if min_execution_count is not None:
                query = query.filter(Workflow.execution_count >= min_execution_count)
            if max_execution_count is not None:
                query = query.filter(Workflow.execution_count <= max_execution_count)
            
            # 统计总记录数
            total = query.count()
            
            # 排序
            sort_column = Workflow.created_at
            if sort_by == "updated_at":
                sort_column = Workflow.updated_at
            elif sort_by == "priority":
                sort_column = Workflow.priority
            elif sort_by == "execution_count":
                sort_column = Workflow.execution_count
            
            if sort_order == "asc":
                query = query.order_by(sort_column)
            else:
                query = query.order_by(desc(sort_column))
            
            # 分页
            offset = (page - 1) * page_size if page > 0 else 0
            query = query.offset(offset).limit(page_size)
            
            db_workflows = query.all()
            all_workflows = [workflow.to_dict() for workflow in db_workflows]
            
            logger.info(f"Loaded {len(all_workflows)} workflows from database (total: {total})")
            
            return {
                "success": True,
                "total": total,
                "page": page,
                "page_size": page_size,
                "total_pages": (total + page_size - 1) // page_size,
                "data": all_workflows
            }
        except Exception as e:
            logger.exception(f"Failed to list workflows: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def publish_workflow(cls, workflow_code: str, db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """
        发布工作流（上线滚动）
        
        将工作流从草稿状态发布为上线状态，支持版本管理和变更记录。
        
        Args:
            workflow_code: 工作流编码
            db: 数据库会话
            user: 操作人
        
        Returns:
            发布结果
        """
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"工作流 {workflow_code} 不存在"}
            
            # 记录发布前的版本历史
            history = WorkflowHistory(
                workflow_id=workflow.id,
                workflow_code=workflow.workflow_code,
                version=workflow.version,
                workflow_name=workflow.workflow_name,
                description=workflow.description,
                workflow_data=workflow.workflow_data,
                category=workflow.category,
                tags=workflow.tags,
                priority=workflow.priority,
                is_active=workflow.is_active,
                change_note=f"发布前版本 {workflow.version}",
                created_by=user
            )
            db.add(history)
            
            # 更新工作流状态为启用
            workflow.is_active = True
            workflow.version += 1
            workflow.updated_by = user
            
            db.commit()
            db.refresh(workflow)
            
            logger.info(f"Published workflow: {workflow_code} (version {workflow.version})")
            return {"success": True, "data": workflow.to_dict(), "message": f"工作流 {workflow_code} 已成功发布"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to publish workflow: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def unpublish_workflow(cls, workflow_code: str, db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """
        下线工作流
        
        将工作流从上线状态下线，停止对外服务。
        
        Args:
            workflow_code: 工作流编码
            db: 数据库会话
            user: 操作人
        
        Returns:
            下线结果
        """
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"工作流 {workflow_code} 不存在"}
            
            # 记录下线前的版本历史
            history = WorkflowHistory(
                workflow_id=workflow.id,
                workflow_code=workflow.workflow_code,
                version=workflow.version,
                workflow_name=workflow.workflow_name,
                description=workflow.description,
                workflow_data=workflow.workflow_data,
                category=workflow.category,
                tags=workflow.tags,
                priority=workflow.priority,
                is_active=workflow.is_active,
                change_note=f"下线前版本 {workflow.version}",
                created_by=user
            )
            db.add(history)
            
            # 更新工作流状态为禁用
            workflow.is_active = False
            workflow.version += 1
            workflow.updated_by = user
            
            db.commit()
            db.refresh(workflow)
            
            logger.info(f"Unpublished workflow: {workflow_code}")
            return {"success": True, "data": workflow.to_dict(), "message": f"工作流 {workflow_code} 已成功下线"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to unpublish workflow: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def batch_publish(cls, workflow_codes: List[str], db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """
        批量发布工作流（滚动发布）
        
        Args:
            workflow_codes: 工作流编码列表
            db: 数据库会话
            user: 操作人
        
        Returns:
            批量发布结果
        """
        results = {
            "success": [],
            "failed": []
        }
        
        for workflow_code in workflow_codes:
            result = cls.publish_workflow(workflow_code, db, user)
            if result["success"]:
                results["success"].append({"workflow_code": workflow_code, "message": result.get("message")})
            else:
                results["failed"].append({"workflow_code": workflow_code, "message": result.get("message")})
        
        return {
            "success": True,
            "total": len(workflow_codes),
            "success_count": len(results["success"]),
            "failed_count": len(results["failed"]),
            "results": results
        }

    @classmethod
    def rollback_version(cls, workflow_code: str, target_version: int, db: Session, user: Optional[str] = None) -> Dict[str, Any]:
        """
        回滚工作流到指定版本
        
        Args:
            workflow_code: 工作流编码
            target_version: 目标版本号
            db: 数据库会话
            user: 操作人
        
        Returns:
            回滚结果
        """
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"工作流 {workflow_code} 不存在"}
            
            # 获取目标版本的历史记录
            history = db.query(WorkflowHistory).filter(
                WorkflowHistory.workflow_code == workflow_code,
                WorkflowHistory.version == target_version
            ).first()
            
            if not history:
                return {"success": False, "message": f"版本 {target_version} 不存在"}
            
            # 记录当前版本到历史
            current_history = WorkflowHistory(
                workflow_id=workflow.id,
                workflow_code=workflow.workflow_code,
                version=workflow.version,
                workflow_name=workflow.workflow_name,
                description=workflow.description,
                workflow_data=workflow.workflow_data,
                category=workflow.category,
                tags=workflow.tags,
                priority=workflow.priority,
                is_active=workflow.is_active,
                change_note=f"回滚前版本 {workflow.version}",
                created_by=user
            )
            db.add(current_history)
            
            # 恢复目标版本的数据
            workflow.workflow_name = history.workflow_name
            workflow.description = history.description
            workflow.workflow_data = history.workflow_data
            workflow.category = history.category
            workflow.tags = history.tags
            workflow.priority = history.priority
            workflow.is_active = history.is_active
            workflow.version += 1
            workflow.updated_by = user
            
            db.commit()
            db.refresh(workflow)
            
            logger.info(f"Rolled back workflow {workflow_code} to version {target_version}")
            return {"success": True, "data": workflow.to_dict(), "message": f"工作流 {workflow_code} 已回滚到版本 {target_version}"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to rollback workflow: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def compare_versions(cls, workflow_code: str, version1: int, version2: int, db: Session) -> Dict[str, Any]:
        """
        比较两个版本的差异
        
        Args:
            workflow_code: 工作流编码
            version1: 版本1
            version2: 版本2
            db: 数据库会话
        
        Returns:
            版本差异信息
        """
        try:
            # 获取两个版本的历史记录
            history1 = db.query(WorkflowHistory).filter(
                WorkflowHistory.workflow_code == workflow_code,
                WorkflowHistory.version == version1
            ).first()
            
            history2 = db.query(WorkflowHistory).filter(
                WorkflowHistory.workflow_code == workflow_code,
                WorkflowHistory.version == version2
            ).first()
            
            if not history1:
                return {"success": False, "message": f"版本 {version1} 不存在"}
            if not history2:
                return {"success": False, "message": f"版本 {version2} 不存在"}
            
            # 比较字段差异
            diff = {
                "workflow_code": workflow_code,
                "version1": version1,
                "version2": version2,
                "changes": []
            }
            
            # 比较工作流名称
            if history1.workflow_name != history2.workflow_name:
                diff["changes"].append({
                    "field": "workflow_name",
                    "label": "工作流名称",
                    "value1": history1.workflow_name,
                    "value2": history2.workflow_name
                })
            
            # 比较描述
            if history1.description != history2.description:
                diff["changes"].append({
                    "field": "description",
                    "label": "描述",
                    "value1": history1.description,
                    "value2": history2.description
                })
            
            # 比较分类
            if history1.category != history2.category:
                diff["changes"].append({
                    "field": "category",
                    "label": "分类",
                    "value1": history1.category,
                    "value2": history2.category
                })
            
            # 比较优先级
            if history1.priority != history2.priority:
                diff["changes"].append({
                    "field": "priority",
                    "label": "优先级",
                    "value1": history1.priority,
                    "value2": history2.priority
                })
            
            # 比较启用状态
            if history1.is_active != history2.is_active:
                diff["changes"].append({
                    "field": "is_active",
                    "label": "启用状态",
                    "value1": "启用" if history1.is_active else "禁用",
                    "value2": "启用" if history2.is_active else "禁用"
                })
            
            # 比较标签
            if history1.tags != history2.tags:
                diff["changes"].append({
                    "field": "tags",
                    "label": "标签",
                    "value1": history1.tags,
                    "value2": history2.tags
                })
            
            # 比较工作流数据（结构差异）
            if history1.workflow_data != history2.workflow_data:
                diff["changes"].append({
                    "field": "workflow_data",
                    "label": "工作流配置",
                    "value1": history1.workflow_data,
                    "value2": history2.workflow_data,
                    "type": "complex"
                })
            
            return {"success": True, "data": diff}
        except Exception as e:
            logger.exception(f"Failed to compare versions: {e}")
            return {"success": False, "message": str(e)}

    @classmethod
    def get_workflow(cls, workflow_code: str, db: Session) -> Dict[str, Any]:
        """获取单个工作流"""
        try:
            workflow = db.query(Workflow).filter(Workflow.workflow_code == workflow_code).first()
            if not workflow:
                return {"success": False, "message": f"Workflow {workflow_code} not found"}
            
            logger.info(f"Loaded workflow {workflow_code} from database")
            workflow_dict = workflow.to_dict()
            
            # 清理旧的 outputs 字段，统一使用 outputParams
            workflow_data = workflow_dict.get("workflowData", {})
            nodes = workflow_data.get("nodes", [])
            for node in nodes:
                node_data = node.get("data", {})
                if "outputs" in node_data:
                    del node_data["outputs"]
            
            return {"success": True, "data": workflow_dict}
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
