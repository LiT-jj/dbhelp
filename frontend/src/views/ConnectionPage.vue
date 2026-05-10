<script setup>
import { onMounted, reactive, ref, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { api } from "../api";

const list = ref([]);
const databaseTypes = ref([]);
const dialogVisible = ref(false);
const dialogMode = ref("create");
const editingId = ref(null);

const emptyForm = () => ({
  name: "",
  dbType: "",
  host: "127.0.0.1",
  port: 3306,
  databaseName: "",
  username: "",
  password: "",
  driverClass: "",
  urlTemplate: ""
});

const form = reactive(emptyForm());

const applyDbTypeTemplate = () => {
  const t = databaseTypes.value.find((x) => x.code === form.dbType);
  if (t) {
    form.driverClass = t.driverClass;
    form.urlTemplate = t.urlTemplate;
    if (!form.port || form.port === 3306) {
      form.port = t.defaultPort || form.port;
    }
  }
};

const load = async () => {
  try {
    const { data } = await api.getConnections();
    list.value = Array.isArray(data) ? data : [];
  } catch (e) {
    list.value = [];
    ElMessage.error(e?.response?.data?.message || "加载连接列表失败");
  }
};

const loadDatabaseTypes = async () => {
  try {
    const { data } = await api.getDatabaseTypes();
    databaseTypes.value = Array.isArray(data) ? data : [];
    if (!form.dbType && databaseTypes.value.length) {
      form.dbType = databaseTypes.value[0].code;
      applyDbTypeTemplate();
    }
  } catch {
    databaseTypes.value = [];
  }
};

const openCreate = async () => {
  dialogMode.value = "create";
  editingId.value = null;
  Object.assign(form, emptyForm());
  await loadDatabaseTypes();
  if (!form.dbType && databaseTypes.value.length) {
    form.dbType = databaseTypes.value[0].code;
    applyDbTypeTemplate();
  }
  dialogVisible.value = true;
};

const openEdit = async (row) => {
  dialogMode.value = "edit";
  editingId.value = row.id;
  Object.assign(form, emptyForm(), {
    name: row.name,
    dbType: row.dbType,
    host: row.host,
    port: row.port,
    databaseName: row.databaseName || "",
    username: row.username,
    password: "",
    driverClass: row.driverClass,
    urlTemplate: row.urlTemplate
  });
  await loadDatabaseTypes();
  dialogVisible.value = true;
};

const payloadForUpdate = () => {
  const body = {
    name: form.name,
    dbType: form.dbType,
    host: form.host,
    port: form.port,
    databaseName: form.databaseName || null,
    username: form.username,
    driverClass: form.driverClass,
    urlTemplate: form.urlTemplate
  };
  if (form.password?.trim()) {
    body.password = form.password.trim();
  }
  return body;
};

const payloadForCreate = () => ({
  name: form.name.trim(),
  dbType: form.dbType,
  host: form.host,
  port: form.port,
  databaseName: form.databaseName || null,
  username: form.username,
  password: form.password,
  driverClass: form.driverClass,
  urlTemplate: form.urlTemplate
});

const save = async () => {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写连接名称");
    return;
  }
  if (!form.dbType) {
    ElMessage.warning("请选择数据库类型");
    return;
  }
  try {
    if (dialogMode.value === "create") {
      if (!form.password) {
        ElMessage.warning("新建连接需要填写密码");
        return;
      }
      await api.createConnection(payloadForCreate());
      ElMessage.success("连接已保存");
    } else {
      await api.updateConnection(editingId.value, payloadForUpdate());
      ElMessage.success("连接已更新");
    }
    dialogVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "保存失败");
  }
};

