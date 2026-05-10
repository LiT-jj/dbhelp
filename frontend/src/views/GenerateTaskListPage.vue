<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import GenerateTaskNewPage from "./GenerateTaskNewPage.vue";

const route = useRoute();
const router = useRouter();
const tasks = ref([]);
const metricsMap = ref({});
const activeTab = ref("list");
const total = ref(0);

const query = reactive({
  name: "",
  description: "",
  status: ""
});

const pagination = reactive({
  page: 1,
  pageSize: 10
});

const TASK_STATUSES = [
  { label: "全部", value: "" },
  { label: "待执行", value: "PENDING" },
  { label: "运行中", value: "RUNNING" },
  { label: "成功", value: "SUCCESS" },
  { label: "失败", value: "FAILED" },
  { label: "已取消", value: "CANCELLED" }
];

let pollTimer = null;

const syncTabFromRoute = () => {
  const t = route.query.tab;
  if (t === "new") activeTab.value = "new";
  else activeTab.value = "list";
};

watch(
  () => route.query.tab,
  () => syncTabFromRoute(),
  { immediate: true }
);

watch(activeTab, (name) => {
  const q = { ...route.query };
  if (name === "new") q.tab = "new";
  else {
    delete q.tab;
    delete q.clone;
  }
  router.replace({ path: route.path, query: q });
});

const cloneTask = (row) => {
  router.replace({
    path: route.path,
    query: { ...route.query, tab: "new", clone: String(row.id) }
  });
};

const load = async () => {
  const params = {
    page: pagination.page,
    pageSize: pagination.pageSize
  };
  if (query.name?.trim()) params.name = query.name.trim();
  if (query.description?.trim()) params.description = query.description.trim();
  if (query.status) params.status = query.status;

  const { data } = await api.listGenerateTasks(params);
  const records = Array.isArray(data?.records) ? data.records : [];
  total.value = Number(data?.total) ?? 0;

  const maxPage = Math.max(1, Math.ceil(total.value / pagination.pageSize) || 1);
  if (records.length === 0 && total.value > 0 && pagination.page > maxPage) {
    pagination.page = maxPage;
    await load();
    return;
  }

  tasks.value = records;

  const running = tasks.value.filter((t) => t.status === "RUNNING").map((t) => t.id);
  await Promise.all(
    running.map(async (id) => {
      try {
        const { data: m } = await api.getGenerateTaskMetrics(id);
        metricsMap.value[id] = m;
      } catch {
        metricsMap.value[id] = null;
      }
    })
  );
};

const search = () => {
  pagination.page = 1;
  load().catch((e) => ElMessage.error(e?.response?.data?.message || e.message || "加载失败"));
};

const resetQuery = () => {
  query.name = "";
  query.description = "";
  query.status = "";
  pagination.page = 1;
  load().catch((e) => ElMessage.error(e?.response?.data?.message || e.message || "加载失败"));
};

const onPageChange = (p) => {
  pagination.page = p;
  load().catch((e) => ElMessage.error(e?.response?.data?.message || e.message || "加载失败"));
};

const onPageSizeChange = (size) => {
  pagination.pageSize = size;
  pagination.page = 1;
  load().catch((e) => ElMessage.error(e?.response?.data?.message || e.message || "加载失败"));
};

const tpsText = (row) => {
  const m = metricsMap.value[row.id];
  if (!m || m.instantTps == null) return "—";
  return m.instantTps.toFixed(1);
};

const startTask = async (row) => {
  try {
    await ElMessageBox.confirm("确定开始执行该任务？", "开始执行", { type: "info" });
    await api.startGenerateTask(row.id);
    ElMessage.success("已启动");
    await load();
  } catch (e) {
    if (e !== "cancel") ElMessage.error(e?.response?.data?.message || e.message || "启动失败");
  }
};

const retry = async (row) => {
  try {
    await ElMessageBox.confirm("是否从断点继续重试？", "断点重试", { type: "warning" });
    await api.retryGenerateTask(row.id, { resumeFromCheckpoint: true });
    ElMessage.success("已提交断点重试");
    await load();
  } catch (e) {
    if (e !== "cancel") ElMessage.error(e?.response?.data?.message || e.message || "重试失败");
  }
};

