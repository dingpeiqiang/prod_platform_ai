from sqlalchemy import Column, Integer, String, JSON, Boolean, DateTime, Text
from sqlalchemy.sql import func
from app.core.database import Base


class Ontology(Base):
    __tablename__ = "ontologies"

    id = Column(Integer, primary_key=True, index=True)
    ontology_code = Column(String(100), unique=True, index=True, nullable=False)
    ontology_name = Column(String(200), nullable=False)
    category = Column(String(100), default="general")
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
            "ontologyCode": self.ontology_code,
            "ontologyName": self.ontology_name,
            "category": self.category,
            "description": self.description,
            "entities": self.entities,
            "formCode": self.form_code,
            "formName": self.form_name,
            "version": self.version,
            "isActive": self.is_active,
            "createdAt": self.created_at.isoformat() if self.created_at else None,
            "updatedAt": self.updated_at.isoformat() if self.updated_at else None
        }

    def to_ontology_format(self):
        return {
            "formCode": self.form_code or self.ontology_code,
            "formName": self.form_name or self.ontology_name,
            "description": self.description,
            "entities": self.entities
        }