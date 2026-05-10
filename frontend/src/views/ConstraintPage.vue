<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../api";

const list = ref([]);
const form = reactive({ name: "", sqlStatement: "", priority: 0 });
const parsed = ref(null);

const load = async () => {
  const { data } = await api.getConstraints();
  list.value = data;
};

const parseSql = async () => {
  const { data } = await api.parseConstraintSql({ sqlStatement: form.sqlStatement });
  parsed.value = data;
};

const save = async () => {
  await api.createConstraint(form);
  ElMessage.success("约束已保存");
  await load();
};

onMounted(load);
</script>

<template>
  <el-card>
    <el-form :model="form" label-width="100px">
      <el-form-item label="约束名称"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="SQL">
        <el-input v-model="form.sqlStatement" :rows="4" type="textarea" placeholder="输入SQL约束语句" />
      </el-form-item>
      <el-form-item label="优先级"><el-input-number v-model="form.priority" /></el-form-item>
      <el-form-item>
        <el-button @click="parseSql">解析SQL</el-button>
        <el-button type="primary" @click="save">保存约束</el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="parsed" type="success" :closable="false" :title="`解析结果类型: ${parsed.type}`" />
    <el-table :data="list" style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" />
      <el-table-column prop="priority" label="优先级" />
    </el-table>
  </el-card>
</template>
