<script setup>
import { onMounted, ref } from "vue";
import { api } from "../api";

const tables = ref([]);
const selected = ref("");
const columns = ref([]);

const loadTables = async () => {
  const { data } = await api.getTables();
  tables.value = data;
};

const loadColumns = async (name) => {
  if (!name) return;
  const { data } = await api.getColumns(name);
  columns.value = data;
};

onMounted(loadTables);
</script>

<template>
  <el-card>
    <el-space direction="vertical" fill>
      <el-select v-model="selected" placeholder="选择表" @change="loadColumns">
        <el-option v-for="t in tables" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
      </el-select>
      <el-table :data="columns">
        <el-table-column prop="columnName" label="字段名" />
        <el-table-column prop="dataType" label="类型" />
        <el-table-column prop="columnSize" label="长度" />
        <el-table-column prop="nullable" label="可空" />
        <el-table-column prop="columnComment" label="注释" />
      </el-table>
    </el-space>
  </el-card>
</template>
