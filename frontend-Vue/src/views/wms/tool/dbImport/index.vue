<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="import-header">
          <div>
            <div class="import-title">数据库合并导入</div>
            <div class="import-tip">仅支持本系统导出的 .sql / .sql.gz；先预览，再合并，不会直接覆盖用户和权限。</div>
          </div>
          <el-upload :show-file-list="false" :before-upload="beforeUpload" accept=".sql,.gz">
            <el-button type="primary" icon="Upload" :loading="uploading">选择数据库文件</el-button>
          </el-upload>
        </div>
      </template>

      <el-alert v-if="previewData" type="warning" :closable="false" show-icon style="margin-bottom:14px">
        {{ previewData.warning }} 冲突策略按表选择：跳过冲突或使用导入数据覆盖冲突记录。
      </el-alert>

      <template v-if="previewData">
        <el-divider content-position="left">用户绑定</el-divider>
        <el-table :data="previewData.users" border size="small" empty-text="导入文件没有用户信息">
          <el-table-column prop="sourceUsername" label="导入库用户" width="180" />
          <el-table-column label="绑定当前用户" min-width="220">
            <template #default="{ row }">
              <el-select v-model="row.targetUsername" clearable filterable placeholder="不绑定则保留原名称" style="width:220px">
                <el-option v-for="name in previewData.targetUsers" :key="name" :label="name" :value="name" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.targetUsername ? 'success' : 'warning'">{{ row.targetUsername ? '已绑定' : '未绑定' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">业务表预览</el-divider>
        <el-table :data="previewData.tables" border stripe size="small">
          <el-table-column prop="name" label="表名" min-width="240" />
          <el-table-column prop="sourceCount" label="导入记录" width="100" align="right" />
          <el-table-column prop="targetCount" label="当前记录" width="100" align="right" />
          <el-table-column prop="newCount" label="新增" width="90" align="right" />
          <el-table-column prop="conflictCount" label="冲突" width="90" align="right" />
          <el-table-column label="冲突处理" width="190">
            <template #default="{ row }">
              <el-select v-model="row.strategy" size="small" :disabled="row.status !== 'ok'">
                <el-option label="跳过冲突" value="skip" />
                <el-option label="导入数据覆盖" value="replace" />
                <el-option label="不导入此表" value="ignore" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>

        <div class="import-actions">
          <el-button @click="cancelImport">取消并清理临时库</el-button>
          <el-button type="primary" :loading="applying" @click="applyImport">确认合并导入</el-button>
        </div>
      </template>

      <el-empty v-else description="请选择数据库备份文件开始预览" />
    </el-card>
  </div>
</template>

<script setup name="DbImport">
import { ref, getCurrentInstance } from 'vue'
import { uploadDbImport, applyDbImport, cancelDbImport } from '@/api/wms/dbImport'

const { proxy } = getCurrentInstance()
const uploading = ref(false)
const applying = ref(false)
const sessionId = ref('')
const previewData = ref(null)

async function beforeUpload(file) {
  uploading.value = true
  try {
    const res = await uploadDbImport(file)
    sessionId.value = res.data.sessionId
    previewData.value = res.data.preview
    previewData.value.tables.forEach(row => { row.strategy = row.status === 'ok' ? 'skip' : 'ignore' })
    proxy.$modal.msgSuccess('文件已导入临时库，请核对用户绑定和冲突表')
  } finally {
    uploading.value = false
  }
  return false
}

async function applyImport() {
  await proxy.$modal.confirm('确认合并导入吗？已选择“导入数据覆盖”的冲突记录会修改当前数据库。')
  applying.value = true
  try {
    const res = await applyDbImport(sessionId.value, {
      tables: previewData.value.tables.map(row => ({ name: row.name, strategy: row.strategy })),
      users: previewData.value.users.map(row => ({ sourceUsername: row.sourceUsername, targetUsername: row.targetUsername }))
    })
    proxy.$modal.msgSuccess(res.data?.message || '导入完成')
    previewData.value = null
    sessionId.value = ''
  } finally {
    applying.value = false
  }
}

async function cancelImport() {
  if (sessionId.value) await cancelDbImport(sessionId.value)
  previewData.value = null
  sessionId.value = ''
}
</script>

<style scoped>
.import-header { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.import-title { font-size:16px; font-weight:600; }
.import-tip { margin-top:5px; color:#909399; font-size:13px; }
.import-actions { display:flex; justify-content:flex-end; gap:12px; margin-top:18px; }
</style>
