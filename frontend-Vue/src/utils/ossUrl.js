/**
 * 归一化 OSS 文件地址。
 *
 * 数据库存储模式下，后端返回的是根相对路径（如 /system/oss/blob/123），
 * 需要拼上接口前缀（VITE_APP_BASE_API，dev=/dev-api、prod=/prod-api）才能被
 * 浏览器 <img>/<a> 直接加载。对象存储模式返回的是绝对 URL（http…），原样返回。
 */
export function normalizeOssUrl(url) {
  if (!url || typeof url !== 'string') return url
  // 绝对地址 / blob: / data: 直接返回
  if (/^(https?:)?\/\//i.test(url) || url.startsWith('blob:') || url.startsWith('data:')) {
    return url
  }
  if (url.startsWith('/')) {
    const base = import.meta.env.VITE_APP_BASE_API || ''
    // 避免重复拼接前缀
    if (base && url.startsWith(base + '/')) return url
    return base + url
  }
  return url
}
