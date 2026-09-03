<template>
  <div class="app-container ai-chat" :style="{ '--ai-vh': viewportHeight }">
    <header class="mobile-shell-header only-mobile">
      <div class="type-tabs">
        <button @click="goMobile('price')">查价格</button>
        <button @click="goMobile('receipt')">入库</button>
        <button @click="goMobile('shipment')">出库</button>
        <button class="active">AI助手</button>
      </div>
      <el-dropdown @command="handleMobileCommand">
        <img :src="userStore.avatar" class="avatar" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="desktop">电脑版</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>
    <el-card class="chat-card" body-style="padding:0;">
      <div class="ai-layout">
        <!-- 移动端：点击遮罩关闭会话抽屉 -->
        <div v-if="showSidebar" class="conv-backdrop only-mobile" @click="showSidebar = false"></div>
        <!-- 左侧：我的会话 -->
        <aside class="conv-sidebar" :class="{ open: showSidebar }">
          <el-button class="new-btn" type="primary" icon="Plus" @click="newChat">新对话</el-button>
          <div class="conv-list">
            <div
              v-for="c in conversations"
              :key="c.id"
              class="conv-item"
              :class="{ active: String(c.id) === String(currentId) }"
              @click="openConversation(c)"
            >
              <span class="conv-title">{{ c.title || '新对话' }}</span>
              <el-icon class="conv-del" @click.stop="removeConversation(c)"><Delete /></el-icon>
            </div>
            <div v-if="!conversations.length" class="conv-empty">还没有历史会话</div>
          </div>
        </aside>

        <!-- 右侧：对话区 -->
        <section class="chat-main">
          <div class="chat-header">
            <div class="chat-title">
              <small class="only-mobile">智能建单</small>
              <span class="title">用一句话处理仓库任务</span>
              <small class="only-mobile">查库存、查价格、生成出入库草稿</small>
            </div>
            <span class="hint hide-mobile">试试：「气管多少钱」「这个月出了多少钱」「把 CK08054357 复制一张，单价改成进价」「盘点：SDA32*20 实际 40 个」</span>
            <div class="mobile-header-actions only-mobile">
              <el-button class="header-action" icon="ChatDotRound" @click="newChat">新对话</el-button>
              <el-button class="header-action primary" icon="Menu" @click="showSidebar = !showSidebar">历史</el-button>
            </div>
          </div>

          <div ref="msgListRef" class="msg-list" v-loading="historyLoading">
            <div v-if="!messages.length" class="empty">
              <div class="empty-title">今天要处理什么？</div>
              <p>我可以查价格、库存、往来单位、单据和出入库历史；也能生成出入库草稿、复制已有单据、改单价或改数量，都由你核对后再保存。</p>
              <div class="quick-prompts">
                <button v-for="item in quickPrompts" :key="item" type="button" @click="usePrompt(item)">
                  {{ item }}
                </button>
              </div>
            </div>

            <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
              <div class="bubble">
                <span v-if="m.streaming && !m.content" class="typing">{{ m.statusText || ('正在思考… ' + thinkingText + 's') }}</span>
                <div v-else-if="m.role === 'assistant'" class="text md">
                  <span v-html="renderMarkdown(m.content)"></span><span v-if="m.streaming" class="caret">▌</span>
                  <div v-if="m.streaming && m.statusText" class="status-line">{{ m.statusText }}</div>
                </div>
                <div v-else class="text"><span v-text="m.content"></span></div>
                <el-collapse v-if="m.toolTrace && m.toolTrace.length" class="trace">
                  <el-collapse-item :title="`AI 调用了 ${m.toolTrace.length} 个工具（点击查看）`">
                    <div v-for="(t, ti) in m.toolTrace" :key="ti" class="trace-item">
                      <div><b>{{ t.tool }}</b> 入参：{{ t.arguments }}</div>
                      <pre>{{ t.result }}</pre>
                    </div>
                  </el-collapse-item>
                </el-collapse>
                <div v-if="isOrderDraft(m.draft)" class="draft-action">
                  <el-button type="success" icon="DocumentAdd" @click="goCreateOrder(m.draft)">
                    {{ draftActionText(m.draft) }}
                  </el-button>
                </div>
                <div v-else-if="isActionDraft(m.draft)" class="action-card" :class="{ done: m.draft.executed }">
                  <div class="action-title">{{ m.draft.title || '待确认操作' }}</div>
                  <div v-if="m.draft.summary" class="action-summary">
                    <span v-for="(v, k) in m.draft.summary" :key="k" v-show="v">{{ k }}：{{ v }}</span>
                  </div>
                  <div v-if="m.draft.lines && m.draft.lines.length" class="md-table action-lines">
                    <table>
                      <thead><tr><th v-for="k in actionColumns(m.draft.lines)" :key="k">{{ k }}</th></tr></thead>
                      <tbody>
                        <tr v-for="(row, ri) in m.draft.lines" :key="ri">
                          <td v-for="k in actionColumns(m.draft.lines)" :key="k">{{ row[k] ?? '' }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <div v-if="m.draft.warnings && m.draft.warnings.length" class="action-warn">
                    <div v-for="(w, wi) in m.draft.warnings" :key="wi">⚠ {{ w }}</div>
                  </div>
                  <div class="action-foot">
                    <template v-if="m.draft.executed">
                      <el-tag type="success" effect="plain">已执行</el-tag>
                      <span class="action-result">{{ m.draft.result }}</span>
                    </template>
                    <el-button v-else type="warning" icon="Check" :loading="m.executing" :disabled="!m.id" @click="runAction(m)">
                      确认执行
                    </el-button>
                  </div>
                </div>
                <div class="msg-meta">
                  <span v-if="m.elapsedSec" class="elapsed">⏱ 用时 {{ m.elapsedSec }}s<template v-if="m.model">（{{ modelLabel(m.model) }}）</template></span>
                  <el-button class="copy-btn" link size="small" icon="CopyDocument" @click="copyText(m.content)">复制</el-button>
                </div>
              </div>
            </div>

          </div>

          <div class="input-bar">
            <el-input
              ref="inputRef"
              v-model="input"
              type="textarea"
              :rows="2"
              resize="none"
              :placeholder="recognizing ? '正在聆听，说完会自动停止…' : inputPlaceholder"
              @keydown="onKeydown"
            />
            <div class="input-actions">
              <el-radio-group v-model="aiMode" size="small" class="mode-switch" :title="'快：' + fastHint + '；强：' + strongHint">
                <el-radio-button label="fast">快</el-radio-button>
                <el-radio-button label="strong">强</el-radio-button>
              </el-radio-group>
              <el-button
                v-if="speechSupported"
                class="mic-btn"
                :class="{ recording: recognizing }"
                :type="recognizing ? 'danger' : 'default'"
                icon="Microphone"
                :title="recognizing ? '点击停止' : '语音输入'"
                @click="toggleVoice"
              >语音</el-button>
              <el-button v-if="loading" class="send-btn" type="danger" plain @click="stopStream">停止</el-button>
              <el-button v-else class="send-btn" type="primary" @click="send">发送</el-button>
            </div>
          </div>
        </section>
      </div>
    </el-card>
  </div>
</template>

<script setup name="WmsAiAssistant">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiChatStream, listConversations, getConversationMessages, deleteConversation, executeAiAction } from '@/api/wms/ai'
import useUserStore from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()

