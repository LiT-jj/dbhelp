<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../api";

const form = reactive({
  connectionId: 1,
  tableName: "users",
  columnConfigs: {},
  rowCount: 10,
  generateSql: false
});
const previewData = ref([]);

const preview = async () => {
  const { data } = await api.previewGenerate(form);
  previewData.value = data.data || [];
  ElMessage.success("预览完成");
};
</script>

<template>
  <el-card>
    <el-form :model="form" inline>
      <el-form-item label="连接ID"><el-input-number v-model="form.connectionId" :min="1" /></el-form-item>
      <el-form-item label="表名"><el-input v-model="form.tableName" /></el-form-item>
      <el-form-item label="生成数量"><el-input-number v-model="form.rowCount" :min="1" :max="100000" /></el-form-item>
      <el-form-item><el-button type="primary" @click="preview">生成预览</el-button></el-form-item>
    </el-form>
    <el-table :data="previewData">
      <el-table-column prop="id" label="ID" />
      <el-table-column prop="name" label="名称" />
    </el-table>
  </el-card>
</template>
