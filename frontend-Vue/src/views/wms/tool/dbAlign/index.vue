<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div style="display:flex; align-items:center; gap:12px;">
          <span style="font-size:16px; font-weight:600;">数据库对齐</span>
          <span style="color:#909399; font-size:13px;">检测并补齐旧数据库缺失的新版本表 / 字段 / 菜单（幂等，可重复执行）</span>
          <div style="flex:1"></div>
          <el-button icon="Refresh" @click="doCheck" :loading="checking">重新检测</el-button>
          <el-button
            type="primary"
            icon="Top"
            :loading="running"
            :disabled="missingCount === 0"
            @click="doRun"
          >一键对齐{{ missingCount ? `（缺 ${missingCount} 项）` : '' }}</el-button>
        </div>
      </template>

      <el-alert
        v-if="missingCount === 0 && list.length"
        type="success" :closable="false" show-icon
        title="数据库已是最新版本，无需对齐。"
        style="margin-bottom:14px;"
      />
      <el-alert
        v-else-if="missingCount > 0"
        type="warning" :closable="false" show-icon
        :title="`检测到 ${missingCount} 项缺失，点击右上角「一键对齐」即可补齐。`"
        style="margin-bottom:14px;"
      />

      <el-table :data="list" border stripe v-loading="checking" empty-text="点击「重新检测」">
        <el-table-column type="index" label="#" width="55" align="center"/>
        <el-table-column label="对象" prop="name" min-width="280"/>
        <el-table-column label="类型" prop="type" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.exists" type="success" effect="dark">已存在</el-tag>
            <el-tag v-else type="danger" effect="dark">缺失</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top:12px; color:#909399; font-size:12px;">
        说明：对齐只会「新建缺失项」，不会删除或修改已有数据；新建的菜单需在「角色管理」里授权给对应角色后其他账号才可见（超级管理员自动可见）。
      </div>
    </el-card>
  </div>
</template>

<script setup name="DbAlign">
import { ref, computed } from 'vue'
import { getCurrentInstance } from 'vue'
import { checkDbAlign, runDbAlign } from '@/api/wms/dbAlign'

const { proxy } = getCurrentInstance()
const list = ref([])
const checking = ref(false)
const running = ref(false)

const missingCount = computed(() => list.value.filter(it => !it.exists).length)

async function doCheck() {
  checking.value = true
  try {
    const res = await checkDbAlign()
    list.value = res.data || []
  } finally {
    checking.value = false
  }
}

async function doRun() {
  await proxy.$modal.confirm(`确认补齐缺失的 ${missingCount.value} 项吗？该操作只新增、不会删除已有数据。`)
  running.value = true
  try {
    const res = await runDbAlign()
    const applied = res.data?.applied || []
    const failed = res.data?.failed || []
    if (failed.length) {
      proxy.$modal.msgWarning(`已补齐 ${applied.length} 项，${failed.length} 项失败，请查看后端日志`)
    } else {
      proxy.$modal.msgSuccess(`对齐完成，已补齐 ${applied.length} 项`)
    }
    await doCheck()
  } finally {
    running.value = false
  }
}

doCheck()
</script>
