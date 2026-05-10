<script setup>
import { ref, reactive, computed, onMounted, watch } from "vue";
import { ElMessage, ElNotification } from "element-plus";
import { useRouter, useRoute } from "vue-router";
import { api } from "../api";

const props = defineProps({
  /** 嵌入「造数任务」页签时不显示顶栏返回 */
  embedded: { type: Boolean, default: false }
});

const emit = defineEmits(["taskCreated"]);

const router = useRouter();
const route = useRoute();

const taskName = ref("多表造数任务");
const connectionMode = ref("SAVED");
const connections = ref([]);
const connectionId = ref(null);
const databaseTypes = ref([]);
const connStatus = ref("idle");
const connMessage = ref("");

const inline = reactive({
  dbType: "MYSQL",
  host: "127.0.0.1",
  port: 3306,
  databaseName: "mysql",
  username: "root",
  password: "",
  driverClass: "",
  urlTemplate: ""
});

const catalogList = ref([]);
const selectedCatalogs = ref([]);
const tableRows = ref([]);
const selectedTableRows = ref([]);

const hardConstraints = ref([]);
const softConstraints = ref([]);

/** 从 SQL 解析软约束（弹框） */
const softParseDlg = reactive({
  visible: false,
  sql: "",
  catalog: "",
  schema: "",
  parseErrors: [],
  submitting: false
});

/** 统一字段行：列元数据 + 刚性（含库表推导并入 hardConstraints）与软约束概要 */
const fieldRows = ref([]);
const fieldGridLoading = ref(false);

const fieldSearch = reactive({
  catalog: "",
  schema: "",
  table: "",
  column: "",
  columnType: "",
  hasRigid: "",
  hasSoft: ""
});

const options = reactive({
  rowCount: 5000,
  concurrency: 2,
  batchSize: 500,
  sinkType: "JDBC",
  transport: "direct"
});

const dlg = reactive({
  visible: false,
  mode: "hard",
  kind: "NOT_NULL",
  catalog: "",
  schema: "",
  table: "",
  column: "",
  equalValue: "",
  rangeMin: "",
  rangeMax: "",
  relateExpr: "",
  /** 关联 RELATE：运算符 + 关联侧表字段（与 relateExpr JSON 同步） */
  relateOperator: "=",
  relateRefCatalog: "",
  relateRefSchema: "",
  relateRefTable: "",
  relateRefColumn: "",
  editingId: null
});

/** 关联约束运算符（暂仅支持三种，其余不可选） */
const RELATE_OPERATORS = ["=", ">", "<"];

const normalizeRelateOperator = (op) => {
  const s = (op == null ? "" : String(op)).trim();
  return RELATE_OPERATORS.includes(s) ? s : "=";
};

const resetRelateFormFields = () => {
  dlg.relateOperator = "=";
  dlg.relateRefCatalog = "";
  dlg.relateRefSchema = "";
  dlg.relateRefTable = "";
  dlg.relateRefColumn = "";
};

const parseRelateExprToForm = (expr) => {
  resetRelateFormFields();
  if (!expr || !String(expr).trim()) return;
  const t = String(expr).trim();
  if (t.startsWith("{")) {
    try {
      const o = JSON.parse(t);
      dlg.relateOperator = normalizeRelateOperator(o.op || o.operator || "=");
      dlg.relateRefCatalog = o.refCatalog != null ? String(o.refCatalog) : "";
      dlg.relateRefSchema = o.refSchema != null ? String(o.refSchema) : "";
      dlg.relateRefTable = o.refTable != null ? String(o.refTable) : "";
      dlg.relateRefColumn = o.refColumn != null ? String(o.refColumn) : "";
      return;
    } catch {
      /* 非 JSON 则按旧格式解析 */
    }
  }
  const m = t.match(/^(\S+)\s+(.+)$/);
  if (!m) return;
  dlg.relateOperator = normalizeRelateOperator(m[1].trim());
  const rhs = m[2].trim().replace(/^`|`$/g, "");
  const parts = rhs.split(".").map((p) => p.trim()).filter(Boolean);
  if (parts.length === 2) {
    dlg.relateRefTable = parts[0];
    dlg.relateRefColumn = parts[1];
  } else if (parts.length === 3) {
    dlg.relateRefSchema = parts[0];
    dlg.relateRefTable = parts[1];
    dlg.relateRefColumn = parts[2];
  } else if (parts.length === 4) {
    dlg.relateRefCatalog = parts[0];
    dlg.relateRefSchema = parts[1];
    dlg.relateRefTable = parts[2];
    dlg.relateRefColumn = parts[3];
  }
};

const buildRelateExprFromForm = () => {
  const o = {
    op: normalizeRelateOperator(dlg.relateOperator),
    refCatalog: (dlg.relateRefCatalog || "").trim() || null,
    refSchema: (dlg.relateRefSchema || "").trim() || null,
    refTable: (dlg.relateRefTable || "").trim() || null,
    refColumn: (dlg.relateRefColumn || "").trim() || null
  };
  return JSON.stringify(o);
};

const formatRelateExprShort = (expr) => {
  if (!expr) return "—";
  const t = String(expr).trim();
  if (t.startsWith("{")) {
    try {
      const o = JSON.parse(t);
      const op = o.op || o.operator || "?";
      const parts = [o.refCatalog, o.refSchema, o.refTable, o.refColumn].filter((x) => x != null && String(x).trim() !== "");
      const tail = parts.length ? parts.join(".") : "—";
      return `${op} → ${tail}`;
    } catch {
      return t.slice(0, 80);
    }
  }
  return t.length > 48 ? t.slice(0, 48) + "…" : t;
};

const drawer = reactive({
  visible: false,
  row: null
});

const connectionPayload = () => {
  if (connectionMode.value === "SAVED") {
    return { connectionId: connectionId.value };
  }
  return {
    dbType: inline.dbType,
    host: inline.host,
    port: inline.port,
    databaseName: inline.databaseName,
    username: inline.username,
    password: inline.password,
    driverClass: inline.driverClass,
    urlTemplate: inline.urlTemplate
  };
};

const connectionCardClass = computed(() => {
  if (connStatus.value === "ok") return "conn-ok";
  if (connStatus.value === "fail") return "conn-fail";
  return "";
});

const selectedTargets = computed(() =>
  selectedTableRows.value.map((r) => ({
    catalog: r.catalog,
    schema: r.schema || null,
    table: r.name
  }))
);

/** 下拉里「选中态 + 候选项」统一：id - 连接名 - host:port - username */
const formatConnectionSelectLabel = (c) => {
  if (!c) return "";
  const id = c.id != null ? String(c.id) : "—";
  const name = c.name ?? "—";
  const hp = `${c.host ?? "—"}:${c.port ?? "—"}`;
  const user = c.username ?? "—";
  return `${id} - ${name} - ${hp} - ${user}`;
};

const applyDbTypeTemplate = () => {
  const t = databaseTypes.value.find((x) => x.code === inline.dbType);
  if (t) {
    inline.driverClass = t.driverClass;
    inline.urlTemplate = t.urlTemplate;
    if (!inline.port || inline.port === 3306) {
      inline.port = t.defaultPort || inline.port;
    }
  }
};

