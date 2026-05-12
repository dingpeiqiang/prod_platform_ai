from app.core.database import SessionLocal
from app.models.form import FormInstance

db = SessionLocal()

# 检查前10条记录的 form_id 和 form_code
records = db.query(FormInstance).limit(10).all()

print('前10条历史记录:')
for record in records:
    print(f'  id={record.id}, form_id={record.form_id}, form_code={record.form_code}, status={record.status}')

db.close()
