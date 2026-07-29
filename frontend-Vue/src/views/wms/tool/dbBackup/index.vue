<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="backup-header">
          <div>
            <div class="backup-title">数据库备份</div>
            <div class="backup-tip">导出当前外部 MySQL 数据库，备份文件只保存在服务器端。</div>
          </div>
          <div>
            <el-button icon="Refresh" @click="loadList" :loading="loading">刷新</el-button>
            <el-button type="primary" icon="Download" @click="createBackup" :loading="creating">立即备份</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="自动备份由 Docker 的 wms-db-backup 服务每天执行；手动备份可能需要几十秒，请不要重复点击。"
        style="margin-bottom: 14px"
      />

      <el-table :data="list" border stripe v-loading="loading" empty-text="暂无备份文件">
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="name" label="文件名" min-width="300" />
        <el-table-column label="大小" width="130" align="right">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column prop="lastModified" label="备份时间" min-width="210" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="downloadBackup(row)">下载</el-button>
            <el-button link type="danger" @click="removeBackup(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="DbBackup">
import { ref, getCurrentInstance } from 'vue'
import { saveAs } from 'file-saver'
import { listDbBackups, createDbBackup, downloadDbBackup, deleteDbBackup } from '@/api/wms/dbBackup'

const { proxy } = getCurrentInstance()
const list = ref([])
const loading = ref(false)
const creating = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await listDbBackups()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function createBackup() {
  await proxy.$modal.confirm('确认立即导出整个数据库吗？备份期间请不要重复操作。')
  creating.value = true
  try {
    const res = await createDbBackup()
    proxy.$modal.msgSuccess(`备份完成：${res.data?.name || ''}`)
    await loadList()
  } finally {
    creating.value = false
  }
}

async function downloadBackup(row) {
  const data = await downloadDbBackup(row.name)
  saveAs(new Blob([data], { type: 'application/gzip' }), row.name)
}

async function removeBackup(row) {
  await proxy.$modal.confirm(`确认删除备份文件「${row.name}」吗？`)
  await deleteDbBackup(row.name)
  proxy.$modal.msgSuccess('删除成功')
  await loadList()
}

function formatSize(size) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

loadList()
</script>

<style scoped>
.backup-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.backup-title { font-size: 16px; font-weight: 600; }
.backup-tip { margin-top: 5px; color: #909399; font-size: 13px; }
</style>