const testConnection = async () => {
  connStatus.value = "idle";
  connMessage.value = "";
  try {
    const payload = connectionPayload();
    const { data } = await api.testConnection(payload);
    if (data.success) {
      connStatus.value = "ok";
      connMessage.value = data.message || "连接成功";
      ElMessage.success(connMessage.value);
      await loadCatalogs();
    } else {
      connStatus.value = "fail";
      connMessage.value = data.message || "连接失败";
      ElMessage({ message: connMessage.value, type: "warning", duration: 5000 });
    }
  } catch (e) {
    connStatus.value = "fail";
    connMessage.value = e?.response?.data?.message || e.message || "请求失败";
    ElMessage({ message: connMessage.value, type: "warning", duration: 5000 });
  }
};

const loadCatalogs = async () => {
  try {
    const { data } = await api.listCatalogs(connectionPayload());
    catalogList.value = Array.isArray(data) ? data.map((c) => c.name) : [];
  } catch (e) {
    catalogList.value = [];
    ElMessage.error("加载库列表失败");
  }
};

const loadTables = async () => {
  if (!selectedCatalogs.value.length) {
    ElMessage.warning("请先选择库");
    return;
  }
  try {
    const body = { ...connectionPayload(), catalogs: [...selectedCatalogs.value] };
    const { data } = await api.listTables(body);
    tableRows.value = Array.isArray(data) ? data : [];
  } catch (e) {
    tableRows.value = [];
    ElMessage.error("加载表失败");
  }
};

const uid = () => (crypto.randomUUID ? crypto.randomUUID() : `id_${Date.now()}_${Math.random()}`);

/** 标识空：null / undefined / "" 视为等价（与 JDBC 元数据里 schema 常为空一致） */
const emptyIdent = (v) => v === null || v === undefined || v === "";

const sameIdent = (a, b) => {
  if (emptyIdent(a) && emptyIdent(b)) return true;
  return String(a ?? "").trim().toLowerCase() === String(b ?? "").trim().toLowerCase();
};

const tableRowMatchesTarget = (r, t) =>
  sameIdent(r.catalog, t.catalog) && sameIdent(r.schema, t.schema) && sameIdent(r.name, t.table);

const parseConfigJson = (raw) => {
  if (raw == null) return null;
  if (typeof raw === "object") return raw;
  const s = String(raw).trim();
  if (!s) return null;
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
};

const stripCloneQuery = async () => {
  if (route.query.clone == null || String(route.query.clone).trim() === "") return;
  const q = { ...route.query };
  delete q.clone;
  await router.replace({ path: route.path, query: q });
};

const applyOptionsFromCfg = (opt) => {
  if (!opt || typeof opt !== "object") return;
  for (const k of Object.keys(options)) {
    if (Object.prototype.hasOwnProperty.call(opt, k) && opt[k] !== undefined && opt[k] !== null) {
      options[k] = opt[k];
    }
  }
};

const cloneConstraintsWithNewIds = (arr) => {
  if (!Array.isArray(arr)) return [];
  return arr.map((c) => {
    let o;
    try {
      o = structuredClone(c);
    } catch {
      o = JSON.parse(JSON.stringify(c));
    }
    o.id = uid();
    return o;
  });
};

const applyCloneFromTaskId = async (taskId) => {
  try {
    const { data } = await api.getGenerateTask(taskId);
    const cfg = parseConfigJson(data.configJson);
    if (!cfg) {
      ElMessage.error("无法读取任务配置，克隆失败");
      return;
    }
    const baseName = (data.name || "造数任务").trim() || "造数任务";
    taskName.value = `${baseName} 副本`.slice(0, 200);
    const mode = String(cfg.connectionMode || "SAVED").toUpperCase();
    connectionMode.value = mode === "INLINE" ? "INLINE" : "SAVED";
    if (connectionMode.value === "SAVED") {
      if (cfg.connectionId != null) connectionId.value = cfg.connectionId;
      const exists = connections.value.some((c) => c.id === connectionId.value);
      if (!exists) {
        ElMessage.warning("当前未找到被克隆任务使用的已保存连接，请在创建页重新选择连接");
      }
    } else {
      const ic = cfg.inlineConnection;
      if (ic && typeof ic === "object") {
        if (ic.dbType) inline.dbType = ic.dbType;
        if (ic.host != null) inline.host = ic.host;
        if (ic.port != null) inline.port = ic.port;
        if (ic.databaseName != null) inline.databaseName = ic.databaseName;
        if (ic.username != null) inline.username = ic.username;
        if (ic.password != null) inline.password = ic.password;
        if (ic.driverClass) inline.driverClass = ic.driverClass;
        if (ic.urlTemplate) inline.urlTemplate = ic.urlTemplate;
      }
      applyDbTypeTemplate();
      if (!String(inline.password ?? "").trim()) {
        ElMessage({ message: "内联连接未包含密码或密码为空，请填写后测试连接", type: "warning", duration: 5000 });
      }
    }
    applyOptionsFromCfg(cfg.options);
    hardConstraints.value = cloneConstraintsWithNewIds(cfg.hardConstraints);
    softConstraints.value = cloneConstraintsWithNewIds(cfg.softConstraints);
    const targets = Array.isArray(cfg.targets) ? cfg.targets : [];
    const cats = [...new Set(targets.map((t) => t.catalog).filter((c) => c != null && String(c).trim() !== ""))];
    selectedCatalogs.value = cats;
    if (!selectedCatalogs.value.length && targets.length) {
      ElMessage.warning("目标任务未包含库名(catalog)，无法自动加载表列表，请手动选择库并加载表");
    }
    await loadCatalogs();
    if (selectedCatalogs.value.length) {
      await loadTables();
      const matched = [];
      for (const t of targets) {
        const row = tableRows.value.find((r) => tableRowMatchesTarget(r, t));
        if (row) matched.push(row);
      }
      selectedTableRows.value = matched;
      if (targets.length && matched.length < targets.length) {
        ElMessage.warning("部分目标表未在列表中匹配到，请检查连接或手动勾选目标表");
      }
    } else {
      tableRows.value = [];
      selectedTableRows.value = [];
    }
    connStatus.value = "idle";
    connMessage.value = "";
    ElMessage.success("已载入克隆配置，请测试连接成功后提交新任务");
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "克隆失败");
  }
};

/**
 * 软约束行是否与字段网格一行对应（兼容 MySQL：解析器 SCHEMA 角色把库名放在 schema，元数据里库名在 catalog、schema 为空）。
 */
const softConstraintMatchesField = (r, catalog, schema, table, column) => {
  if (!sameIdent(r.table, table) || !sameIdent(r.column, column)) return false;
  if (sameIdent(r.catalog, catalog) && sameIdent(r.schema, schema)) return true;
  if (emptyIdent(r.catalog) && sameIdent(r.schema, catalog) && emptyIdent(schema)) return true;
  if (sameIdent(r.catalog, catalog) && emptyIdent(r.schema) && emptyIdent(schema)) return true;
  return false;
};

