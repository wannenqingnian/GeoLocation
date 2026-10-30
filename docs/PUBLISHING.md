# 公开发布说明

本文用于将 GeoLocation 发布到 GitHub，并提交至 LSPosed 模块仓库。源码仓库为 <https://github.com/wannenqingnian/GeoLocation>，包名为 `io.github.ceigt.geolocation`。

## 发布前安全检查

发布前确认下列文件和信息没有进入 Git 暂存区：

- `local.properties`（包含 `BAIDU_WEB_AK`）
- `keystore.properties`、`keystore/`、`*.jks`、`*.keystore`、`*.p12`
- 密码、Token、私钥、账号、服务器地址及其他个人资料
- 构建产物目录 `app/build/`、`.gradle/`、`.kotlin/`

可在 PowerShell 中检查：

```powershell
git status --short
git check-ignore local.properties keystore.properties
git diff --cached --check
```

`local.properties` 与 `keystore.properties` 应显示为已忽略。若曾意外公开密钥或密码，请立即在对应服务端轮换，而非仅从仓库历史中删除。

## 首次上传 GitHub

在项目根目录执行。以下命令只暂存、提交并推送当前项目；请先自行核对 `git status` 输出。

```powershell
git remote set-url origin https://github.com/wannenqingnian/GeoLocation.git
git branch -M main
git add .
git status
git commit -m "Prepare GeoLocation public release"
git push -u origin main
```

如果希望先保留现有 `self-development` 分支，可将最后两行改为：

```powershell
git commit -m "Prepare GeoLocation public release"
git push -u origin self-development
```

随后在 GitHub 创建 Pull Request 合并到 `main`。不要在未检查 `git status` 的情况下直接执行 `git add .`。

## 版本与 GitHub Release

版本号由 `app/build.gradle.kts` 计算：`major.minor.patch` 映射为 `major * 10000 + minor * 100 + patch`。因此 `1.0.0` 的 `versionCode` 是 `10000`。

LSPosed Release 标签固定采用：

```text
versionCode-versionName
```

例如首次公开版本：

```text
10000-1.0.0
```

建议在 GitHub Release 中使用：

```text
标题：GeoLocation 1.0.0
标签：10000-1.0.0
附件：GeoLocation-1.0.0-YYYYMMDD.apk
```

APK 必须使用你自己的正式签名。没有 `keystore.properties` 的本地 Release 保持未签名，不能作为可安装的正式发布包。

## GitHub Actions 自动构建

日常测试版使用 “Verify changes”：推送到 `main` 或 `fixes/**` 分支即执行测试、Lint 和 APK 构建，也可手动选择分支运行。成功后的 `candidate-apks-<提交哈希>` 附件保留 14 天，包含未签名 Release APK 和设备测试客户端；验证报告单独保存。下载 Release APK 后使用现有正式证书在本机签名并校验证书指纹即可安装，签名不需要重新编译。这个流程不上传本机私钥或地图凭据，也不会创建公开 Release。

正式发布使用 [Publish signed APK](https://github.com/wannenqingnian/GeoLocation/actions/workflows/release.yml)。与日常校验不同，这个流程会自动签名并将 APK 上传到公开 Releases。它只在推送版本标签时触发，也可手动输入已有标签重试；推送普通代码不会自动公开发布。

先在仓库 Settings → Secrets and variables → Actions 配置四个签名 Secrets（不要提交到源码）：

```text
GEOLOCATION_KEYSTORE_BASE64
GEOLOCATION_KEYSTORE_PASSWORD
GEOLOCATION_KEY_ALIAS
GEOLOCATION_KEY_PASSWORD
```

`GEOLOCATION_KEYSTORE_BASE64` 是本机 `keystore/geolocation-release.p12` 的 Base64 内容，其他三项对应 `keystore.properties` 的密码和别名。地图凭据无需配置，由安装者在设置中输入。请保留同一份证书，工作流会校验本仓库的正式证书 SHA-256：

```text
cc5e95bcabbf19ad79992ffffbae77bf2dad5cafed01b4f3cd2c2e7213ec7633
```

发布时先提交代码，再推送对应的版本标签，例如：

```powershell
git tag 20111-2.1.11
git push origin 20111-2.1.11
```

无需预先创建 Release 草稿。流水线会校验标签与版本号映射，运行 Debug/Release 单元测试、Release Lint、构建及设备测试代码编译，再将验证过的产物交给独立签名任务。签名和构建类型校验通过后，自动创建草稿、上传签名 APK 与 SHA-256 校验文件，最后公开发布。带版本后缀的标签（如 `20111-2.1.11-rc.1`）会标记为预发布。

已公开的 Release 不会被覆盖；失败后可在 Actions 手动选择同一标签重试，已有草稿中的同名附件会更新。证书只在签名任务中临时恢复，步骤结束即删除。实机测试仍须在正式发布标签前由维护者完成。

## 提交 LSPosed 模块仓库

1. 确认公开 GitHub 仓库默认分支已包含 `README.md`、`SUMMARY`、`SOURCE_URL`、`LICENSE`，并已有带有效 APK 附件的 GitHub Release。
2. 打开 <https://modules.lsposed.org/submission/>，提交包名 `io.github.ceigt.geolocation`。
3. 按页面提示在 `Xposed-Modules-Repo/submission` 创建提交；机器人会创建对应包名仓库并邀请维护者。
4. 在创建的模块仓库中保留同步的 `README.md`、`SUMMARY`、`SOURCE_URL`、`LICENSE`，并按 `versionCode-versionName` 标签提交后续 Release。

提交描述建议：

```text
GeoLocation — Android Xposed/LSPosed location simulation module for lawful testing.
```

## 合规提示

公开仓库与 Release 仅应描述合法、已授权的测试、开发调试和学习研究用途。不要将项目宣传为绕过规则、虚假签到、获得不当利益或侵犯他人权益的工具。
