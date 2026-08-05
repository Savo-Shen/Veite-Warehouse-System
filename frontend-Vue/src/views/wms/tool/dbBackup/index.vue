<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="backup-header">
          <div>
            <div class="backup-title">数据库备份</div>
            <div class="backup-tip">导出整个 MySQL 数据库并压缩保存。备份由后端自己执行，和数据库是否跑在 Docker 里无关。</div>
          </div>
          <div class="backup-actions">
            <el-button icon="Refresh" @click="loadAll" :loading="loading">刷新</el-button>
            <el-button icon="FirstAidKit" @click="runCheck" :loading="checking">环境自检</el-button>
            <el-button type="primary" icon="Download" @click="createBackup" :loading="creating">立即备份</el-button>
          </div>
        </div>
      </template>

      <!-- 上次执行结果：失败时飘红，避免「看起来在跑其实一直失败」 -->
      <el-alert
        v-if="lastRun"
        :type="lastRun.success ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="lastRunTitle"
        style="margin-bottom: 14px"
      >
        <template #default>
          <div class="backup-status">
            <span>{{ lastRun.message || '—' }}</span>
            <span v-if="!lastRun.success">失败期间数据没有新备份，请点「环境自检」定位原因，修好后点「立即备份」补一次。</span>
          </div>
        </template>
      </el-alert>

      <el-alert
        :type="status.enabled ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="autoBackupTitle"
        style="margin-bottom: 14px"
      >
        <template #default>
          <div class="backup-status">
            <span>备份目录：{{ status.dir || '—' }}</span>
            <span v-if="status.mirrorDir">离机副本：{{ status.mirrorDir }}</span>
            <span v-else class="backup-status-warn">未配置离机副本，备份与数据库同盘，硬盘损坏会一起丢失</span>
          </div>
        </template>
      </el-alert>

      <!-- 备份计划 -->
      <el-collapse v-model="openedPanels" style="margin-bottom: 14px">
        <el-collapse-item name="plan">
          <template #title>
            <span class="plan-title">备份计划设置</span>
            <span class="plan-subtitle">{{ planSummary }}</span>
          </template>

          <el-form :model="form" label-width="130px" class="plan-form">
            <el-form-item label="自动备份">
              <el-switch v-model="form.enabled" active-text="开启" inactive-text="关闭" />
            </el-form-item>

            <el-form-item label="备份方式">
              <el-radio-group v-model="form.mode">
                <el-radio label="daily">每天定时</el-radio>
                <el-radio label="interval">按间隔时间</el-radio>
              </el-radio-group>
            </el-form-item>

            <template v-if="form.mode === 'daily'">
              <el-form-item label="备份时刻">
                <el-time-select
                  v-model="form.dailyTime"
                  start="00:00"
                  step="00:30"
                  end="23:30"
                  placeholder="选择时间"
                  style="width: 150px"
                />
                <span class="form-tip">到点没开机也没关系，开机后会自动补上当天这一次。</span>
              </el-form-item>
              <el-form-item label="星期">
                <el-checkbox-group v-model="form.weekdays">
                  <el-checkbox v-for="d in weekdayOptions" :key="d.value" :label="d.value">{{ d.label }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
            </template>

            <el-form-item v-else label="备份间隔">
              <el-input-number v-model="form.intervalHours" :min="1" :max="8760" :step="1" />
              <span class="form-tip">小时。距上次备份超过这个时长就备份一次。</span>
            </el-form-item>

            <el-form-item label="保留天数">
              <el-input-number v-model="form.retentionDays" :min="0" :max="3650" :step="1" />
              <span class="form-tip">超过天数的旧备份自动删除，0 表示永不删除。</span>
            </el-form-item>

            <el-form-item label="最少保留份数">
              <el-input-number v-model="form.minKeep" :min="1" :max="1000" :step="1" />
              <span class="form-tip">无论多旧都至少留这么多份，防止长期停用后备份被清空。</span>
            </el-form-item>

            <el-form-item label="备份目录">
              <el-input v-model="form.dir" placeholder="例如 D:\wms-backups" clearable />
              <span class="form-tip">服务器上的绝对路径。保存时会立即测试能否写入。</span>
            </el-form-item>

            <el-form-item label="离机副本目录">
              <el-input v-model="form.mirrorDir" placeholder="例如 C:\Users\你的用户名\OneDrive\wms-backups（强烈建议配置）" clearable />
              <span class="form-tip">每次备份完成后自动复制一份过去。指向网盘同步目录或移动硬盘，硬盘坏了才有救。</span>
            </el-form-item>

            <el-form-item label="mysqldump 路径">
              <el-input v-model="form.mysqldumpPath" placeholder="留空自动查找，例如 C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe" clearable />
              <span class="form-tip">只有自检提示「找不到 mysqldump」时才需要填。</span>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" icon="Check" :loading="saving" @click="saveSettings">保存设置</el-button>
              <el-button icon="RefreshLeft" @click="loadSettings">重置</el-button>
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>

      <el-table :data="list" border stripe v-loading="loading" empty-text="暂无备份文件">
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="name" label="文件名" min-width="300" />
        <el-table-column label="大小" width="130" align="right">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="备份时间" min-width="210">
          <template #default="{ row }">{{ formatTime(row.lastModified) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="downloadBackup(row)">下载</el-button>
            <el-button link type="danger" @click="removeBackup(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="checkVisible" title="备份环境自检" width="720px">
      <el-table :data="checkResult" border v-loading="checking">
        <el-table-column label="检查项" prop="name" width="220" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.ok" type="success" effect="dark">正常</el-tag>
            <el-tag v-else type="danger" effect="dark">异常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" prop="detail" min-width="320">
          <template #default="{ row }">
            <span style="word-break: break-all">{{ row.detail }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="checkVisible = false">关闭</el-button>
        <el-button type="primary" :loading="checking" @click="runCheck">重新检测</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="DbBackup">
import { ref, computed, getCurrentInstance } from 'vue'
import { saveAs } from 'file-saver'
import {
  listDbBackups,
  getDbBackupStatus,
  getDbBackupSettings,
  saveDbBackupSettings,
  checkDbBackupEnv,
  createDbBackup,
  downloadDbBackup,
  deleteDbBackup
} from '@/api/wms/dbBackup'

const { proxy } = getCurrentInstance()
const list = ref([])
const status = ref({})
const loading = ref(false)
const creating = ref(false)
const saving = ref(false)
const checking = ref(false)
const checkVisible = ref(false)
const checkResult = ref([])
const openedPanels = ref([])

const weekdayOptions = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]

const form = ref({
  enabled: true,
  mode: 'daily',
  intervalHours: 24,
  dailyTime: '03:00',
  weekdays: [1, 2, 3, 4, 5, 6, 7],
  retentionDays: 14,
  minKeep: 3,
  dir: '',
  mirrorDir: '',
  mysqldumpPath: ''
})

const lastRun = computed(() => status.value.lastRun || null)

const lastRunTitle = computed(() => {
  const run = lastRun.value
  if (!run) return ''
  const when = formatTime(run.time)
  return run.success
    ? `上次备份成功（${run.trigger || ''}，${when}）`
    : `上次备份失败（${run.trigger || ''}，${when}）`
})

const autoBackupTitle = computed(() => {
  const s = status.value
  if (!s.enabled) {
    return '自动备份已关闭，目前只能手动备份。'
  }
  const last = s.lastBackupTime ? formatTime(s.lastBackupTime) : '暂无'
  return `自动备份已开启：${describePlan(s)}，保留 ${s.retentionDays} 天（至少 ${s.minKeep} 份）。`
    + ` 上次备份：${last}；下次计划：${s.nextRunTime || '—'}`
})

const planSummary = computed(() => {
  if (!status.value.enabled) return '当前已关闭'
  return describePlan(status.value)
})

function describePlan(s) {
  if (!s || !s.mode) return ''
  if (s.mode === 'daily') {
    const days = (s.weekdays || []).map(d => weekdayOptions.find(o => o.value === d)?.label).filter(Boolean)
    const dayText = days.length === 7 ? '每天' : days.join('、')
    return `${dayText} ${s.dailyTime} 备份`
  }
  return `每隔 ${s.intervalHours} 小时备份一次`
}

async function loadStatus() {
  const res = await getDbBackupStatus()
  status.value = res.data || {}
}

async function loadSettings() {
  const res = await getDbBackupSettings()
  const data = res.data || {}
  form.value = {
    enabled: data.enabled !== false,
    mode: data.mode || 'interval',
    intervalHours: data.intervalHours ?? 24,
    dailyTime: data.dailyTime || '03:00',
    weekdays: data.weekdays?.length ? [...data.weekdays] : [1, 2, 3, 4, 5, 6, 7],
    retentionDays: data.retentionDays ?? 14,
    minKeep: data.minKeep ?? 3,
    dir: data.dir || '',
    mirrorDir: data.mirrorDir || '',
    mysqldumpPath: data.mysqldumpPath || ''
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [res] = await Promise.all([listDbBackups(), loadStatus(), loadSettings()])
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    await saveDbBackupSettings(form.value)
    proxy.$modal.msgSuccess('设置已保存，立即生效')
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function runCheck() {
  checkVisible.value = true
  checking.value = true
  try {
    const res = await checkDbBackupEnv()
    checkResult.value = res.data || []
  } finally {
    checking.value = false
  }
}

async function createBackup() {
  await proxy.$modal.confirm('确认立即导出整个数据库吗？备份期间请不要重复操作。')
  creating.value = true
  try {
    const res = await createDbBackup()
    proxy.$modal.msgSuccess(`备份完成：${res.data?.name || ''}`)
    await loadAll()
  } catch (e) {
    // 失败原因已经由请求层弹出，这里只刷新一次，让页面上的「上次结果」同步变红
    await loadStatus()
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
  await loadAll()
}

function formatSize(size) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

loadAll()
</script>

<style scoped>
.backup-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.backup-actions { flex-shrink: 0; }
.backup-title { font-size: 16px; font-weight: 600; }
.backup-tip { margin-top: 5px; color: #909399; font-size: 13px; }
.backup-status { display: flex; flex-direction: column; gap: 3px; font-size: 13px; word-break: break-all; }
.backup-status-warn { color: #e6a23c; }
.plan-title { font-size: 14px; font-weight: 600; }
.plan-subtitle { margin-left: 12px; color: #909399; font-size: 13px; }
.plan-form { max-width: 760px; padding-top: 6px; }
.form-tip { margin-left: 10px; color: #909399; font-size: 12px; }
</style>
