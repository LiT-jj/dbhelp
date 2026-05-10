import { createRouter, createWebHistory } from "vue-router";
import ConnectionPage from "./views/ConnectionPage.vue";
import GenerateTaskListPage from "./views/GenerateTaskListPage.vue";

const routes = [
  { path: "/", redirect: "/connections" },
  { path: "/connections", component: ConnectionPage },
  { path: "/generate/tasks", component: GenerateTaskListPage },
  {
    path: "/generate/tasks/new",
    redirect: (to) => ({ path: "/generate/tasks", query: { ...to.query, tab: "new" } })
  }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
