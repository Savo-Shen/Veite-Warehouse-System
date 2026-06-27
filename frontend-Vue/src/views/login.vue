<template>
  <div class="login">
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
      <div class="login-brand">
        <img class="login-logo" src="@/assets/logo/logo.png" alt="logo" />
        <h3 class="title">仓库出入库管理系统</h3>
        <p class="subtitle">快速搜索、入库、出库</p>
      </div>
      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          type="text"
          size="large"
          auto-complete="off"
          placeholder="账号"
        >
          <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          size="large"
          auto-complete="off"
          placeholder="密码"
          @keyup.enter="handleLogin"
        >
          <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="code" v-if="captchaEnabled">
        <div class="login-code-row">
          <el-input
            v-model="loginForm.code"
            size="large"
            auto-complete="off"
            placeholder="验证码"
            class="login-code-input"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
          </el-input>
          <button type="button" class="login-code" title="点击刷新验证码" @click="getCode">
            <img :src="codeUrl" class="login-code-img"/>
          </button>
        </div>
      </el-form-item>
      <el-checkbox v-model="loginForm.rememberMe" class="remember-checkbox">保持登录（推荐常用设备）</el-checkbox>
      <el-form-item class="login-actions">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          class="login-button"
          @click.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
        <!-- <el-button
          size="large"
          type="primary"
          style="width:45%;"
          @click.native.prevent="handleTry"
        >
          <span>获取体验账号</span>
        </el-button>
        <div style="float: right;" v-if="register">
          <router-link class="link-type" :to="'/register'">立即注册</router-link>
        </div> -->
      </el-form-item>
    </el-form>
    <el-dialog
      title="公众号二维码"
      v-model="dialogVisible"
      append-to-body
      :show-close="false"
      width="30%">
      <div style="text-align: center">
        <span class="font-title-large"><span class="color-main font-extra-large">关注公众号</span>回复<span class="color-main font-extra-large">库存</span>获取体验账号</span>
        <br>
        <img src="@/assets/logo/gzh.jpg" width="160" height="160" style="margin-top: 10px">
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="dialogConfirm">确定</el-button>
        </div>
      </template>
    </el-dialog>
    <!--  底部  -->
    <div class="el-login-footer">
      <span>Copyright © 2024-2025 shenyifan.home.blog Savo_Shen</span>
    </div>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login";
import Cookies from "js-cookie";
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const router = useRouter();
const { proxy } = getCurrentInstance();

const loginForm = ref({
  username: "",
  password: "",
  rememberMe: true,
  code: "",
  uuid: ""
});

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
};

const codeUrl = ref("");
const loading = ref(false);
// 验证码开关
const captchaEnabled = ref(true);
// 注册开关
const register = ref(false);
const redirect = ref(undefined);
const dialogVisible = ref(false);

function handleTry(){
  dialogVisible.value =true
}
function dialogConfirm(){
  dialogVisible.value =false;
}

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true;
      // 常用设备只记住账号和登录凭证，不在浏览器中保存密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 90, sameSite: "Lax" });
        Cookies.set("rememberMe", "true", { expires: 90, sameSite: "Lax" });
      } else {
        Cookies.remove("username");
        Cookies.set("rememberMe", "false", { sameSite: "Lax" });
      }
      // 清理旧版本曾保存的可还原密码
      Cookies.remove("password");
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        router.push({ path: redirect.value || "/" });
      }).catch(() => {
        loading.value = false;
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode();
        }
      });
    }
  });
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.data.captchaEnabled === undefined ? true : res.data.captchaEnabled;
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.data.img;
      loginForm.value.uuid = res.data.uuid;
    }
  });
}

function getCookie() {
  const username = Cookies.get("username");
  const rememberMe = Cookies.get("rememberMe");
  if (username !== undefined) {
    loginForm.value.username = username;
  }
  loginForm.value.rememberMe = rememberMe === undefined ? true : rememberMe === "true";
  Cookies.remove("password");
}

getCode();
getCookie();
</script>

