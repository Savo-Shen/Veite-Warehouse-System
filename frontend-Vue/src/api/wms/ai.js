import request from '@/utils/request'
import { getToken } from '@/utils/auth'

// AI 助手对话（非流式；内部可能多轮调用工具，放宽超时到 60s）
// conversationId 为空则后端新建会话
export function aiChat(message, conversationId) {
  return request({
    url: '/wms/ai/chat',
    method: 'post',
    data: { message, conversationId },
    timeout: 60000
  })
}

/**
 * 流式对话（SSE，用原生 fetch 读流）。
 * handlers: { onMeta, onStatus, onDelta, onDone, onError, signal }
 */
export function aiChatStream(message, conversationId, handlers = {}) {
  const { onMeta, onStatus, onDelta, onDone, onError, signal, mode } = handlers
  const base = import.meta.env.VITE_APP_BASE_API || ''
  return fetch(base + '/wms/ai/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + getToken() },
    body: JSON.stringify({ message, conversationId, mode: mode || 'fast' }),
    signal
  }).then(async (resp) => {
    if (!resp.ok || !resp.body) {
      onError && onError('HTTP ' + resp.status)
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      let idx
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        dispatchSse(buf.slice(0, idx), { onMeta, onStatus, onDelta, onDone, onError })
        buf = buf.slice(idx + 2)
      }
    }
  }).catch((e) => {
    if (e && e.name === 'AbortError') return
    onError && onError((e && e.message) || String(e))
  })
}

function dispatchSse(chunk, h) {
  let event = 'message'
  const dataLines = []
  for (const line of chunk.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5)) // 不裁前导空格，保留 token 原文
  }
  const data = dataLines.join('\n')
  if (event === 'delta') h.onDelta && h.onDelta(data)
  else if (event === 'status') h.onStatus && h.onStatus(data)
  else if (event === 'meta') { try { h.onMeta && h.onMeta(JSON.parse(data)) } catch (e) { /* ignore */ } }
  else if (event === 'done') { try { h.onDone && h.onDone(JSON.parse(data)) } catch (e) { h.onDone && h.onDone({}) } }
  else if (event === 'error') h.onError && h.onError(data)
}

// 我的会话列表
export function listConversations() {
  return request({
    url: '/wms/ai/conversations',
    method: 'get'
  })
}

// 某会话的消息
export function getConversationMessages(id) {
  return request({
    url: `/wms/ai/conversations/${id}/messages`,
    method: 'get'
  })
}

// 删除会话
export function deleteConversation(id) {
  return request({
    url: `/wms/ai/conversations/${id}`,
    method: 'delete'
  })
}

// 执行某条助手消息里的“待确认操作”（库位调整 / 新建往来单位 / 新建商品）
export function executeAiAction(messageId) {
  return request({
    url: `/wms/ai/actions/${messageId}/execute`,
    method: 'post',
    timeout: 30000
  })
}
