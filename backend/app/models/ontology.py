from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text
from sqlalchemy.sql import func
from app.core.database import Base


class Ontology(Base):
    __tablename__ = "ontologies"

    id = Column(Integer, primary_key=True, index=True)
    ontology_code = Column(String(100), unique=True, index=True, nullable=False)
    ontology_name = Column(String(200), nullable=False)
    entities = Column(JSON, nullable=False)
    form_code = Column(String(100))
    form_name = Column(String(200))
    description = Column(Text)
    version = Column(Integer, default=1)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    def to_dict(self):
        return {
            "formCode": self.form_code or self.ontology_code,
            "formName": self.form_name or self.ontology_name,
            "description": self.description,
            "entities": self.entities,
            "version": self.version,
            "isActive": self.is_active
        }

    def to_ontology_format(self):
        return {
            "formCode": self.form_code or self.ontology_code,
            "formName": self.form_name or self.ontology_name,
            "description": self.description,
            "entities": self.entities
        }