const messages = ref([])
const input = ref('')
const loading = ref(false)
const historyLoading = ref(false)
const msgListRef = ref(null)
const inputRef = ref(null)
const viewportHeight = ref('100dvh')
const isMobileViewport = ref(false)
const inputPlaceholder = computed(() => {
  if (isMobileViewport.value) return '输入仓库任务，或点语音说一句'
  return '输入问题，Enter 发送，Shift+Enter 换行，↑ / ↓ 调出历史'
})

const conversations = ref([])
const currentId = ref(null)

/* ---------- 快 / 强 双模型 ---------- */
const fastHint = '日常查询、建单，几秒出结果'
const strongHint = '多步推理、长清单、复杂改单，更稳但更慢'
const readMode = () => { try { return localStorage.getItem('wms_ai_mode') === 'strong' ? 'strong' : 'fast' } catch (e) { return 'fast' } }
const aiMode = ref(readMode())
watch(aiMode, (v) => { try { localStorage.setItem('wms_ai_mode', v) } catch (e) { /* ignore */ } })
const modelLabel = (model) => {
  if (!model) return ''
  const m = String(model)
  if (/pro|strong|max/i.test(m)) return '强 · ' + m
  return '快 · ' + m
}
const showSidebar = ref(false)   // 移动端会话抽屉
const goMobile = (type) => router.push({ path: '/mobile', query: { type } })
const handleMobileCommand = (command) => {
  if (command === 'desktop') {
    router.push('/index?desktop=1')
    return
  }
  if (command === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { confirmButtonText: '退出', cancelButtonText: '取消', type: 'warning' })
      .then(() => userStore.logOut())
      .then(() => { location.href = import.meta.env.VITE_APP_CONTEXT_PATH + 'login' })
      .catch(() => {})
  }
}
const quickPrompts = [
  '查一下PU管库存',
  '库存低于 10 的商品有哪些',
  '把 CK08054357 复制一张，单价改成进价',
  '这个月哪个客户买得最多',
  '接一根四分两层 1米2，两头 22×1.5 A型芯，多少钱',
  '给约克报个价：PC08-02 接头 200 个'
]

const usePrompt = (text) => {
  input.value = text
  nextTick(() => inputRef.value?.focus?.())
}

const scrollToBottom = () => {
  nextTick(() => {
    if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight
  })
}

const updateViewportHeight = () => {
  if (typeof window === 'undefined') return
  const visualHeight = window.visualViewport?.height
  viewportHeight.value = `${Math.round(visualHeight || window.innerHeight)}px`
  isMobileViewport.value = window.innerWidth <= 768
}