<style lang='scss' scoped>
.color-main {
  color: #409EFF;
}
.font-extra-large {
  font-size: 20px;
}
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-image: url("../assets/images/login-background.jpg");
  background-size: cover;
  background-position: center;
}
.login-brand {
  margin-bottom: 28px;
  text-align: center;
}
.login-logo {
  display: block;
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  object-fit: contain;
}
.title {
  margin: 0;
  color: #707070;
  font-size: 20px;
  font-weight: 600;
}
.subtitle {
  display: none;
  margin: 8px 0 0;
  color: #8c8c8c;
  font-size: 13px;
}

.login-form {
  border-radius: 6px;
  background: #ffffff;
  width: 400px;
  padding: 25px 25px 5px 25px;
  .el-input {
    height: 40px;
    input {
      height: 40px;
    }
  }
  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 0px;
  }
}
.login-code-input {
  flex: 1;
  min-width: 0;
}
.remember-checkbox {
  margin: 0 0 25px 0;
}
.login-actions {
  width: 100%;
}
.login-button {
  width: 45%;
  margin: 0 auto;
}
.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}
.login-code-row {
  display: flex;
  align-items: stretch;
  gap: 12px;
  width: 100%;
}
.login-code {
  width: 120px;
  height: 40px;
  flex: 0 0 120px;
  padding: 0;
  cursor: pointer;
  background: #f7f8fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.el-login-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #fff;
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 1px;
}
.login-code-img {
  display: block;
  width: 100%;
  height: 40px;
  object-fit: cover;
}

@media (max-width: 768px) {
  .login {
    display: block;
    min-height: 100dvh;
    padding: max(26px, env(safe-area-inset-top)) 16px calc(72px + env(safe-area-inset-bottom));
    background:
      radial-gradient(circle at 18% 8%, rgba(64, 158, 255, 0.28), transparent 30%),
      linear-gradient(180deg, #eef6ff 0%, #f6f8fb 45%, #ffffff 100%);
    overflow-y: auto;
  }

  .login-form {
    width: 100%;
    max-width: none;
    margin: 0 auto;
    padding: 0;
    border-radius: 0;
    background: transparent;
    box-shadow: none;

    :deep(.el-form-item) {
      margin-bottom: 18px;
    }

    :deep(.el-input) {
      height: 50px;
    }

    :deep(.el-input__wrapper) {
      min-height: 50px;
      border-radius: 14px;
      box-shadow: 0 6px 18px rgba(31, 64, 104, 0.08);
    }

    :deep(.el-input__inner) {
      height: 50px;
      font-size: 16px;
    }

    .input-icon {
      height: 50px;
      width: 16px;
    }
  }

  .login-brand {
    margin: 8px 0 28px;
    padding: 20px 4px 6px;
  }

  .login-logo {
    width: 64px;
    height: 64px;
    margin-bottom: 16px;
    padding: 10px;
    background: rgba(255, 255, 255, 0.86);
    border-radius: 20px;
    box-shadow: 0 10px 24px rgba(31, 64, 104, 0.12);
  }

  .title {
    color: #17233d;
    font-size: 26px;
    line-height: 1.25;
    letter-spacing: 0.5px;
  }

  .subtitle {
    display: block;
    color: #5f6f89;
    font-size: 15px;
  }

  .login-code-row {
    gap: 10px;
  }
  .login-code {
    width: 120px;
    height: 50px;
    flex-basis: 120px;
    border-radius: 14px;
    background: #fff;
    border: 0;
    box-shadow: 0 6px 18px rgba(31, 64, 104, 0.08);
  }

  .login-code-img {
    width: 100%;
    height: 50px;
    padding-left: 0;
    object-fit: cover;
  }

  .remember-checkbox {
    display: flex;
    margin: 2px 0 22px 2px;
    color: #526070;
  }

  .login-actions {
    margin-bottom: 0;
  }

  .login-button {
    width: 100%;
    min-height: 50px;
    border-radius: 14px;
    font-size: 17px;
    font-weight: 600;
    box-shadow: 0 10px 22px rgba(64, 158, 255, 0.26);
  }

  .el-login-footer {
    height: auto;
    padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
    line-height: 1.5;
  }
}
</style>
