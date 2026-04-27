from typing import Dict, List, Any
from fastapi import WebSocket
import json


class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, List[WebSocket]] = {}
        self.form_states: Dict[str, Dict[str, Any]] = {}
    
    async def connect(self, websocket: WebSocket, form_id: str):
        await websocket.accept()
        if form_id not in self.active_connections:
            self.active_connections[form_id] = []
        self.active_connections[form_id].append(websocket)
    
    def disconnect(self, websocket: WebSocket, form_id: str):
        if form_id in self.active_connections:
            self.active_connections[form_id].remove(websocket)
            if not self.active_connections[form_id]:
                del self.active_connections[form_id]
    
    async def send_personal_message(self, message: Dict[str, Any], websocket: WebSocket):
        await websocket.send_json(message)
    
    async def broadcast_to_form(self, message: Dict[str, Any], form_id: str, exclude: WebSocket = None):
        if form_id in self.active_connections:
            for connection in self.active_connections[form_id]:
                if connection != exclude:
                    await connection.send_json(message)
    
    def get_form_state(self, form_id: str) -> Dict[str, Any]:
        return self.form_states.get(form_id, {})
    
    def set_form_state(self, form_id: str, state: Dict[str, Any]):
        self.form_states[form_id] = state
    
    def increment_version(self, form_id: str) -> int:
        if form_id not in self.form_states:
            self.form_states[form_id] = {"version": 1}
        else:
            self.form_states[form_id]["version"] = self.form_states[form_id].get("version", 1) + 1
        return self.form_states[form_id]["version"]
    
    def get_version(self, form_id: str) -> int:
        return self.form_states.get(form_id, {}).get("version", 1)


manager = ConnectionManager()