const summarizeManualList = (list, catalog, schema, table, column, looseMatch) => {
  const rows = looseMatch
    ? list.filter((r) => softConstraintMatchesField(r, catalog, schema, table, column))
    : list.filter(
        (r) =>
          (r.catalog || "") === (catalog || "") &&
          (r.schema || "") === (schema || "") &&
          (r.table || "") === (table || "") &&
          (r.column || "") === (column || "")
      );
  if (!rows.length) return { text: "—", count: 0 };
  const parts = rows.map((r) => {
    if (r.kind === "RANGE") return `范围(${r.rangeMin}~${r.rangeMax})`;
    if (r.kind === "EQUAL") return `等值(${r.equalValue ?? ""})`;
    if (r.kind === "RELATE") return `关联(${formatRelateExprShort(r.relateExpr)})`;
    return r.kind || "—";
  });
  return { text: parts.join("；"), count: rows.length };
};

const buildRigidColumnMap = (parseRes) => {
  const map = new Map();
  const cols = parseRes?.columns || [];
  for (const col of cols) {
    const name = col.name;
    if (!name) continue;
    map.set(name, col.constraints || []);
  }
  return map;
};

/** 库表推导的刚性写入 hardConstraints，刷新时按表替换带此标记的条目 */
const SCHEMA_RIGID_SOURCE = "SCHEMA_RIGID";

const sameColHard = (h, catalog, schema, table, column) =>
  (h.catalog || "") === (catalog || "") &&
  (h.schema || "") === (schema || "") &&
  (h.table || "") === (table || "") &&
  (h.column || "") === (column || "");

const hardConstraintSignature = (h) => {
  const k = (h.kind || "").toUpperCase();
  return `${k}|${h.equalValue ?? ""}|${h.rangeMin ?? ""}|${h.rangeMax ?? ""}|${h.relateExpr ?? ""}`;
};

const rigidDtoToHardRow = (catalog, schema, table, column, c) => {
  if (!c || !c.kind) return null;
  const kind = String(c.kind).toUpperCase();
  const schemaStored =
    schema === undefined || schema === null || schema === "" ? null : String(schema).trim() || null;
  const base = {
    id: uid(),
    catalog,
    schema: schemaStored,
    table,
    column,
    source: SCHEMA_RIGID_SOURCE
  };
  if (kind === "NOT_NULL") {
    return { ...base, kind: "NOT_NULL", equalValue: null, rangeMin: null, rangeMax: null, relateExpr: null };
  }
  if (kind === "RANGE") {
    return {
      ...base,
      kind: "RANGE",
      rangeMin: c.min != null ? String(c.min) : null,
      rangeMax: c.max != null ? String(c.max) : null,
      equalValue: null,
      relateExpr: null
    };
  }
  if (kind === "EQUAL") {
    const av = Array.isArray(c.allowedValues) ? c.allowedValues : [];
    const ev = av.map((x) => (x == null ? "" : String(x))).join(",");
    return {
      ...base,
      kind: "EQUAL",
      equalValue: ev || null,
      rangeMin: null,
      rangeMax: null,
      relateExpr: null
    };
  }
  return null;
};

const syncSchemaRigidIntoHard = (catalog, schema, table, rigidColumnMap) => {
  const normSch = (s) => (s === undefined || s === null ? "" : String(s));
  const kept = hardConstraints.value.filter((h) => {
    const same =
      (h.catalog || "") === (catalog || "") &&
      normSch(h.schema) === normSch(schema) &&
      (h.table || "") === (table || "");
    if (same && h.source === SCHEMA_RIGID_SOURCE) return false;
    return true;
  });
  const added = [];
  const sigSeen = new Set();
  const pushIfNew = (row) => {
    const sig = `${row.column}|${hardConstraintSignature(row)}`;
    if (sigSeen.has(sig)) return;
    const dupKept = kept.some(
      (h) => sameColHard(h, catalog, schema, table, row.column) && hardConstraintSignature(h) === hardConstraintSignature(row)
    );
    if (dupKept) return;
    sigSeen.add(sig);
    added.push(row);
  };
  for (const [colName, cons] of rigidColumnMap.entries()) {
    for (const c of cons || []) {
      const row = rigidDtoToHardRow(catalog, schema, table, colName, c);
      if (row) pushIfNew(row);
    }
  }
  hardConstraints.value = [...kept, ...added];
};

const refreshFieldGrid = async () => {
  if (connStatus.value !== "ok" || !selectedTableRows.value.length) {
    fieldRows.value = [];
    return;
  }
  fieldGridLoading.value = true;
  const rows = [];
  try {
    for (const tr of selectedTableRows.value) {
      const catalog = tr.catalog;
      const schema = tr.schema || null;
      const table = tr.name;
      const base = { ...connectionPayload(), catalog, schema, table };

      let rigidMap = new Map();
      try {
        const { data: rigidData } = await api.parseHardConstraints(base);
        if (rigidData?.success === false) {
          console.warn(rigidData.message);
        } else {
          rigidMap = buildRigidColumnMap(rigidData);
        }
      } catch (e) {
        console.warn("刚性约束解析失败", table, e);
      }

      syncSchemaRigidIntoHard(catalog, schema, table, rigidMap);

      let colList = [];
      try {
        const { data } = await api.listColumns(base);
        colList = Array.isArray(data) ? data : [];
      } catch (e) {
        ElMessage.warning(`加载列失败: ${catalog}.${table}`);
        continue;
      }

      for (const c of colList) {
        const colName = c.name;
        const smH = summarizeManualList(hardConstraints.value, catalog, schema, table, colName, false);
        const smS = summarizeManualList(softConstraints.value, catalog, schema, table, colName, true);
        const typeName = c.typeName || "";
        const size = c.columnSize != null ? `(${c.columnSize}${c.decimalDigits != null ? `,${c.decimalDigits}` : ""})` : "";
        rows.push({
          catalog,
          schema,
          table,
          column: colName,
          jdbcType: c.jdbcType,
          typeName,
          displayType: `${typeName}${size}`,
          nullable: c.nullable,
          hardSummary: smH.text,
          hardCount: smH.count,
          softSummary: smS.text,
          softCount: smS.count
        });
      }
    }
    fieldRows.value = rows;
  } finally {
    fieldGridLoading.value = false;
  }
};

watch(
  () => [...selectedTableRows.value],
  () => {
    refreshFieldGrid();
  },
  { deep: true }
);

const refreshFieldConstraintSummaries = () => {
  if (!fieldRows.value.length) return;
  for (const fr of fieldRows.value) {
    const smH = summarizeManualList(hardConstraints.value, fr.catalog, fr.schema, fr.table, fr.column, false);
    const smS = summarizeManualList(softConstraints.value, fr.catalog, fr.schema, fr.table, fr.column, true);
    fr.hardSummary = smH.text;
    fr.hardCount = smH.count;
    fr.softSummary = smS.text;
    fr.softCount = smS.count;
  }
};

