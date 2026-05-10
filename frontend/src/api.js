import axios from "axios";

const http = axios.create({
  baseURL: "http://localhost:8080",
  timeout: 10000
});

export const api = {
  getConnections: () => http.get("/api/connections"),
  getConnection: (id) => http.get(`/api/connections/${id}`),
  createConnection: (payload) => http.post("/api/connections", payload),
  updateConnection: (id, payload) => http.put(`/api/connections/${id}`, payload),
  deleteConnection: (id) => http.delete(`/api/connections/${id}`),
  exportConnections: () => http.get("/api/connections/export"),
  importConnections: (formData) => http.post("/api/connections/import", formData),
  testConnection: (payload) => http.post("/api/connections/test", payload),
  getDatabaseTypes: () => http.get("/api/database-types"),
  listCatalogs: (payload) => http.post("/api/metadata/catalogs", payload),
  listTables: (payload) => http.post("/api/metadata/tables", payload),
  listColumns: (payload) => http.post("/api/metadata/columns", payload),
  parseSql: (payload) => http.post("/api/constraint/soft/parse", payload),
  parseHardConstraints: (payload) => http.post("/api/constraint/hard/parse", payload),
  getTables: () => http.get("/api/tables"),
  getColumns: (tableName) => http.get(`/api/tables/${tableName}/columns`),
  getConstraints: () => http.get("/api/constraints"),
  createConstraint: (payload) => http.post("/api/constraints", payload),
  parseConstraintSql: (payload) => http.post("/api/constraints/parse", payload),
  previewGenerate: (payload) => http.post("/api/generate/preview", payload),
  createGenerateTask: (payload) => http.post("/api/generate/tasks", payload),
  listGenerateTasks: (params) => http.get("/api/generate/tasks", { params }),
  deleteGenerateTask: (id) => http.delete(`/api/generate/tasks/${id}`),
  getGenerateTask: (id) => http.get(`/api/generate/tasks/${id}`),
  getGenerateTaskMetrics: (id) => http.get(`/api/generate/tasks/${id}/metrics`),
  startGenerateTask: (id) => http.post(`/api/generate/tasks/${id}/start`),
  retryGenerateTask: (id, body) => http.post(`/api/generate/tasks/${id}/retry`, body || {}),
  cancelGenerateTask: (id) => http.post(`/api/generate/tasks/${id}/cancel`),
  executeCompare: (payload) => http.post("/api/compare/execute", payload)
};
