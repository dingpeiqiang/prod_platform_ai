"""
初始化提示词数据到数据库
将场景提示词文件导入到提示词管理系统中
"""
import json
import sys
from pathlib import Path

# 添加项目根目录到路径
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.core.database import SessionLocal, engine, Base
from app.models.prompt import Prompt
from sqlalchemy.orm import Session


def init_prompts():
    """初始化提示词数据"""
    print("开始初始化提示词数据...")
    
    db = SessionLocal()
    
    try:
        # 场景提示词目录
        prompts_dir = Path(__file__).parent.parent / 'config' / 'prompts' / 'scenes'
        
        if not prompts_dir.exists():
            print(f"提示词目录不存在: {prompts_dir}")
            return
        
        # 遍历所有 .txt 文件
        imported_count = 0
        for prompt_file in prompts_dir.glob('*.txt'):
            if prompt_file.name.startswith('_'):  # 跳过模板文件
                continue
                
            code = prompt_file.stem
            name = code.replace('_', ' ').title()
            
            # 检查是否已存在
            existing = db.query(Prompt).filter(Prompt.code == code).first()
            if existing:
                print(f"提示词已存在，跳过: {code}")
                continue
            
            # 读取文件内容
            with open(prompt_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 根据文件名确定分类
            category = 'form'  # 默认是表单类
            if 'qa' in code.lower():
                category = 'qa'
            elif 'tool' in code.lower():
                category = 'tool'
            elif 'chat' in code.lower():
                category = 'chat'
            
            # 创建提示词
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
            print(f"导入提示词: {code}")
        
        db.commit()
        print(f"\n完成！共导入 {imported_count} 个提示词")
        
    except Exception as e:
        db.rollback()
        print(f"初始化失败: {e}")
        import traceback
        traceback.print_exc()
    finally:
        db.close()


if __name__ == "__main__":
    init_prompts()