const retryRestart = async (row) => {
  try {
    await ElMessageBox.confirm("将清空进度从头执行，确定？", "重头跑", { type: "warning" });
    await api.retryGenerateTask(row.id, { resumeFromCheckpoint: false });
    ElMessage.success("已重新排队");
    await load();
  } catch (e) {
    if (e !== "cancel") ElMessage.error(e?.response?.data?.message || e.message || "操作失败");
  }
};

const onRowMoreCommand = (cmd, row) => {
  switch (cmd) {
    case "start":
      return startTask(row);
    case "resume":
      return retry(row);
    case "restart":
      return retryRestart(row);
    case "cancel":
      return cancel(row);
    case "clone":
      return cloneTask(row);
    case "delete":
      return removeTask(row);
    default:
      return undefined;
  }
};

const cancel = async (row) => {
  try {
    await api.cancelGenerateTask(row.id);
    ElMessage.success("已请求取消");
    await load();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "取消失败");
  }
};

const removeTask = async (row) => {
  if (row.status === "RUNNING") {
    ElMessage.warning("运行中的任务请先取消后再删除");
    return;
  }
  try {
    await ElMessageBox.confirm(`确定删除任务「${row.name}」？此操作不可恢复。`, "删除任务", { type: "warning" });
    await api.deleteGenerateTask(row.id);
    ElMessage.success("已删除");
    await load();
  } catch (e) {
    if (e !== "cancel") ElMessage.error(e?.response?.data?.message || e.message || "删除失败");
  }
};

const detailDlg = reactive({
  visible: false,
  loading: false,
  row: null,
  payload: null
});

const safeJsonParse = (text) => {
  if (text == null || String(text).trim() === "") return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
};

const taskConfigParsed = computed(() => safeJsonParse(detailDlg.payload?.configJson));

const checkpointParsed = computed(() => safeJsonParse(detailDlg.payload?.checkpointJson));

const statusTagType = (status) => {
  const s = status || "";
  if (s === "SUCCESS") return "success";
  if (s === "FAILED") return "danger";
  if (s === "RUNNING") return "primary";
  if (s === "CANCELLED") return "info";
  if (s === "PENDING") return "warning";
  return "";
};

const formatTargetRef = (t) => {
  if (!t) return "—";
  return [t.catalog, t.schema, t.table].filter((x) => x != null && x !== "").join(".");
};

const formatRelateBrief = (expr) => {
  if (!expr) return "—";
  const t = String(expr).trim();
  if (t.startsWith("{")) {
    try {
      const o = JSON.parse(t);
      const op = o.op || o.operator || "?";
      const parts = [o.refCatalog, o.refSchema, o.refTable, o.refColumn].filter((x) => x != null && String(x).trim() !== "");
      return `${op} → ${parts.length ? parts.join(".") : "—"}`;
    } catch {
      return t.slice(0, 40);
    }
  }
  return t.length > 40 ? t.slice(0, 40) + "…" : t;
};

const constraintBrief = (c) => {
  if (!c) return "—";
  if (c.kind === "EQUAL") return `等值 ${c.equalValue ?? ""}`;
  if (c.kind === "RANGE") return `范围 ${c.rangeMin ?? ""} ~ ${c.rangeMax ?? ""}`;
  if (c.kind === "RELATE") return `关联 ${formatRelateBrief(c.relateExpr)}`;
  if (c.kind === "NOT_NULL") return "非空";
  return c.kind || "—";
};

/** 原始 config 展示时掩码内联密码 */
const maskedConfigJsonText = computed(() => {
  const raw = detailDlg.payload?.configJson;
  if (raw == null || String(raw).trim() === "") return "—";
  try {
    const o = JSON.parse(raw);
    if (o?.inlineConnection && typeof o.inlineConnection === "object" && "password" in o.inlineConnection) {
      o.inlineConnection = { ...o.inlineConnection, password: "***" };
    }
    return JSON.stringify(o, null, 2);
  } catch {
    return String(raw);
  }
});

const showDetail = async (row) => {
  detailDlg.row = row;
  detailDlg.visible = true;
  detailDlg.loading = true;
  detailDlg.payload = null;
  try {
    const { data } = await api.getGenerateTask(row.id);
    detailDlg.payload = data;
  } catch (e) {
    ElMessage.error("加载详情失败");
    detailDlg.visible = false;
  } finally {
    detailDlg.loading = false;
  }
};