watch(hardConstraints, refreshFieldConstraintSummaries, { deep: true });
watch(softConstraints, refreshFieldConstraintSummaries, { deep: true });

watch(
  () => dlg.kind,
  (k) => {
    if (k === "RELATE") parseRelateExprToForm(dlg.relateExpr || "");
    else resetRelateFormFields();
  }
);

const uniqSorted = (arr) =>
  [...new Set(arr.filter((x) => x !== undefined && x !== null))].sort((a, b) =>
    String(a).localeCompare(String(b))
  );

const filterCatalogOptions = computed(() =>
  uniqSorted([...catalogList.value, ...selectedCatalogs.value, ...fieldRows.value.map((r) => r.catalog)])
);

const filterSchemaOptions = computed(() => {
  const fromRows = fieldRows.value.map((r) => (r.schema === null || r.schema === undefined ? "" : r.schema));
  return uniqSorted(fromRows);
});

const filterTableOptions = computed(() => uniqSorted(fieldRows.value.map((r) => r.table)));

const filterColumnOptions = computed(() => uniqSorted(fieldRows.value.map((r) => r.column)));

const filterColumnTypeOptions = computed(() => uniqSorted(fieldRows.value.map((r) => r.displayType)));

/** 约束弹窗：关联侧候选项（随 catalog/schema/table 级联） */
const dlgRelateSchemaOptions = computed(() => {
  const cat = dlg.relateRefCatalog;
  const rows = emptyIdent(cat)
    ? fieldRows.value
    : fieldRows.value.filter((r) => sameIdent(r.catalog, cat));
  return uniqSorted(rows.map((r) => (r.schema === null || r.schema === undefined ? "" : r.schema)));
});

const dlgRelateTableOptions = computed(() => {
  let rows = fieldRows.value;
  const cat = dlg.relateRefCatalog;
  const sch = dlg.relateRefSchema;
  if (!emptyIdent(cat)) rows = rows.filter((r) => sameIdent(r.catalog, cat));
  const schNorm = sch === undefined || sch === null ? "" : sch;
  rows = rows.filter((r) => sameIdent(r.schema === null || r.schema === undefined ? "" : r.schema, schNorm));
  return uniqSorted(rows.map((r) => r.table));
});

const dlgRelateColumnOptions = computed(() => {
  let rows = fieldRows.value;
  const cat = dlg.relateRefCatalog;
  const sch = dlg.relateRefSchema;
  const tbl = dlg.relateRefTable;
  if (!emptyIdent(cat)) rows = rows.filter((r) => sameIdent(r.catalog, cat));
  const schNorm = sch === undefined || sch === null ? "" : sch;
  rows = rows.filter((r) => sameIdent(r.schema === null || r.schema === undefined ? "" : r.schema, schNorm));
  if (!emptyIdent(tbl)) rows = rows.filter((r) => sameIdent(r.table, tbl));
  return uniqSorted(rows.map((r) => r.column));
});

const softParseCatalogOptions = computed(() =>
  uniqSorted([...catalogList.value, ...selectedCatalogs.value, ...fieldRows.value.map((r) => r.catalog)])
);

const softParseSchemaOptions = computed(() => filterSchemaOptions.value);

const filteredFieldRows = computed(() => {
  const fc = fieldSearch.catalog?.trim().toLowerCase() || "";
  const fsRaw = fieldSearch.schema;
  const fs = typeof fsRaw === "string" ? fsRaw.trim().toLowerCase() : "";
  const ft = fieldSearch.table?.trim().toLowerCase() || "";
  const fcol = fieldSearch.column?.trim().toLowerCase() || "";
  const ftyp = fieldSearch.columnType?.trim().toLowerCase() || "";
  const hr = fieldSearch.hasRigid;
  const hs = fieldSearch.hasSoft;

  return fieldRows.value.filter((r) => {
    if (fc && String(r.catalog || "").toLowerCase().indexOf(fc) < 0) return false;
    if (fsRaw === "__EMPTY_SCHEMA__") {
      if (r.schema) return false;
    } else if (fs && String(r.schema || "").toLowerCase().indexOf(fs) < 0) return false;
    if (ft && String(r.table || "").toLowerCase().indexOf(ft) < 0) return false;
    if (fcol && String(r.column || "").toLowerCase().indexOf(fcol) < 0) return false;
    if (ftyp && String(r.displayType || "").toLowerCase().indexOf(ftyp) < 0) return false;
    if (hr === "yes" && !(r.hardCount > 0)) return false;
    if (hr === "no" && r.hardCount > 0) return false;
    if (hs === "yes" && !(r.softCount > 0)) return false;
    if (hs === "no" && r.softCount > 0) return false;
    return true;
  });
});

const clearFieldSearch = () => {
  fieldSearch.catalog = "";
  fieldSearch.schema = "";
  fieldSearch.table = "";
  fieldSearch.column = "";
  fieldSearch.columnType = "";
  fieldSearch.hasRigid = "";
  fieldSearch.hasSoft = "";
};

const openSoftParseDialog = () => {
  softParseDlg.sql = "";
  // catalog 可选：不预选，用户可按需填写或仅填 schema
  softParseDlg.catalog = "";
  softParseDlg.schema = "";
  softParseDlg.parseErrors = [];
  softParseDlg.visible = true;
};

const buildSqlParsePrefix = () => {
  const cat = softParseDlg.catalog?.trim() || "";
  const sch = softParseDlg.schema?.trim() || "";
  if (!cat && !sch) return null;
  if (sch) return `${cat}.${sch}`.replace(/^\./, "");
  return cat || null;
};

const confirmSoftSqlParse = async () => {
  softParseDlg.parseErrors = [];
  if (!softParseDlg.sql?.trim()) {
    ElMessage.warning("请输入 SQL");
    return;
  }
  softParseDlg.submitting = true;
  try {
    const prefix = buildSqlParsePrefix();
    const { data } = await api.parseSql({
      sql: softParseDlg.sql.trim(),
      prefix,
      singleQualifierRole: "SCHEMA",
      strict: false
    });
    softParseDlg.parseErrors = data.errors || [];
    if (!data.parseOk) {
      ElMessage.warning({ message: "解析存在告警，已尽可能合并软约束", duration: 4000 });
    }
    const mapped = mapParseToSoft(data.constraints).map((m) => alignSoftConstraintToFieldGrid(m));
    mapped.forEach((m) => softConstraints.value.push(m));
    softParseDlg.visible = false;
    ElNotification.info({
      title: "软约束已合并",
      message: `本次新增 ${mapped.length} 条软约束`,
      duration: 5200,
      position: "bottom-right"
    });
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "解析失败");
  } finally {
    softParseDlg.submitting = false;
  }
};

