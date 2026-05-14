import axios from 'axios';

const BASE_URL = '/api/visualization';

export const visualizationApi = {
  async getTraces(limit = 20) {
    const response = await axios.get(`${BASE_URL}/traces`, {
      params: { limit }
    });
    return response.data;
  },

  async getTraceDetail(traceId) {
    const response = await axios.get(`${BASE_URL}/traces/${traceId}`);
    return response.data;
  },

  async getFlowDiagram(traceId) {
    const response = await axios.get(`${BASE_URL}/traces/${traceId}/flow`);
    return response.data;
  },

  async getStats() {
    const response = await axios.get(`${BASE_URL}/stats`);
    return response.data;
  },

  async deleteTrace(traceId) {
    const response = await axios.delete(`${BASE_URL}/traces/${traceId}`);
    return response.data;
  },

  createWebSocket(traceId) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    return new WebSocket(`${protocol}//${host}${BASE_URL}/ws/${traceId}`);
  }
};