const closeDetail = () => {
  detailDlg.visible = false;
  detailDlg.payload = null;
  detailDlg.row = null;
};

const onTaskCreated = () => {
  activeTab.value = "list";
  pagination.page = 1;
  load().catch((e) => ElMessage.error(e?.response?.data?.message || e.message || "加载失败"));
};

const pollLoad = () => {
  if (activeTab.value !== "list") return;
  load().catch(() => {});
};

onMounted(async () => {
  await load().catch((e) => ElMessage.error(e?.response?.data?.message || e.message || "加载失败"));
  pollTimer = setInterval(pollLoad, 2500);
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<template>
  <div class="page">
    <el-page-header @back="router.push('/')" content="造数任务管理" />

    <el-tabs v-model="activeTab" class="mt tabs-wrap">
      <el-tab-pane label="任务列表" name="list">
        <el-card>
          <div class="toolbar">
            <el-input v-model="query.name" placeholder="任务名称" clearable class="filter-input" @keyup.enter="search" />
            <el-input
              v-model="query.description"
              placeholder="任务描述"
              clearable
              class="filter-input-wide"
              @keyup.enter="search"
            />
            <el-select v-model="query.status" placeholder="状态" clearable class="filter-select">
              <el-option v-for="s in TASK_STATUSES" :key="s.value || 'all'" :label="s.label" :value="s.value" />
            </el-select>
            <el-button type="primary" @click="search">检索</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </div>

          <el-table :data="tasks" stripe class="table-block">
            <el-table-column prop="name" label="任务名称" min-width="160" />
            <el-table-column prop="description" label="任务描述" min-width="220" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="进度" width="120">
              <template #default="{ row }">
                <el-progress :percentage="row.progressPercent || 0" :stroke-width="10" />
              </template>
            </el-table-column>
            <el-table-column label="行数" width="140">
              <template #default="{ row }">
                {{ row.processedRows }} / {{ row.targetRows }}
              </template>
            </el-table-column>
            <el-table-column label="TPS" width="90">
              <template #default="{ row }">{{ tpsText(row) }}</template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="170" />
            <el-table-column label="操作" width="168" fixed="right" align="left">
              <template #default="{ row }">
                <div class="table-row-ops">
                  <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
                  <el-dropdown
                    trigger="click"
                    placement="bottom-end"
                    :teleported="true"
                    @command="(cmd) => onRowMoreCommand(cmd, row)"
                  >
                    <el-button link type="primary" size="small" class="table-row-ops-more">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="row.status === 'PENDING'" command="start">开始执行</el-dropdown-item>
                        <el-dropdown-item v-if="row.status === 'FAILED' || row.status === 'CANCELLED'" command="resume">
                          断点重试
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="row.status === 'FAILED' || row.status === 'CANCELLED' || row.status === 'SUCCESS'"
                          command="restart"
                        >
                          重头跑
                        </el-dropdown-item>
                        <el-dropdown-item v-if="row.status === 'RUNNING'" command="cancel">取消执行</el-dropdown-item>
                        <el-dropdown-item
                          command="clone"
                          :divided="
                            row.status === 'PENDING' ||
                            row.status === 'FAILED' ||
                            row.status === 'CANCELLED' ||
                            row.status === 'SUCCESS' ||
                            row.status === 'RUNNING'
                          "
                        >
                          克隆任务
                        </el-dropdown-item>
                        <el-dropdown-item v-if="row.status !== 'RUNNING'" command="delete" divided>删除任务</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              :current-page="pagination.page"
              :page-size="pagination.pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="onPageChange"
              @size-change="onPageSizeChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="新建造数任务" name="new">
        <GenerateTaskNewPage embedded @task-created="onTaskCreated" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="detailDlg.visible"
      :title="detailDlg.payload ? `任务详情 · ${detailDlg.payload.name || ''}` : '任务详情'"
      width="820px"
      class="task-detail-dialog"
      destroy-on-close
      @closed="detailDlg.payload = null"
    >
      <div v-loading="detailDlg.loading" class="detail-body">
        <template v-if="detailDlg.payload && !detailDlg.loading">
          <h4 class="detail-section-title">概览</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="任务 ID">{{ detailDlg.payload.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(detailDlg.payload.status)" size="small">{{ detailDlg.payload.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="名称" :span="2">{{ detailDlg.payload.name }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              <span class="detail-desc">{{ detailDlg.payload.description || "—" }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="进度">{{ detailDlg.payload.progressPercent ?? 0 }}%</el-descriptions-item>
            <el-descriptions-item label="已处理 / 目标行">
              {{ detailDlg.payload.processedRows ?? 0 }} / {{ detailDlg.payload.targetRows ?? "—" }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detailDlg.payload.createdAt || "—" }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ detailDlg.payload.updatedAt || "—" }}</el-descriptions-item>
          </el-descriptions>

          <template v-if="detailDlg.payload.errorMessage">
            <h4 class="detail-section-title">错误信息</h4>
            <el-alert type="error" :closable="false" show-icon>
              <pre class="detail-pre">{{ detailDlg.payload.errorMessage }}</pre>
            </el-alert>
          </template>

          <template v-if="detailDlg.payload.warningEntries?.length">
            <h4 class="detail-section-title">批次告警</h4>
            <p class="detail-muted">写入等环节出现非致命问题时记录，任务可能仍会继续或已结束。</p>
            <el-table :data="detailDlg.payload.warningEntries" size="small" stripe max-height="220">
              <el-table-column prop="at" label="时间" width="168" />
              <el-table-column label="位置" min-width="140" show-overflow-tooltip>
                <template #default="{ row: w }">{{ formatTargetRef({ catalog: w.catalog, schema: w.schema, table: w.table }) }}</template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
            </el-table>
          </template>

          <template v-if="taskConfigParsed">
            <h4 class="detail-section-title">连接与造数配置</h4>
            <el-descriptions :column="2" border size="small" class="detail-block">
              <el-descriptions-item label="连接模式">{{ taskConfigParsed.connectionMode || "—" }}</el-descriptions-item>
              <el-descriptions-item label="已保存连接 ID">
                {{ taskConfigParsed.connectionId != null ? taskConfigParsed.connectionId : "—" }}
              </el-descriptions-item>
              <template v-if="taskConfigParsed.inlineConnection">
                <el-descriptions-item label="库类型">{{ taskConfigParsed.inlineConnection.dbType || "—" }}</el-descriptions-item>
                <el-descriptions-item label="地址">
                  {{ taskConfigParsed.inlineConnection.host }}:{{ taskConfigParsed.inlineConnection.port }}
                </el-descriptions-item>
                <el-descriptions-item label="库名">{{ taskConfigParsed.inlineConnection.databaseName || "—" }}</el-descriptions-item>
                <el-descriptions-item label="用户">{{ taskConfigParsed.inlineConnection.username || "—" }}</el-descriptions-item>
              </template>
              <template v-if="taskConfigParsed.options">
                <el-descriptions-item label="目标行数">{{ taskConfigParsed.options.rowCount ?? "—" }}</el-descriptions-item>
                <el-descriptions-item label="并发">{{ taskConfigParsed.options.concurrency ?? "—" }}</el-descriptions-item>
                <el-descriptions-item label="批大小">{{ taskConfigParsed.options.batchSize ?? "—" }}</el-descriptions-item>
                <el-descriptions-item label="写入方式">{{ taskConfigParsed.options.sinkType || "—" }}</el-descriptions-item>
                <el-descriptions-item label="传输">{{ taskConfigParsed.options.transport || "—" }}</el-descriptions-item>
              </template>
            </el-descriptions>

            <h4 class="detail-section-title">目标表</h4>
            <el-table
              v-if="taskConfigParsed.targets?.length"
              :data="taskConfigParsed.targets"
              size="small"
              stripe
              max-height="200"
            >
              <el-table-column prop="catalog" label="catalog" width="120" />
              <el-table-column prop="schema" label="schema" width="100" />
              <el-table-column prop="table" label="表" min-width="140" />
            </el-table>
            <el-empty v-else description="无目标表记录" :image-size="64" />

            <h4 class="detail-section-title">约束摘要</h4>
            <div class="detail-tags">
              <el-tag type="info" size="small">刚性约束 {{ taskConfigParsed.hardConstraints?.length ?? 0 }} 条</el-tag>
              <el-tag type="info" size="small">软约束 {{ taskConfigParsed.softConstraints?.length ?? 0 }} 条</el-tag>
            </div>
            <el-collapse v-if="taskConfigParsed.hardConstraints?.length || taskConfigParsed.softConstraints?.length">
              <el-collapse-item v-if="taskConfigParsed.hardConstraints?.length" title="刚性约束列表" name="hard">
                <el-table :data="taskConfigParsed.hardConstraints" size="small" max-height="200">
                  <el-table-column prop="kind" label="类型" width="88" />
                  <el-table-column label="表.列" min-width="160" show-overflow-tooltip>
                    <template #default="{ row: c }">{{ formatTargetRef(c) }} · {{ c.column || "—" }}</template>
                  </el-table-column>
                  <el-table-column label="内容" min-width="160" show-overflow-tooltip>
                    <template #default="{ row: c }">{{ constraintBrief(c) }}</template>
                  </el-table-column>
                </el-table>
              </el-collapse-item>
              <el-collapse-item v-if="taskConfigParsed.softConstraints?.length" title="软约束列表" name="soft">
                <el-table :data="taskConfigParsed.softConstraints" size="small" max-height="200">
                  <el-table-column prop="kind" label="类型" width="88" />
                  <el-table-column label="表.列" min-width="160" show-overflow-tooltip>
                    <template #default="{ row: c }">{{ formatTargetRef(c) }} · {{ c.column || "—" }}</template>
                  </el-table-column>
                  <el-table-column label="内容" min-width="160" show-overflow-tooltip>
                    <template #default="{ row: c }">{{ constraintBrief(c) }}</template>
                  </el-table-column>
                </el-table>
              </el-collapse-item>
            </el-collapse>
          </template>
          <el-alert v-else type="warning" :closable="false" show-icon title="任务配置 JSON 无法解析或为空" class="detail-block" />

          <template v-if="detailDlg.payload.checkpointJson?.trim()">
            <h4 class="detail-section-title">检查点</h4>
            <pre v-if="checkpointParsed != null" class="detail-pre detail-pre--sm">{{ JSON.stringify(checkpointParsed, null, 2) }}</pre>
            <pre v-else class="detail-pre detail-pre--sm">{{ detailDlg.payload.checkpointJson }}</pre>
          </template>

          <el-collapse class="detail-raw">
            <el-collapse-item title="原始 JSON（调试用）" name="raw">
              <el-tabs type="border-card">
                <el-tab-pane label="configJson" lazy>
                  <p class="detail-muted">内联连接密码已替换为 ***</p>
                  <pre class="detail-pre">{{ maskedConfigJsonText }}</pre>
                </el-tab-pane>
                <el-tab-pane label="checkpointJson" lazy>
                  <pre class="detail-pre">{{ detailDlg.payload.checkpointJson || "—" }}</pre>
                </el-tab-pane>
                <el-tab-pane label="warningJson" lazy>
                  <pre class="detail-pre">{{ detailDlg.payload.warningJson || "—" }}</pre>
                </el-tab-pane>
              </el-tabs>
            </el-collapse-item>
          </el-collapse>
        </template>
      </div>
      <template #footer>
        <el-button type="primary" @click="closeDetail">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 8px;
}
.mt {
  margin-top: 16px;
}
.tabs-wrap :deep(.el-tab-pane) {
  padding-top: 4px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.filter-input {
  width: 160px;
}
.filter-input-wide {
  width: 200px;
}
.filter-select {
  width: 120px;
}
.table-block {
  width: 100%;
}
.table-row-ops {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.table-row-ops-more {
  margin-left: 2px;
}
.table-row-ops :deep(.el-button.is-link) {
  vertical-align: middle;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.task-detail-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}
.detail-body {
  max-height: min(70vh, 640px);
  overflow-y: auto;
  padding-right: 4px;
}
.detail-section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.detail-section-title:first-of-type {
  margin-top: 0;
}
.detail-muted {
  margin: 0 0 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.detail-desc {
  white-space: pre-wrap;
  word-break: break-word;
}
.detail-pre {
  margin: 0;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.45;
  background: #f5f7fa;
  border-radius: 8px;
  overflow-x: auto;
  max-height: 280px;
  white-space: pre-wrap;
  word-break: break-word;
}
.detail-pre--sm {
  max-height: 160px;
}
.detail-block {
  margin-bottom: 4px;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.detail-raw {
  margin-top: 16px;
}
</style>