/* ---------- 会话列表 ---------- */
const loadConversations = async () => {
  try {
    const res = await listConversations()
    conversations.value = res.data || []
  } catch (e) { /* ignore */ }
}

const newChat = () => {
  currentId.value = null
  messages.value = []
  input.value = ''
  historyIndex.value = -1
  showSidebar.value = false
  nextTick(() => inputRef.value?.focus?.())
}

const openConversation = async (c) => {
  if (loading.value) return
  showSidebar.value = false
  currentId.value = c.id
  historyLoading.value = true
  messages.value = []
  try {
    const res = await getConversationMessages(c.id)
    messages.value = (res.data || []).map(mapPersistedMessage)
  } catch (e) {
    ElMessage.error('加载会话失败')
  } finally {
    historyLoading.value = false
    scrollToBottom()
  }
}

const removeConversation = async (c) => {
  try {
    await ElMessageBox.confirm(`删除会话「${c.title || '新对话'}」？`, '提示', { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteConversation(c.id)
    conversations.value = conversations.value.filter(x => String(x.id) !== String(c.id))
    if (String(currentId.value) === String(c.id)) newChat()
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

const mapPersistedMessage = (m) => {
  const safeParse = (s) => {
    if (s == null) return null
    try { return typeof s === 'string' ? JSON.parse(s) : s } catch (e) { return null }
  }
  const draft = safeParse(m.draft)
  return {
    id: m.id,
    role: m.role,
    content: m.content,
    toolTrace: safeParse(m.toolTrace) || [],
    draft: isOrderDraft(draft) || isActionDraft(draft) ? draft : null,
    elapsedSec: m.elapsedMs ? (m.elapsedMs / 1000).toFixed(1) : undefined
  }
}

/* ---------- 思考计时器 ---------- */
let timer = null
let startTs = 0
const thinkingMs = ref(0)
const thinkingText = computed(() => (thinkingMs.value / 1000).toFixed(1))
const startThinking = () => {
  startTs = performance.now()
  thinkingMs.value = 0
  timer = window.setInterval(() => { thinkingMs.value = performance.now() - startTs }, 100)
}
const stopThinking = () => { if (timer) { clearInterval(timer); timer = null } }
const elapsedSec = () => ((performance.now() - startTs) / 1000).toFixed(1)
onUnmounted(stopThinking)

/* ---------- Markdown（表格/加粗/列表/代码），先转义再渲染 ---------- */
const escapeHtml = (s) => String(s ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
const inlineMd = (s) => s
  .replace(/`([^`]+)`/g, '<code>$1</code>')
  .replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
  .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<i>$2</i>')
const renderMarkdown = (text) => {
  const lines = escapeHtml(text).split('\n')
  const out = []
  let i = 0
  const isTableRow = (l) => /^\s*\|.*\|\s*$/.test(l)
  const isSepRow = (l) => /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/.test(l)
  while (i < lines.length) {
    const line = lines[i]
    if (/^\s*```/.test(line)) {
      const buf = []
      i++
      while (i < lines.length && !/^\s*```/.test(lines[i])) buf.push(lines[i++])
      i++
      out.push('<pre><code>' + buf.join('\n') + '</code></pre>')
      continue
    }
    if (isTableRow(line) && i + 1 < lines.length && isSepRow(lines[i + 1])) {
      const cells = (l) => l.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(c => inlineMd(c.trim()))
      let html = '<div class="md-table"><table><thead><tr>' + cells(line).map(c => `<th>${c}</th>`).join('') + '</tr></thead><tbody>'
      i += 2
      while (i < lines.length && isTableRow(lines[i])) {
        html += '<tr>' + cells(lines[i]).map(c => `<td>${c}</td>`).join('') + '</tr>'
        i++
      }
      out.push(html + '</tbody></table></div>')
      continue
    }
    const h = /^\s*(#{1,6})\s+(.*)$/.exec(line)
    if (h) { out.push(`<div class="md-h">${inlineMd(h[2])}</div>`); i++; continue }
    if (/^\s*([-*•]|\d+[.、)])\s+/.test(line)) {
      const ordered = /^\s*\d+[.、)]\s+/.test(line)
      const items = []
      while (i < lines.length && /^\s*([-*•]|\d+[.、)])\s+/.test(lines[i])) {
        items.push('<li>' + inlineMd(lines[i].replace(/^\s*([-*•]|\d+[.、)])\s+/, '')) + '</li>')
        i++
      }
      out.push((ordered ? '<ol>' : '<ul>') + items.join('') + (ordered ? '</ol>' : '</ul>'))
      continue
    }
    if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) { out.push('<hr>'); i++; continue }
    out.push(line.trim() === '' ? '<div class="md-gap"></div>' : `<div>${inlineMd(line)}</div>`)
    i++
  }
  return out.join('')
}

/* ---------- 复制 ---------- */
const copyText = async (text) => {
  const t = text == null ? '' : String(text)
  try {
    await navigator.clipboard.writeText(t)
    ElMessage.success('已复制')
  } catch (e) {
    const ta = document.createElement('textarea')
    ta.value = t; ta.style.position = 'fixed'; ta.style.opacity = '0'
    document.body.appendChild(ta); ta.select()
    try { document.execCommand('copy'); ElMessage.success('已复制') } catch (err) { ElMessage.error('复制失败') }
    document.body.removeChild(ta)
  }
}

/* ---------- 输入历史（↑/↓） ---------- */
const history = ref([])
const historyIndex = ref(-1)
const draftBeforeHistory = ref('')
const recallPrev = () => {
  if (!history.value.length) return false
  if (historyIndex.value === -1) { draftBeforeHistory.value = input.value; historyIndex.value = history.value.length - 1 }
  else if (historyIndex.value > 0) historyIndex.value -= 1
  input.value = history.value[historyIndex.value]
  return true
}
const recallNext = () => {
  if (historyIndex.value === -1) return false
  if (historyIndex.value < history.value.length - 1) { historyIndex.value += 1; input.value = history.value[historyIndex.value] }
  else { historyIndex.value = -1; input.value = draftBeforeHistory.value }
  return true
}
const setCaretEnd = (ta) => { if (ta) ta.selectionStart = ta.selectionEnd = ta.value.length }
const onKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); return }
  const ta = e.target
  if (e.key === 'ArrowUp') {
    if (ta.selectionStart === 0 && ta.selectionEnd === 0 && recallPrev()) { e.preventDefault(); nextTick(() => setCaretEnd(ta)) }
  } else if (e.key === 'ArrowDown') {
    const len = (input.value || '').length
    if (ta.selectionStart === len && ta.selectionEnd === len && recallNext()) { e.preventDefault(); nextTick(() => setCaretEnd(ta)) }
  }
}

/* ---------- 语音输入（浏览器 Web Speech API，中文） ---------- */
const SpeechRec = typeof window !== 'undefined' ? (window.SpeechRecognition || window.webkitSpeechRecognition) : null
const speechSupported = !!SpeechRec
const recognizing = ref(false)
let recognition = null
let voiceBase = ''   // 开始识别时输入框已有的文字，识别结果追加在其后

const ensureRecognition = () => {
  if (recognition || !SpeechRec) return recognition
  recognition = new SpeechRec()
  recognition.lang = 'zh-CN'
  recognition.interimResults = true   // 实时显示
  recognition.continuous = false      // 说完停顿即结束
  recognition.maxAlternatives = 1
  recognition.onresult = (event) => {
    let finalText = ''
    let interim = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const tr = event.results[i][0].transcript
      if (event.results[i].isFinal) finalText += tr
      else interim += tr
    }
    if (finalText) voiceBase += finalText
    input.value = voiceBase + interim
  }
  recognition.onerror = (e) => {
    recognizing.value = false
    if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
      ElMessage.error('麦克风权限被拒绝，请在浏览器/系统设置中允许')
    } else if (e.error !== 'aborted' && e.error !== 'no-speech') {
      ElMessage.error('语音识别出错：' + e.error)
    }
  }
  recognition.onend = () => { recognizing.value = false }
  return recognition
}

const toggleVoice = () => {
  if (!speechSupported) {
    ElMessage.warning('当前环境不支持语音输入（建议用手机 Chrome，并在 https 域名下使用）')
    return
  }
  const r = ensureRecognition()
  if (!r) return
  if (recognizing.value) { try { r.stop() } catch (e) { /* ignore */ } return }
  voiceBase = input.value ? input.value.replace(/\s*$/, '') + ' ' : ''
  try {
    r.start()
    recognizing.value = true
  } catch (e) {
    recognizing.value = false
  }
}

onUnmounted(() => { try { recognition && recognition.abort() } catch (e) { /* ignore */ } })

/* ---------- 草稿 ---------- */
const ORDER_DRAFTS = {
  shipment: { name: '出库单', path: '/shipmentOrderEdit', key: 'wms_ai_shipment_draft', partner: '客户' },
  receipt: { name: '入库单', path: '/receiptOrderEdit', key: 'wms_ai_receipt_draft', partner: '供应商' },
  movement: { name: '移库单', path: '/movementOrderEdit', key: 'wms_ai_movement_draft' },
  check: { name: '盘点单', path: '/checkOrderEdit', key: 'wms_ai_check_draft' }
}
const isOrderDraft = (draft) => !!(draft && ORDER_DRAFTS[draft.type])
const isActionDraft = (draft) => !!(draft && draft.type === 'action')
const draftActionText = (draft) => {
  const cfg = ORDER_DRAFTS[draft.type]
  const count = (draft.details || []).length
  if (draft.type === 'movement') {
    return `去确认并创建移库单（${count} 项，${draft.sourceWarehouseName || ''} → ${draft.targetWarehouseName || ''}）`
  }
  if (draft.type === 'check') {
    return `去核对并保存盘点单（${count} 项，${draft.warehouseName || ''}）`
  }
  const merchantText = draft.merchantName ? `，${cfg.partner}：${draft.merchantName}` : ''
  if (draft.mode === 'edit' && draft.orderId) {
    return `去核对并保存对${cfg.name} ${draft.orderNo || ''} 的修改（${count} 项${merchantText}）`
  }
  const copied = draft.sourceOrderNo ? `，复制自 ${draft.sourceOrderNo}` : ''
  return `去确认并创建${cfg.name}（${count} 项${merchantText}${copied}）`
}
const goCreateOrder = (draft) => {
  const cfg = ORDER_DRAFTS[draft.type]
  if (!cfg) return
  sessionStorage.setItem(cfg.key, JSON.stringify(draft))
  const query = { fromAi: '1' }
  // 改单草稿：打开原单据的编辑页，编辑页加载完再把改动叠上去
  if (draft.mode === 'edit' && draft.orderId) query.id = String(draft.orderId)
  router.push({ path: cfg.path, query })
}

/* ---------- 待确认操作（建档 / 库位调整） ---------- */
const actionColumns = (lines) => {
  const keys = []
  ;(lines || []).forEach(row => Object.keys(row || {}).forEach(k => { if (k !== 'skuId' && !keys.includes(k)) keys.push(k) }))
  return keys
}
const runAction = async (m) => {
  if (!m.id || m.executing) return
  try {
    await ElMessageBox.confirm(`确定执行「${m.draft.title || '该操作'}」？执行后会直接写入系统。`, '确认执行', { type: 'warning', confirmButtonText: '执行', cancelButtonText: '再看看' })
  } catch (e) { return }
  m.executing = true
  try {
    const res = await executeAiAction(m.id)
    if (res.data && res.data.draft) m.draft = res.data.draft
    else m.draft = { ...m.draft, executed: true, result: res.data && res.data.result }
    ElMessage.success(res.data && res.data.result ? res.data.result : '已执行')
  } catch (e) {
    ElMessage.error((e && e.message) || '执行失败')
  } finally {
    m.executing = false
  }
}
const parseDraftFromTrace = (toolTrace = []) => {
  const draftTrace = [...toolTrace].reverse().find(t => /_draft$/.test(t.tool || ''))
  if (!draftTrace?.result) return null
  try {
    const parsed = typeof draftTrace.result === 'string' ? JSON.parse(draftTrace.result) : draftTrace.result
    return isOrderDraft(parsed) || isActionDraft(parsed) ? parsed : null
  } catch (e) { return null }
}

/* ---------- 发送 ---------- */
const send = async () => {
  if (recognizing.value && recognition) { try { recognition.stop() } catch (e) { /* ignore */ } }
  const text = input.value.trim()
  if (!text || loading.value) return
  const isNew = !currentId.value
  messages.value.push({ role: 'user', content: text })
  history.value.push(text)
  historyIndex.value = -1
  draftBeforeHistory.value = ''
  input.value = ''
  loading.value = true
  startThinking()
  // 流式占位的助手消息
  messages.value.push({ role: 'assistant', content: '', toolTrace: [], draft: null, streaming: true, statusText: '' })
  const idx = messages.value.length - 1
  scrollToBottom()

  const finish = () => { stopThinking(); loading.value = false; abortCtrl = null }
  abortCtrl = typeof AbortController !== 'undefined' ? new AbortController() : null

  await aiChatStream(text, currentId.value, {
    signal: abortCtrl ? abortCtrl.signal : undefined,
    mode: aiMode.value,
    onMeta: (d) => { if (d && d.conversationId) currentId.value = d.conversationId },
    onStatus: (s) => { messages.value[idx].statusText = s; scrollToBottom() },
    onDelta: (t) => {
      messages.value[idx].content += t
      messages.value[idx].statusText = ''
      scrollToBottom()
    },
    onDone: (d) => {
      const m = messages.value[idx]
      if (d && d.reply) m.content = d.reply
      m.toolTrace = (d && d.toolTrace) || m.toolTrace || []
      m.draft = (d && d.draft) || parseDraftFromTrace(m.toolTrace)
      m.id = d && d.messageId
      m.model = d && d.model
      m.elapsedSec = elapsedSec()
      m.streaming = false
      m.statusText = ''
      finish()
      if (isNew) loadConversations()
      scrollToBottom()
    },
    onError: (e) => {
      const m = messages.value[idx]
      m.content = (m.content ? m.content + '\n' : '') + '出错了：' + e
      m.streaming = false
      m.statusText = ''
      finish()
      scrollToBottom()
    }
  })

  // 兜底：流意外结束（既无 done 也无 error）或被用户中断时复位状态
  if (loading.value) {
    const m = messages.value[idx]
    if (m) {
      m.streaming = false
      m.statusText = ''
      if (!m.content) m.content = '（已停止）'
    }
    finish()
  }
}

let abortCtrl = null
const stopStream = () => {
  if (abortCtrl) { try { abortCtrl.abort() } catch (e) { /* ignore */ } }
}

onMounted(() => {
  updateViewportHeight()
  window.visualViewport?.addEventListener('resize', updateViewportHeight)
  window.visualViewport?.addEventListener('scroll', updateViewportHeight)
  window.addEventListener('resize', updateViewportHeight)
  loadConversations()
})
onUnmounted(() => {
  window.visualViewport?.removeEventListener('resize', updateViewportHeight)
  window.visualViewport?.removeEventListener('scroll', updateViewportHeight)
  window.removeEventListener('resize', updateViewportHeight)
})
</script>

<style scoped lang="scss">
.ai-chat { display: flex; justify-content: center; }
.chat-card { width: 100%; max-width: 1040px; }
.ai-layout { display: flex; height: 72vh; position: relative; }

/* 侧栏 */
.conv-sidebar { width: 230px; flex-shrink: 0; border-right: 1px solid #ebeef5; display: flex; flex-direction: column; padding: 12px; }
.new-btn { width: 100%; margin-bottom: 10px; }
.conv-list { flex: 1; overflow-y: auto; }
.conv-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 10px; border-radius: 8px; cursor: pointer; margin-bottom: 4px; color: #555;
  &:hover { background: #f5f7fa; }
  &.active { background: #ecf5ff; color: #409eff; }
}
.conv-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.conv-del { opacity: 0; flex-shrink: 0; margin-left: 6px; }
.conv-item:hover .conv-del { opacity: .6; }
.conv-del:hover { opacity: 1; color: #f56c6c; }
.conv-empty { color: #c0c4cc; font-size: 12px; text-align: center; margin-top: 24px; }

/* 对话区 */
.chat-main { flex: 1; display: flex; flex-direction: column; padding: 12px 16px; min-width: 0; }
.chat-header { display: flex; align-items: baseline; gap: 12px; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0;
  .hint { font-size: 12px; color: #999; }
}
.msg-list { flex: 1; overflow-y: auto; padding: 12px 4px; }
.empty { color: #999; text-align: center; margin-top: 40px; }
.msg-row { display: flex; margin-bottom: 12px;
  &.user { justify-content: flex-end; }
  &.assistant { justify-content: flex-start; }
}
.bubble { position: relative; max-width: 82%; padding: 10px 14px; border-radius: 10px; background: #f4f4f5; }
.msg-row.user .bubble { background: #ecf5ff; }
.text { white-space: pre-wrap; word-break: break-word; }
.text.md { white-space: normal; line-height: 1.6; }
.text.md :deep(b) { font-weight: 600; }
.text.md :deep(code) { background: #eef1f6; padding: 1px 5px; border-radius: 4px; font-size: 12.5px; }
.text.md :deep(pre) { background: #f4f5f7; padding: 8px 10px; border-radius: 6px; overflow-x: auto; font-size: 12.5px; white-space: pre; }
.text.md :deep(ul), .text.md :deep(ol) { margin: 4px 0 4px 20px; padding: 0; }
.text.md :deep(li) { margin: 2px 0; }
.text.md :deep(.md-h) { font-weight: 600; margin: 8px 0 4px; }
.text.md :deep(.md-gap) { height: 8px; }
.text.md :deep(hr) { border: 0; border-top: 1px solid #e5e7eb; margin: 8px 0; }
.text.md :deep(.md-table) { overflow-x: auto; margin: 6px 0; }
.text.md :deep(table) { border-collapse: collapse; font-size: 13px; min-width: 60%; }
.text.md :deep(th), .text.md :deep(td) { border: 1px solid #e0e3e9; padding: 4px 10px; text-align: left; white-space: nowrap; }
.text.md :deep(th) { background: #f3f5f9; font-weight: 600; }
.status-line { margin-top: 6px; font-size: 12px; color: #909399; }
.mode-switch { margin-right: 4px; }
.action-card { margin-top: 10px; padding: 10px 12px; border: 1px solid #f3d19e; background: #fdf6ec; border-radius: 8px; }
.action-card.done { border-color: #c2e7b0; background: #f0f9eb; }
.action-title { font-weight: 600; margin-bottom: 6px; }
.action-summary { font-size: 13px; color: #606266; margin-bottom: 6px; }
.action-summary span { margin-right: 12px; }
.action-lines { font-size: 13px; }
.action-lines table { border-collapse: collapse; }
.action-lines th, .action-lines td { border: 1px solid #e0e3e9; padding: 3px 8px; white-space: nowrap; }
.action-lines th { background: #fff; }
.action-warn { margin-top: 6px; font-size: 12px; color: #e6a23c; }
.action-foot { margin-top: 8px; display: flex; align-items: center; gap: 8px; }
.action-result { font-size: 13px; color: #67c23a; }
.typing { color: #909399; font-variant-numeric: tabular-nums; }
.caret { color: #409eff; animation: blink 1s steps(1) infinite; margin-left: 1px; }
@keyframes blink { 50% { opacity: 0; } }
.trace { margin-top: 8px; }
.draft-action { margin-top: 10px; }
.trace-item pre { white-space: pre-wrap; word-break: break-word; background: #fafafa; padding: 6px; border-radius: 6px; font-size: 12px; }
.msg-meta {
  display: flex; align-items: center; gap: 10px; margin-top: 6px; min-height: 20px;
  opacity: 0; transition: opacity .15s;
  .elapsed { font-size: 12px; color: #b0b3b8; }
  .copy-btn { font-size: 12px; padding: 0; height: auto; }
}
.msg-row:hover .msg-meta { opacity: 1; }
.input-bar { display: flex; gap: 8px; align-items: flex-end; padding-top: 8px; }
.input-bar .el-textarea { flex: 1; }
.input-actions { display: flex; gap: 8px; align-items: center; }

/* 语音按钮 */
.mic-btn { flex-shrink: 0; align-self: stretch; height: auto; }
.mic-btn.recording {
  animation: micPulse 1.2s ease-in-out infinite;
}
@keyframes micPulse { 0%,100% { box-shadow: 0 0 0 0 rgba(245,108,108,.5); } 50% { box-shadow: 0 0 0 8px rgba(245,108,108,0); } }

/* 响应式开关 */
.only-mobile { display: none; }
.chat-header .title { font-weight: 600; }

/* ---------- 移动端 ---------- */
@media (max-width: 768px) {
  .ai-chat {
    display: block;
    height: var(--ai-vh, 100dvh);
    padding: 0;
    overflow: hidden;
    background: #f3f6fa;
  }

  .mobile-shell-header {
    position: sticky;
    z-index: 30;
    top: 0;
    display: flex !important;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    height: calc(58px + env(safe-area-inset-top));
    padding: env(safe-area-inset-top) 14px 0;
    background: rgba(255,255,255,.96);
    border-bottom: 1px solid #e8edf3;
    backdrop-filter: blur(12px);
  }

  .type-tabs {
    display: flex;
    flex: 1;
    gap: 4px;
    min-width: 0;
    padding: 4px;
    background: #eef3f8;
    border-radius: 12px;
  }

  .type-tabs button {
    flex: 1;
    min-width: 0;
    padding: 8px 7px;
    color: #667085;
    white-space: nowrap;
    background: transparent;
    border: 0;
    border-radius: 9px;
    font-size: 14px;
    font-weight: 700;
  }

  .type-tabs button.active {
    color: #fff;
    background: var(--el-color-primary);
    box-shadow: 0 4px 12px rgba(64,158,255,.28);
  }

  .avatar {
    flex: 0 0 auto;
    width: 38px;
    height: 38px;
    object-fit: cover;
    border-radius: 50%;
  }

  .chat-card {
    max-width: 100%;
    height: calc(var(--ai-vh, 100dvh) - 58px - env(safe-area-inset-top));
    min-height: 0;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .chat-card :deep(.el-card__body) {
    height: calc(var(--ai-vh, 100dvh) - 58px - env(safe-area-inset-top));
    padding: 0 !important;
  }

  .ai-layout {
    height: calc(var(--ai-vh, 100dvh) - 58px - env(safe-area-inset-top));
    background: #f3f6fa;
    overflow: hidden;
  }

  .conv-sidebar {
    position: absolute;
    right: 10px;
    bottom: 10px;
    left: 0;
    top: auto;
    z-index: 20;
    width: auto;
    max-width: none;
    max-height: min(68dvh, 520px);
    margin-left: 10px;
    padding: 14px 12px calc(14px + env(safe-area-inset-bottom));
    background: #fff;
    border: 1px solid #e8edf3;
    border-radius: 18px 18px 0 0;
    transform: translateY(calc(100% + 18px));
    transition: transform .22s ease;
    box-shadow: 0 -10px 28px rgba(31, 45, 61, .18);
  }

  .new-btn {
    min-height: 44px;
    border-radius: 12px;
    font-size: 15px;
    font-weight: 800;
  }

  .conv-item {
    min-height: 44px;
    padding: 10px 12px;
    border-radius: 10px;
  }

  .conv-del {
    opacity: .55;
  }

  .conv-sidebar.open { transform: translateY(0); }
  .conv-backdrop { position: absolute; inset: 0; z-index: 15; background: rgba(0, 0, 0, .35); }
  .only-mobile { display: inline-flex; }
  .hide-mobile { display: none; }

  .chat-main {
    position: relative;
    height: calc(var(--ai-vh, 100dvh) - 58px - env(safe-area-inset-top));
    overflow: hidden;
    padding: 0;
    background: #f3f6fa;
  }

  .chat-header {
    z-index: 10;
    align-items: center;
    gap: 10px;
    margin: 10px 10px 0;
    padding: 10px 11px;
    background: #fff;
    border: 1px solid #e8edf3;
    border-radius: 13px;
    box-shadow: 0 3px 14px rgba(31, 45, 61, .055);
  }

  .header-action {
    flex: 0 0 auto;
    min-width: 68px;
    height: 34px;
    padding: 0 9px;
    color: #2563eb;
    background: #f7f9fc;
    border-color: #bfdbfe;
    border-radius: 12px;
    font-weight: 800;
  }

  .header-action.primary {
    color: #fff;
    background: #2563eb;
    border-color: #2563eb;
    box-shadow: 0 5px 14px rgba(37, 99, 235, .22);
  }

  .mobile-header-actions {
    display: flex !important;
    flex: 0 0 auto;
    gap: 8px;
  }

  .mobile-header-actions .el-button {
    margin-left: 0;
  }

  .chat-title {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  .chat-title small:first-child {
    color: var(--el-color-primary);
    font-size: 12px;
    font-weight: 800;
  }

  .chat-header .title {
    overflow: hidden;
    color: #111827;
    font-size: 18px;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .chat-title small:last-child {
    overflow: hidden;
    color: #98a2b3;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .msg-list {
    flex: 1;
    min-height: 0;
    padding: 10px 10px 138px;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }

  .empty {
    margin: 0;
    padding: 14px 12px;
    color: #667085;
    text-align: left;
    background: #fff;
    border: 1px solid #e8edf3;
    border-radius: 13px;
    box-shadow: 0 3px 14px rgba(31,45,61,.05);
  }

  .empty-title {
    color: #111827;
    font-size: 18px;
    font-weight: 800;
  }

  .empty p {
    margin: 6px 0 12px;
    line-height: 1.5;
  }

  .quick-prompts {
    display: grid;
    gap: 7px;
  }

  .quick-prompts button {
    min-height: 38px;
    padding: 8px 10px;
    color: #2563eb;
    text-align: left;
    background: #eff6ff;
    border: 1px solid #bfdbfe;
    border-radius: 10px;
    font-weight: 700;
  }

  .msg-row {
    margin-bottom: 12px;
  }

  .bubble {
    max-width: 92%;
    padding: 10px 12px;
    border-radius: 14px;
    background: #fff;
    box-shadow: 0 3px 12px rgba(31,45,61,.05);
  }

  .msg-row.user .bubble {
    color: #fff;
    background: #2563eb;
    border-bottom-right-radius: 6px;
  }

  .msg-row.assistant .bubble {
    border-bottom-left-radius: 6px;
  }

  .msg-meta {
    opacity: 1;
  }

  .msg-row.user .msg-meta,
  .msg-row.user .copy-btn {
    color: rgba(255,255,255,.78);
  }

  .trace {
    margin-top: 8px;
    overflow: hidden;
    border-radius: 10px;
  }

  .trace :deep(.el-collapse-item__header) {
    height: auto;
    min-height: 38px;
    padding: 0 8px;
    line-height: 1.35;
  }

  .draft-action .el-button {
    width: 100%;
    min-height: 40px;
    height: auto;
    padding: 9px 12px;
    white-space: normal;
    line-height: 1.35;
  }

  .input-bar {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 12;
    display: grid;
    grid-template-columns: 1fr;
    gap: 9px;
    align-items: stretch;
    margin: 0;
    padding: 10px 10px 12px;
    background: rgba(255,255,255,.96);
    border: 1px solid #e8edf3;
    border-bottom: 0;
    border-radius: 15px 15px 0 0;
    box-shadow: 0 -7px 20px rgba(31,45,61,.1);
    backdrop-filter: blur(12px);
  }

  .input-bar .el-textarea {
    width: 100%;
  }

  .input-bar :deep(.el-textarea__inner) {
    min-height: 58px !important;
    max-height: 118px;
    padding: 11px 13px;
    color: #111827;
    background: #f7f9fc;
    border-color: #dbe5f0;
    border-radius: 13px;
    font-size: 15px;
    line-height: 1.45;
  }

  .input-bar :deep(.el-textarea__inner:hover) {
    background: #f7f9fc;
    border-color: #dbe5f0;
  }

  .input-bar :deep(.el-textarea__inner:focus) {
    background: #fff;
    border-color: #409eff;
  }

  .input-actions {
    display: grid;
    grid-template-columns: minmax(82px, .42fr) 1fr;
    gap: 9px;
  }

  .input-actions .el-button {
    margin-left: 0 !important;
  }

  .input-bar .send-btn {
    width: 100%;
    min-height: 46px;
    padding: 0 13px;
    border-radius: 12px;
    font-size: 16px;
    font-weight: 800;
    box-shadow: 0 6px 16px rgba(64, 158, 255, .28);
  }

  .mic-btn {
    width: 100%;
    min-height: 46px;
    color: #475467;
    background: #f7f9fc;
    border-color: #dbe5f0;
    border-radius: 12px;
    font-weight: 800;
  }

  .mic-btn:hover {
    color: #475467;
    background: #f7f9fc;
    border-color: #dbe5f0;
  }

  .mic-btn:active {
    color: #2563eb;
    background: #eff6ff;
    border-color: #bfdbfe;
  }

  .mic-btn.recording {
    color: #fff;
  }
}
</style>
