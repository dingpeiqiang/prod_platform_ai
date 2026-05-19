from enum import Enum
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional, List
import logging

logger = logging.getLogger("data_source")


class DataSourceType(Enum):
    FILE = "file"
    DATABASE = "database"


class BaseDataSource(ABC):
    
    @abstractmethod
    def load_ontologies(self) -> Dict[str, Any]:
        pass
    
    @abstractmethod
    def load_scenes(self) -> List[Dict[str, Any]]:
        pass
    
    @abstractmethod
    def load_prompts(self) -> Dict[str, str]:
        pass
    
    @abstractmethod
    def load_recommendations(self) -> Dict[str, Any]:
        pass
    
    @abstractmethod
    def get_ontology(self, form_code: str) -> Optional[Dict[str, Any]]:
        pass
    
    @abstractmethod
    def get_scene(self, scene_code: str) -> Optional[Dict[str, Any]]:
        pass
    
    @abstractmethod
    def get_prompt(self, prompt_name: str) -> Optional[str]:
        pass
    
    @abstractmethod
    def get_recommendation(self, form_code: str, field_code: str) -> List[str]:
        pass


class DataSourceFactory:
    _registry = {}
    
    @classmethod
    def register(cls, source_type: DataSourceType, provider_class):
        cls._registry[source_type] = provider_class
    
    @classmethod
    def create(cls, source_type: DataSourceType, **kwargs) -> BaseDataSource:
        if source_type not in cls._registry:
            raise ValueError(f"Unknown data source type: {source_type}")
        return cls._registry[source_type](**kwargs)