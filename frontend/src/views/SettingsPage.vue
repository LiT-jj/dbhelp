<template>
  <el-card>
    <el-descriptions title="系统设置" border :column="1">
      <el-descriptions-item label="SQL超时">30 秒</el-descriptions-item>
      <el-descriptions-item label="单次生成上限">100000 条</el-descriptions-item>
      <el-descriptions-item label="缓存">本地缓存（可扩展 Redis）</el-descriptions-item>
      <el-descriptions-item label="队列">同步处理（可扩展 RabbitMQ）</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>
