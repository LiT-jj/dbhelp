<script setup>
import { reactive, ref } from "vue";
import { api } from "../api";

const form = reactive({
  sourceConnectionId: 1,
  targetConnectionId: 2,
  sql: "SELECT 1",
  executeCount: 3
});
const result = ref(null);

const executeCompare = async () => {
  const { data } = await api.executeCompare(form);
  result.value = data;
};
</script>

<template>
  <el-card>
    <el-form :model="form" label-width="140px">
      <el-form-item label="源连接ID"><el-input-number v-model="form.sourceConnectionId" :min="1" /></el-form-item>
      <el-form-item label="目标连接ID"><el-input-number v-model="form.targetConnectionId" :min="1" /></el-form-item>
      <el-form-item label="SQL"><el-input v-model="form.sql" type="textarea" :rows="4" /></el-form-item>
      <el-form-item><el-button type="primary" @click="executeCompare">执行比对</el-button></el-form-item>
    </el-form>
    <el-descriptions v-if="result" border :column="2">
      <el-descriptions-item label="行数一致">{{ result.comparisonResult.rowCountMatch }}</el-descriptions-item>
      <el-descriptions-item label="内容一致">{{ result.comparisonResult.contentMatch }}</el-descriptions-item>
      <el-descriptions-item label="源平均耗时">{{ result.performanceResult.sourceAverageTime }} ms</el-descriptions-item>
      <el-descriptions-item label="目标平均耗时">{{ result.performanceResult.targetAverageTime }} ms</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>
