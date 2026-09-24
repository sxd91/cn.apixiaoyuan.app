# Skill · 本机环境事实速查（Environment Facts）

> 用途：与 `tool-call-discipline.md` 配套。规范讲「怎么调用」，本文讲「本机到底是什么样」。
> 来源：2026-09-22 会话中实测确认，非推测。

---

## 一、两侧环境能力边界

| 能力 | `super_admin:terminal`（Ubuntu proot） | `super_admin:shell`（Android Root） |
|---|---|---|
| python3 | 有 | 无 |
| git / gradle / java | 有 | 无 |
| readelf / nm / objdump | 有 | 无 |
| pm / am / getprop | **无** | 有 |
| dumpsys / ss / toybox | **无** | 有 |
| 访问 `/root/` | 有 | **无** |
| 访问 `/data/data/<包名>/` | **无**（权限 + 路径不通） | 有 |
| 访问 `/sdcard/`、`/storage/` | 有（已挂载） | 有 |

**判定规则：** 命令属于 Android 系统管理（包、活动、属性、网络栈）→ shell；属于工程构建 / 脚本 / 二进制分析 → terminal。

---

## 二、路径空间

- Ubuntu 侧根：`/root/`、`/home/`、`/etc/`、`/opt/`。
- Android 侧根：`/data/`、`/system/`、`/sdcard/`、`/storage/`。
- 交集：`/sdcard/`、`/storage/` 双向可见，是唯一可直接交换文件的通道。
- 写文件工具（`create_file` / `edit_file`）路径以 `/root/`、`/home/`、`/etc/`、`/opt/` 开头时，**必须带 `environment=linux`**，否则工具在 Android 路径空间等待目标，卡住不返回。

---

## 三、当前环境已确认的不可用项

| 项 | 状态 | 影响 | 替代路径 |
|---|---|---|---|
| `tavily` 包 | 缺 `TAVILY_API_KEY`，无法激活 | 不能走 tavily 联网检索 | 改走 `visit_web`；或明示无法实时确认 |
| `/root/leo_jadx` | 存在但为空 | jadx 反编译源码不可读 | 重新产出或定位新位置 |
| `/root/leo_jadx_classes6` | 存在但为空 | 同上 | 同上 |

**注意：** `find_files` 能查到这两个目录存在，但 `list_files` 返回空 —— 说明目录壳在、内容已清。不要误判为「路径写错」。

---

## 四、项目侧可读资产（2026-09-22 实测）

- 工程根：`/root/cn.apixiaoyuan.app`（Ubuntu 侧）。
- 源码：`app/src/main/java/cn/apixiaoyuan/app/`，`core/` 下含 design / navigation / network / database / datastore / model / session / auth / exercise / pk / native / samples。
- native 声明：`core/native/NativeEncoders.kt`（`sdwioxccsd` / `zcvsd1wr2t` / `c` 三方法签名已从原版 smali 读出，语义未确证）。
- native 库：`app/src/main/jniLibs/arm64-v8a/`。
- API 层：`core/network/api/`，共 10 个 Service（含 `YtkApiService`、`YtkUserCenterApiService`）。
- 文档：`docs/API-INVENTORY.md`、`docs/DEV-PLAN.md`、`docs/LOGIN-API.md`、`docs/skills/`。
- 短信脚本：`/root/xyks_sms/`（`send_sms.py`、`encode_phone.py`）。

---

## 五、一句话

环境能力与路径空间是硬边界，不是偏好问题。命令能用与否，先查本文表；查不到再实测，实测后回填本文。