const openDlg = (mode, preset = {}) => {
  dlg.mode = mode;
  dlg.kind =
    preset.kind != null && preset.kind !== ""
      ? preset.kind
      : mode === "hard"
        ? "NOT_NULL"
        : "EQUAL";
  dlg.catalog = preset.catalog ?? "";
  dlg.schema = preset.schema ?? "";
  dlg.table = preset.table ?? "";
  dlg.column = preset.column ?? "";
  dlg.equalValue = preset.equalValue ?? "";
  dlg.rangeMin = preset.rangeMin ?? "";
  dlg.rangeMax = preset.rangeMax ?? "";
  dlg.relateExpr = preset.relateExpr ?? "";
  dlg.editingId = preset.editingId ?? null;
  if (dlg.kind === "RELATE") {
    parseRelateExprToForm(dlg.relateExpr);
  } else {
    resetRelateFormFields();
  }
  dlg.visible = true;
};

const openAddFromDrawer = (mode) => {
  const r = drawer.row;
  if (!r) return;
  openDlg(mode, { catalog: r.catalog, schema: r.schema || "", table: r.table, column: r.column });
};

const openEditConstraint = (mode, row) => {
  openDlg(mode, {
    catalog: row.catalog,
    schema: row.schema || "",
    table: row.table,
    column: row.column,
    kind: row.kind,
    equalValue: row.equalValue,
    rangeMin: row.rangeMin,
    rangeMax: row.rangeMax,
    relateExpr: row.relateExpr,
    editingId: row.id
  });
};

const saveDlg = () => {
  let relateOut = dlg.relateExpr?.trim() || null;
  if (dlg.kind === "RELATE") {
    if (!dlg.relateRefTable?.trim() || !dlg.relateRefColumn?.trim()) {
      ElMessage.warning("请填写关联目标表与列（可从候选选择）");
      return;
    }
    relateOut = buildRelateExprFromForm();
  }
  const schemaStored =
    dlg.schema === "__EMPTY_SCHEMA__" || dlg.schema === "" || dlg.schema == null ? null : String(dlg.schema).trim() || null;
  const arr = dlg.mode === "hard" ? hardConstraints.value : softConstraints.value;
  const row = {
    id: dlg.editingId || uid(),
    kind: dlg.kind,
    catalog: dlg.catalog,
    schema: schemaStored,
    table: dlg.table,
    column: dlg.column || null,
    equalValue: dlg.equalValue || null,
    rangeMin: dlg.rangeMin || null,
    rangeMax: dlg.rangeMax || null,
    relateExpr: relateOut
  };
  if (dlg.editingId) {
    const i = arr.findIndex((x) => x.id === dlg.editingId);
    if (i >= 0) {
      const prev = arr[i];
      const merged = { ...prev, ...row };
      if (prev.source === SCHEMA_RIGID_SOURCE) {
        delete merged.source;
      }
      arr[i] = merged;
    }
  } else {
    arr.push(row);
  }
  dlg.visible = false;
  dlg.editingId = null;
};

const removeRow = (arr, id) => {
  const i = arr.findIndex((r) => r.id === id);
  if (i >= 0) arr.splice(i, 1);
};

const constraintsForDrawer = (mode) => {
  const r = drawer.row;
  if (!r) return [];
  const arr = mode === "hard" ? hardConstraints.value : softConstraints.value;
  if (mode === "soft") {
    return arr.filter((x) => softConstraintMatchesField(x, r.catalog, r.schema, r.table, r.column));
  }
  return arr.filter(
    (x) =>
      (x.catalog || "") === (r.catalog || "") &&
      (x.schema || "") === (r.schema || "") &&
      (x.table || "") === (r.table || "") &&
      (x.column || "") === (r.column || "")
  );
};

/** 将解析结果中的库表字段名对齐到当前字段网格，便于列表与抽屉展示一致 */
const alignSoftConstraintToFieldGrid = (r) => {
  if (!fieldRows.value.length) return r;
  for (const fr of fieldRows.value) {
    if (!sameIdent(r.table, fr.table)) continue;
    if (sameIdent(r.catalog, fr.catalog) && sameIdent(r.schema, fr.schema)) {
      return { ...r, catalog: fr.catalog, schema: fr.schema, table: fr.table, column: sameIdent(r.column, fr.column) ? fr.column : r.column };
    }
    if (emptyIdent(r.catalog) && sameIdent(r.schema, fr.catalog) && emptyIdent(fr.schema)) {
      return { ...r, catalog: fr.catalog, schema: fr.schema, table: fr.table, column: sameIdent(r.column, fr.column) ? fr.column : r.column };
    }
    if (sameIdent(r.catalog, fr.catalog) && emptyIdent(r.schema) && emptyIdent(fr.schema)) {
      return { ...r, catalog: fr.catalog, schema: fr.schema, table: fr.table, column: sameIdent(r.column, fr.column) ? fr.column : r.column };
    }
  }
  return r;
};

const mapParseToSoft = (constraints) => {
  const out = [];
  if (!constraints?.tables) return out;
  for (const tab of constraints.tables) {
    const t = tab.table || {};
    const cat = t.catalog;
    const sch = t.schema;
    const tbl = t.table;
    for (const col of tab.columns || []) {
      const c = col.column || {};
      const base = {
        id: uid(),
        catalog: c.catalog ?? cat,
        schema: c.schema ?? sch,
        table: c.table ?? tbl,
        column: c.column,
        kind: "EQUAL",
        equalValue: null,
        rangeMin: null,
        rangeMax: null,
        relateExpr: null,
        source: "SQL_PARSE"
      };
      if (col.equals?.length) {
        base.kind = "EQUAL";
        base.equalValue = col.equals.map((e) => e.value).join(",");
        out.push({ ...base });
      }
      if (col.ranges?.length) {
        for (const rg of col.ranges) {
          out.push({
            ...base,
            id: uid(),
            kind: "RANGE",
            rangeMin: rg.min,
            rangeMax: rg.max,
            equalValue: null
          });
        }
      }
      if (col.relates?.length) {
        for (const rel of col.relates) {
          const rc = rel.column || {};
          const relateExpr = JSON.stringify({
            op: (rel.operator || "=").trim(),
            refCatalog: rc.catalog != null && String(rc.catalog).trim() !== "" ? rc.catalog : null,
            refSchema: rc.schema != null && String(rc.schema).trim() !== "" ? rc.schema : null,
            refTable: rc.table != null && String(rc.table).trim() !== "" ? rc.table : null,
            refColumn: rc.column != null && String(rc.column).trim() !== "" ? rc.column : null
          });
          out.push({
            ...base,
            id: uid(),
            kind: "RELATE",
            relateExpr,
            equalValue: null
          });
        }
      }
    }
  }
  return out;
};

const constraintToMap = (r) => {
  const m = {
    kind: r.kind,
    catalog: r.catalog,
    schema: r.schema,
    table: r.table,
    column: r.column
  };
  if (r.equalValue != null) m.equalValue = r.equalValue;
  if (r.rangeMin != null) m.rangeMin = r.rangeMin;
  if (r.rangeMax != null) m.rangeMax = r.rangeMax;
  if (r.relateExpr) m.relateExpr = r.relateExpr;
  if (r.source) m.source = r.source;
  return m;
};

