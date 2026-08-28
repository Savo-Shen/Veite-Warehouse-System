import { defineConfig, loadEnv } from 'vite'
import path from 'path'
import createVitePlugins from './vite/plugins'

// 后端地址默认 8080；要同时开第二个后端调试时，用环境变量指到别的端口，
// 例如 VITE_APP_PROXY_TARGET=http://localhost:8081 pnpm run dev
const apiProxy = (env) => {
  const target = env.VITE_APP_PROXY_TARGET || 'http://localhost:8080'
  return {
    // https://cn.vitejs.dev/config/#server-proxy
    '/dev-api': {
      target,
      changeOrigin: true,
      rewrite: (p) => p.replace(/^\/dev-api/, '')
    },
    '/prod-api': {
      target,
      changeOrigin: true,
      rewrite: (p) => p.replace(/^\/prod-api/, '')
    }
  }
}

// 与 docker/nginx.conf 保持一致，两条部署路径给出同样的响应头
const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'SAMEORIGIN',
  'Referrer-Policy': 'strict-origin-when-cross-origin'
}

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    base: env.VITE_APP_CONTEXT_PATH,
    plugins: createVitePlugins(env, command === 'build'),
    resolve: {
      // https://cn.vitejs.dev/config/#resolve-alias
      alias: {
        // 设置路径
        '~': path.resolve(__dirname, './'),
        // 设置别名
        '@': path.resolve(__dirname, './src')
      },
      // https://cn.vitejs.dev/config/#resolve-extensions
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // vite 相关配置
    server: {
      port: 80,
      // 开发服务器默认只绑本机。
      // vite dev 带 /@fs/ 端点，默认可读整个工作区（含 backend/.env、
      // application-local.yml 这些放着数据库密码和 JWT 密钥的文件），
      // 而且 vite 3 已经 EOL，后续一串 fs.deny 绕过的洞都不会再修。
      // 需要用手机等其他设备访问时，命令行显式加 --host。
      // 生产一律走 `pnpm run build:prod` + nginx / `pnpm run preview`。
      host: '127.0.0.1',
      open: true,
      proxy: apiProxy(env),
      fs: {
        strict: true,
        deny: ['.env', '.env.*', '*.{crt,pem,key,p12,jks}', '**/application-local.yml', '**/*.sql']
      }
    },
    // 预览服务器：只托管 build 产物，不暴露源码树。
    // start-prod 脚本用的就是它。
    preview: {
      port: 80,
      host: true,
      open: false,
      proxy: apiProxy(env),
      headers: SECURITY_HEADERS
    },
    //fix:error:stdin>:7356:1: warning: "@charset" must be the first rule in the file
    css: {
      postcss: {
        plugins: [
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                if (atRule.name === 'charset') {
                  atRule.remove();
                }
              }
            }
          }
        ]
      }
    }
  }
})
