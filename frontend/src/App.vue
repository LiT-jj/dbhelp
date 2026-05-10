<template>
  <el-container class="app-shell layout">
    <el-header class="app-header header" height="56px">
      <div class="app-header__glow" aria-hidden="true" />
      <div class="app-header__inner">
        <div class="app-header__title">数据<span class="accent">生成</span>工具</div>
        <div class="app-header__tagline">快速编排 · 活跃流水线 · <strong>全链路加速</strong></div>
      </div>
    </el-header>
    <el-container class="app-body">
      <el-aside width="232px" class="app-aside aside">
        <div class="app-aside__brand">
          <div class="app-aside__logo" aria-hidden="true">⚡</div>
          <div class="app-aside__hint">选一项开始；造数任务内可切换「新建」与列表。</div>
        </div>
        <el-menu
          :default-active="$route.path"
          router
          background-color="transparent"
          text-color="rgba(255,255,255,0.72)"
          active-text-color="#041016"
        >
          <el-menu-item index="/connections">连接管理</el-menu-item>
          <el-menu-item index="/generate/tasks">造数任务</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="app-main main">
        <router-view />
      </el-main>
    </el-container>
    <el-footer class="app-footer footer" height="40px">
      <span class="app-footer__dot" aria-hidden="true" />
      <span>系统就绪</span>
    </el-footer>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.header {
  height: 56px !important;
  display: flex;
  align-items: center;
  padding: 0 22px;
}
.app-body {
  flex: 1;
  min-height: 0;
}
.aside {
  width: 232px !important;
}
.main {
  padding: 16px 20px 24px;
}
.footer {
  height: 40px !important;
  line-height: 40px;
}
</style>