const submit = async () => {
  if (connStatus.value !== "ok") {
    ElMessage.warning("请先测试连接成功");
    return;
  }
  if (!taskName.value.trim()) {
    ElMessage.warning("请填写任务名称");
    return;
  }
  if (!selectedTargets.value.length) {
    ElMessage.warning("请选择目标表");
    return;
  }
  const body = {
    name: taskName.value.trim(),
    description: null,
    connectionMode: connectionMode.value,
    connectionId: connectionMode.value === "SAVED" ? connectionId.value : null,
    inlineConnection: connectionMode.value === "INLINE" ? { ...inline } : null,
    targets: selectedTargets.value,
    hardConstraints: hardConstraints.value.map(constraintToMap),
    softConstraints: softConstraints.value.map(constraintToMap),
    options: { ...options }
  };
  try {
    const { data } = await api.createGenerateTask(body);
    await api.startGenerateTask(data.id);
    ElMessage.success("任务已创建并开始执行");
    if (props.embedded) {
      emit("taskCreated");
    } else {
      router.push("/generate/tasks");
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "提交失败");
  }
};

const onFieldRowClick = (row) => {
  drawer.row = row;
  drawer.visible = true;
};

onMounted(async () => {
  const [{ data: conns }, { data: types }] = await Promise.all([api.getConnections(), api.getDatabaseTypes()]);
  connections.value = Array.isArray(conns) ? conns : [];
  databaseTypes.value = Array.isArray(types) ? types : [];
  const cloneParam = route.query.clone;
  const hasClone =
    cloneParam != null &&
    String(cloneParam).trim() !== "" &&
    Number.isFinite(Number(cloneParam)) &&
    Number(cloneParam) > 0;
  if (connections.value.length && !hasClone) connectionId.value = connections.value[0].id;
  applyDbTypeTemplate();
  if (hasClone) {
    await applyCloneFromTaskId(Number(cloneParam));
    await stripCloneQuery();
  }
});

watch(
  () => route.query.clone,
  async (cid, prev) => {
    if (cid == null || String(cid).trim() === "") return;
    if (cid === prev) return;
    const id = Number(cid);
    if (!Number.isFinite(id) || id <= 0) return;
    await applyCloneFromTaskId(id);
    await stripCloneQuery();
  }
);
</script>

