import { request } from './httpClient.js';

const BASE = '/visualization';

export const visualizationApi = {
  async getTraces(limit = 20) {
    return request(`${BASE}/traces`, { params: { limit } });
  },

  async getTraceDetail(traceId) {
    return request(`${BASE}/traces/${traceId}`);
  },

  async getFlowDiagram(traceId) {
    return request(`${BASE}/traces/${traceId}/flow`);
  },

  async getStats() {
    return request(`${BASE}/stats`);
  },

  async deleteTrace(traceId) {
    return request(`${BASE}/traces/${traceId}`, { method: 'DELETE' });
  },

  createWebSocket(traceId) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    return new WebSocket(`${protocol}//${host}/api/visualization/ws/${traceId}`);
  }
};