const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除连接「${row.name}」？`, "删除", { type: "warning" });
    await api.deleteConnection(row.id);
    ElMessage.success("已删除");
    await load();
  } catch (e) {
    if (e !== "cancel") ElMessage.error(e?.response?.data?.message || e.message || "删除失败");
  }
};

/** 弹窗内：按表单字段测试（未保存也可用） */
const testInline = async () => {
  try {
    const { data } = await api.testConnection({
      dbType: form.dbType,
      host: form.host,
      port: form.port,
      databaseName: form.databaseName,
      username: form.username,
      password: form.password,
      driverClass: form.driverClass,
      urlTemplate: form.urlTemplate
    });
    ElMessage[data.success ? "success" : "error"](data.message);
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "测试失败");
  }
};

/** 列表行：对已保存连接仅传 connectionId（无需密码） */
const testSaved = async (row) => {
  try {
    const { data } = await api.testConnection({ connectionId: row.id });
    ElMessage[data.success ? "success" : "error"](data.message);
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "测试失败");
  }
};

const exportConfig = async () => {
  try {
    const { data } = await api.exportConnections();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "dbhelp-connections.json";
    a.click();
    URL.revokeObjectURL(url);
    ElMessage.success("已导出");
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "导出失败");
  }
};

const importUploadRef = ref(null);

const onImportFile = async (uploadFile) => {
  const raw = uploadFile.raw;
  if (!raw) return;
  try {
    const fd = new FormData();
    fd.append("file", raw);
    const { data } = await api.importConnections(fd);
    const n = data?.imported ?? 0;
    ElMessage.success(typeof n === "number" ? `已导入 ${n} 条连接` : "导入完成");
    await load();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "导入失败");
  }
  importUploadRef.value?.clearFiles?.();
};

const dialogTitle = computed(() => (dialogMode.value === "create" ? "新增连接" : "编辑连接"));

onMounted(async () => {
  await Promise.all([load(), loadDatabaseTypes()]);
});
</script>

<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">新增连接</el-button>
        <el-button @click="exportConfig">导出配置</el-button>
        <el-upload
          ref="importUploadRef"
          class="import-upload"
          :auto-upload="false"
          :show-file-list="false"
          accept="application/json,.json"
          :on-change="onImportFile"
        >
          <el-button>导入配置</el-button>
        </el-upload>
      </div>
    </template>
    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="72" />
      <el-table-column prop="name" label="连接名称" min-width="140" />
      <el-table-column prop="dbType" label="类型" width="100" />
      <el-table-column prop="host" label="主机" min-width="120" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="databaseName" label="数据库" min-width="120" show-overflow-tooltip />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="testSaved(row)">测试</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
    <el-form :model="form" label-width="120px">
      <el-form-item label="连接名称" required>
        <el-input v-model="form.name" placeholder="名称" />
      </el-form-item>
      <el-form-item label="数据库类型" required>
        <el-select v-model="form.dbType" placeholder="类型" style="width: 100%" @change="applyDbTypeTemplate">
          <el-option v-for="t in databaseTypes" :key="t.code" :label="t.displayName || t.code" :value="t.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="主机" required><el-input v-model="form.host" /></el-form-item>
      <el-form-item label="端口" required><el-input-number v-model="form.port" :min="1" style="width: 100%" /></el-form-item>
      <el-form-item label="数据库"><el-input v-model="form.databaseName" placeholder="可为空" /></el-form-item>
      <el-form-item label="用户名" required><el-input v-model="form.username" /></el-form-item>
      <el-form-item :label="dialogMode === 'create' ? '密码' : '密码（可选）'">
        <el-input v-model="form.password" type="password" show-password placeholder="编辑时留空表示不改" />
      </el-form-item>
      <el-form-item label="驱动类" required>
        <el-input v-model="form.driverClass" disabled />
      </el-form-item>
      <el-form-item label="URL 模板" required>
        <el-input v-model="form.urlTemplate" type="textarea" :rows="2" disabled />
      </el-form-item>
      <el-form-item>
        <el-button @click="testInline">测试连接</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.import-upload {
  display: inline-block;
}
</style>