<template>
  <div class="page">
    <el-page-header v-if="!embedded" @back="router.push('/generate/tasks')" content="新建造数任务" />

    <el-card class="mt" :class="connectionCardClass" header="1. 连接信息">
      <el-radio-group v-model="connectionMode">
        <el-radio label="SAVED">使用已保存连接</el-radio>
        <el-radio label="INLINE">自定义连接</el-radio>
      </el-radio-group>

      <div v-if="connectionMode === 'SAVED'" class="row">
        <el-select v-model="connectionId" placeholder="选择连接" filterable style="min-width: 420px; width: 100%; max-width: 720px">
          <el-option v-for="c in connections" :key="c.id" :label="formatConnectionSelectLabel(c)" :value="c.id">
            <span class="conn-opt-line" :title="formatConnectionSelectLabel(c)">{{ formatConnectionSelectLabel(c) }}</span>
          </el-option>
        </el-select>
      </div>

      <div v-else class="grid">
        <el-select v-model="inline.dbType" placeholder="数据库类型" style="width: 200px" @change="applyDbTypeTemplate">
          <el-option v-for="t in databaseTypes" :key="t.code" :label="t.displayName" :value="t.code" />
        </el-select>
        <el-input v-model="inline.host" placeholder="host" />
        <el-input-number v-model="inline.port" :min="1" />
        <el-input v-model="inline.databaseName" placeholder="database" />
        <el-input v-model="inline.username" placeholder="username" />
        <el-input v-model="inline.password" type="password" placeholder="password" show-password />
        <el-input v-model="inline.driverClass" disabled placeholder="driverClass" />
        <el-input v-model="inline.urlTemplate" disabled placeholder="urlTemplate" />
      </div>

      <div class="row mt-sm">
        <el-button type="primary" @click="testConnection">测试连接</el-button>
        <span v-if="connMessage" class="hint">{{ connMessage }}</span>
      </div>
    </el-card>

    <el-card class="mt" header="2. 选择造数库表">
      <el-alert v-if="connStatus !== 'ok'" title="请先完成连接测试" type="info" show-icon class="mb" />
      <template v-else>
        <div class="row">
          <span class="label">库（多选）</span>
          <el-select v-model="selectedCatalogs" multiple filterable placeholder="选择库" style="width: 100%">
            <el-option v-for="c in catalogList" :key="c" :label="c" :value="c" />
          </el-select>
          <el-button type="primary" @click="loadTables">加载表</el-button>
        </div>
        <el-table
          class="mt-sm"
          :data="tableRows"
          :row-key="(r) => `${r.catalog}_${r.schema || ''}_${r.name}`"
          @selection-change="(rows) => (selectedTableRows = rows)"
          max-height="320"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column prop="catalog" label="catalog" width="140" />
          <el-table-column prop="schema" label="schema" width="120" />
          <el-table-column prop="name" label="表" min-width="160" />
          <el-table-column prop="tableType" label="类型" width="100" />
        </el-table>
      </template>
    </el-card>

    <el-card class="mt" header="3. 表字段与约束">
      <el-alert
        title="勾选目标表后自动加载列与刚性约束（MySQL 库表推导会写入下方列表并可编辑）；点击字段行管理约束。存在刚性约束时，造数会优先按刚性约束生成该列取值，再参与软约束等。"
        type="info"
        show-icon
        class="mb"
      />

      <div class="soft-sql-actions row">
        <el-button type="primary" plain @click="openSoftParseDialog">从 SQL 合并软约束…</el-button>
      </div>

      <div class="row mt field-toolbar">
        <el-button :loading="fieldGridLoading" @click="refreshFieldGrid">刷新字段与刚性约束</el-button>
      </div>

      <div class="field-filters mt-sm">
        <el-select
          v-model="fieldSearch.catalog"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="catalog"
          class="filter-select"
        >
          <el-option v-for="o in filterCatalogOptions" :key="'cat-' + o" :label="o || '(空)'" :value="o" />
        </el-select>
        <el-select
          v-model="fieldSearch.schema"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="schema"
          class="filter-select filter-select-sm"
        >
          <el-option label="（仅空 schema）" value="__EMPTY_SCHEMA__" />
          <el-option v-for="o in filterSchemaOptions" :key="'sch-' + o" :label="o === '' ? '（空）' : o" :value="o" />
        </el-select>
        <el-select
          v-model="fieldSearch.table"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="table"
          class="filter-select"
        >
          <el-option v-for="o in filterTableOptions" :key="'tbl-' + o" :label="o" :value="o" />
        </el-select>
        <el-select
          v-model="fieldSearch.column"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="column"
          class="filter-select"
        >
          <el-option v-for="o in filterColumnOptions" :key="'col-' + o" :label="o" :value="o" />
        </el-select>
        <el-select
          v-model="fieldSearch.columnType"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="列类型"
          class="filter-select-wide"
        >
          <el-option v-for="o in filterColumnTypeOptions" :key="'typ-' + o" :label="o" :value="o" />
        </el-select>
        <el-select v-model="fieldSearch.hasRigid" placeholder="刚性约束" clearable class="filter-select">
          <el-option label="不限" value="" />
          <el-option label="有刚性约束" value="yes" />
          <el-option label="无刚性约束" value="no" />
        </el-select>
        <el-select v-model="fieldSearch.hasSoft" placeholder="软约束" clearable class="filter-select">
          <el-option label="不限" value="" />
          <el-option label="有软约束" value="yes" />
          <el-option label="无软约束" value="no" />
        </el-select>
        <el-button class="filter-clear-btn" @click="clearFieldSearch">清空筛选</el-button>
      </div>

      <el-table
        v-loading="fieldGridLoading"
        class="mt-sm field-table"
        :data="filteredFieldRows"
        :row-key="(r) => `${r.catalog}|${r.schema || ''}|${r.table}|${r.column}`"
        size="small"
        max-height="420"
        empty-text="请先勾选目标表并等待加载"
        highlight-current-row
        @row-click="onFieldRowClick"
      >
        <el-table-column prop="catalog" label="catalog" width="100" show-overflow-tooltip />
        <el-table-column prop="schema" label="schema" width="90" show-overflow-tooltip />
        <el-table-column prop="table" label="table" width="120" show-overflow-tooltip />
        <el-table-column prop="column" label="column" width="130" show-overflow-tooltip />
        <el-table-column prop="displayType" label="列类型" min-width="140" show-overflow-tooltip />
        <el-table-column label="可空" width="56">
          <template #default="{ row }">{{ row.nullable ? "Y" : "N" }}</template>
        </el-table-column>
        <el-table-column label="刚性约束" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: row.hardCount === 0 }">{{ row.hardCount === 0 ? "—" : row.hardSummary }}</span>
          </template>
        </el-table-column>
        <el-table-column label="软约束" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: row.softCount === 0 }">{{ row.softSummary }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="mt" header="4. 造数参数">
      <el-form inline>
        <el-form-item label="造数行数"><el-input-number v-model="options.rowCount" :min="1" :max="10000000" /></el-form-item>
        <el-form-item label="并发"><el-input-number v-model="options.concurrency" :min="1" :max="64" /></el-form-item>
        <el-form-item label="batch_size"><el-input-number v-model="options.batchSize" :min="1" :max="50000" /></el-form-item>
        <el-form-item label="写入方式">
          <el-select v-model="options.sinkType" style="width: 140px">
            <el-option label="JDBC 批量插入" value="JDBC" />
            <el-option label="CSV 文件" value="CSV" />
          </el-select>
        </el-form-item>
        <el-form-item label="传输">
          <el-select v-model="options.transport" style="width: 160px">
            <el-option label="进程内 direct" value="direct" />
            <el-option label="RabbitMQ 异步" value="rabbitmq" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        title="已选目标表时由后端按元数据 + Datafaker 造数并写入。选择 RabbitMQ 时需在服务端开启 dbhelp.generate.rabbit.enabled 并启动 Broker；不支持断点续跑。"
        type="info"
        show-icon
      />
    </el-card>

    <div class="mt row">
      <el-button type="primary" size="large" @click="submit">创建并开始</el-button>
      <el-button @click="router.push('/generate/tasks')">取消</el-button>
    </div>

    <el-drawer v-model="drawer.visible" title="字段与约束详情" size="480px" destroy-on-close>
      <template v-if="drawer.row">
        <el-descriptions :column="1" border size="small" class="mb">
          <el-descriptions-item label="定位"
            >{{ drawer.row.catalog }}.{{ drawer.row.schema || "∅" }}.{{ drawer.row.table }}.{{ drawer.row.column }}</el-descriptions-item
          >
          <el-descriptions-item label="列类型">{{ drawer.row.displayType }}</el-descriptions-item>
          <el-descriptions-item label="可空">{{ drawer.row.nullable ? "是" : "否" }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sub-title">刚性约束</h4>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-sm"
          title="某列存在刚性约束时，造数会优先按刚性约束生成该列取值。"
        />
        <div class="row mb-sm">
          <el-button type="primary" size="small" @click="openAddFromDrawer('hard')">新增刚性约束</el-button>
        </div>
        <el-table :data="constraintsForDrawer('hard')" size="small" max-height="200">
          <el-table-column prop="kind" label="类型" width="88" />
          <el-table-column label="摘要" min-width="120" show-overflow-tooltip>
            <template #default="{ row: cr }">
              <span v-if="cr.kind === 'EQUAL'">{{ cr.equalValue }}</span>
              <span v-else-if="cr.kind === 'RANGE'">{{ cr.rangeMin }} ~ {{ cr.rangeMax }}</span>
              <span v-else>{{ formatRelateExprShort(cr.relateExpr) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row: cr }">
              <el-button link type="primary" @click.stop="openEditConstraint('hard', cr)">改</el-button>
              <el-button link type="danger" @click.stop="removeRow(hardConstraints, cr.id)">删</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="sub-title mt">软约束</h4>
        <div class="row mb-sm">
          <el-button type="primary" size="small" @click="openAddFromDrawer('soft')">新增软约束</el-button>
        </div>
        <el-table :data="constraintsForDrawer('soft')" size="small" max-height="200">
          <el-table-column prop="kind" label="类型" width="88" />
          <el-table-column label="摘要" min-width="120" show-overflow-tooltip>
            <template #default="{ row: cr }">
              <span v-if="cr.kind === 'EQUAL'">{{ cr.equalValue }}</span>
              <span v-else-if="cr.kind === 'RANGE'">{{ cr.rangeMin }} ~ {{ cr.rangeMax }}</span>
              <span v-else>{{ formatRelateExprShort(cr.relateExpr) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row: cr }">
              <el-button link type="primary" @click.stop="openEditConstraint('soft', cr)">改</el-button>
              <el-button link type="danger" @click.stop="removeRow(softConstraints, cr.id)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="softParseDlg.visible" title="从 SQL 合并软约束" width="640px" destroy-on-close @closed="softParseDlg.parseErrors = []">
      <el-form label-width="108px">
        <el-form-item label="catalog（可选）">
          <el-select
            v-model="softParseDlg.catalog"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="不选则仅用 schema 或无前缀；也可从候选选择或手输"
            style="width: 100%"
          >
            <el-option v-for="o in softParseCatalogOptions" :key="'sp-cat-' + o" :label="o || '(空)'" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="schema">
          <el-select
            v-model="softParseDlg.schema"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="可选；MySQL 常为空"
            style="width: 100%"
          >
            <el-option label="（空）" value="" />
            <el-option v-for="o in softParseSchemaOptions.filter((x) => x !== '')" :key="'sp-sch-' + o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="SQL">
          <el-input v-model="softParseDlg.sql" type="textarea" :rows="10" placeholder="输入 DML / 查询 SQL，解析 WHERE 等为软约束" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-sm"
          title="catalog 可不填；填写时与 schema 组成 prefix（如 db.public），仅 schema 时也会作为前缀。用于补全 SQL 里未带库名的表引用。"
        />
        <div v-if="softParseDlg.parseErrors.length" class="parse-err">
          <div v-for="(e, i) in softParseDlg.parseErrors" :key="i">{{ e.stage }}: {{ e.message }}</div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="softParseDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="softParseDlg.submitting" @click="confirmSoftSqlParse">解析并合并</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dlg.visible" :title="(dlg.editingId ? '编辑' : '新增') + (dlg.mode === 'hard' ? '刚性约束' : '软约束')" width="600px" destroy-on-close>
      <el-form label-width="108px">
        <el-form-item label="类型">
          <el-select v-if="dlg.mode === 'hard'" v-model="dlg.kind" style="width: 100%">
            <el-option label="范围 RANGE" value="RANGE" />
            <el-option label="等值 EQUAL" value="EQUAL" />
            <el-option label="关联 RELATE" value="RELATE" />
            <el-option label="非空 NOT_NULL" value="NOT_NULL" />
            <el-option label="唯一 UNIQUE" value="UNIQUE" />
          </el-select>
          <el-select v-else v-model="dlg.kind" style="width: 100%">
            <el-option label="范围 RANGE" value="RANGE" />
            <el-option label="等值 EQUAL" value="EQUAL" />
            <el-option label="关联 RELATE" value="RELATE" />
          </el-select>
        </el-form-item>

        <template v-if="dlg.mode === 'soft'">
          <el-form-item label="catalog">
            <el-select
              v-model="dlg.catalog"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="候选或手输"
              class="dlg-field-select"
            >
              <el-option v-for="o in filterCatalogOptions" :key="'dlg-cat-' + o" :label="o || '(空)'" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="schema">
            <el-select
              v-model="dlg.schema"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="候选或手输（MySQL 常选空）"
              class="dlg-field-select"
            >
              <el-option v-for="o in filterSchemaOptions" :key="'dlg-sch-' + o" :label="o === '' ? '（空）' : o" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="table">
            <el-select
              v-model="dlg.table"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="候选或手输"
              class="dlg-field-select"
            >
              <el-option v-for="o in filterTableOptions" :key="'dlg-tbl-' + o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="column">
            <el-select
              v-model="dlg.column"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="候选或手输"
              class="dlg-field-select"
            >
              <el-option v-for="o in filterColumnOptions" :key="'dlg-col-' + o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
        </template>
        <template v-else>
          <el-alert
            v-if="dlg.mode === 'hard' && !dlg.editingId"
            type="info"
            :closable="false"
            show-icon
            class="mb-sm"
            title="库表定位（catalog / schema / table / column）由当前字段行确定，不可修改。该列有刚性约束时，造数会优先按刚性约束生成取值。"
          />
          <el-form-item label="catalog">
            <el-input v-model="dlg.catalog" :readonly="dlg.mode === 'hard' && !dlg.editingId" />
          </el-form-item>
          <el-form-item label="schema">
            <el-input v-model="dlg.schema" :readonly="dlg.mode === 'hard' && !dlg.editingId" />
          </el-form-item>
          <el-form-item label="table">
            <el-input v-model="dlg.table" :readonly="dlg.mode === 'hard' && !dlg.editingId" />
          </el-form-item>
          <el-form-item label="column">
            <el-input v-model="dlg.column" :readonly="dlg.mode === 'hard' && !dlg.editingId" />
          </el-form-item>
        </template>

        <el-form-item v-if="dlg.kind === 'EQUAL'" label="等值"><el-input v-model="dlg.equalValue" /></el-form-item>
        <el-form-item v-if="dlg.kind === 'RANGE'" label="min"><el-input v-model="dlg.rangeMin" /></el-form-item>
        <el-form-item v-if="dlg.kind === 'RANGE'" label="max"><el-input v-model="dlg.rangeMax" /></el-form-item>

        <template v-if="dlg.kind === 'RELATE'">
          <el-divider content-position="left">关联侧（引用列）</el-divider>
          <el-alert type="info" :closable="false" show-icon class="mb-sm" title="从已加载的「表字段与约束」中选 catalog/schema/表/列；也可手输。保存为结构化 JSON。" />
          <el-form-item label="运算符">
            <el-select v-model="dlg.relateOperator" class="dlg-field-select">
              <el-option v-for="op in RELATE_OPERATORS" :key="op" :label="op" :value="op" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联 catalog">
            <el-select
              v-model="dlg.relateRefCatalog"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="可选"
              class="dlg-field-select"
              @change="() => { dlg.relateRefSchema = ''; dlg.relateRefTable = ''; dlg.relateRefColumn = ''; }"
            >
              <el-option v-for="o in filterCatalogOptions" :key="'rel-cat-' + o" :label="o || '(空)'" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联 schema">
            <el-select
              v-model="dlg.relateRefSchema"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="MySQL 常为空"
              class="dlg-field-select"
              @change="() => { dlg.relateRefTable = ''; dlg.relateRefColumn = ''; }"
            >
              <el-option label="（空）" value="" />
              <el-option v-for="o in dlgRelateSchemaOptions.filter((x) => x !== '')" :key="'rel-sch-' + o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联表">
            <el-select
              v-model="dlg.relateRefTable"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="候选或手输"
              class="dlg-field-select"
              @change="() => { dlg.relateRefColumn = ''; }"
            >
              <el-option v-for="o in dlgRelateTableOptions" :key="'rel-tbl-' + o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联列">
            <el-select
              v-model="dlg.relateRefColumn"
              clearable
              filterable
              allow-create
              default-first-option
              placeholder="候选或手输"
              class="dlg-field-select"
            >
              <el-option v-for="o in dlgRelateColumnOptions" :key="'rel-col-' + o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" @click="saveDlg">保存</el-button>
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
.mt-sm {
  margin-top: 8px;
}
.mb {
  margin-bottom: 8px;
}
.mb-sm {
  margin-bottom: 8px;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
  margin-top: 12px;
}
.label {
  color: #606266;
  white-space: nowrap;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.conn-ok {
  background: #f0f9eb;
  border-color: #c2e7b0;
}
.conn-fail {
  background: #fef0f0;
  border-color: #fbc4c4;
}
.parse-err {
  color: #f56c6c;
  font-size: 13px;
}
.muted {
  color: #909399;
}
.conn-opt-line {
  display: block;
  font-size: 13px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.soft-sql-actions {
  padding: 4px 0 12px;
  border-bottom: 1px solid #ebeef5;
}
.field-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.field-filters .filter-select {
  width: 120px;
}
.field-filters .filter-select-sm {
  width: 118px;
}
.field-filters .filter-select-wide {
  width: 168px;
}
.field-filters .filter-clear-btn {
  height: 32px;
}
.field-toolbar {
  margin-top: 12px;
}
.field-table :deep(.el-table__row) {
  cursor: pointer;
}
.sub-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.dlg-field-select {
  width: 100%;
  max-width: 100%;
}
</style>
