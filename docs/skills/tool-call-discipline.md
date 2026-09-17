# Skill · 工具调用规范（Tool Call Discipline）

> 适用范围：所有使用 `super_admin:terminal` / `super_admin:shell` / `create_file` / `edit_file` / `read_file` 等工具的对话。
> 目的：把「工具调用失败」这一类问题从反复踩坑变成一次性规避。
> 来源：cn.apixiaoyuan.app 会话中真实发生过的八类失败（2026-09-15 ~ 09-17）。

---

## 一、总则

1. **一次响应里，工具调用块与说明文本不得混排导致解析失败。** 要么整段是工具调用，要么整段是文本。
2. **工具调用必须用规范标记，不得自创。** 禁用 `｜｜DSML｜｜`、`<tool:name>`、`<tool_call>`、`ToolName({...})`、代码围栏包裹 JSON 等一切非标准写法。
3. **`params` 必须是合法 JSON 对象。** 不是字符串、不是转义过的字符串、不是 YAML。
4. **工具选择遵循「哪一侧的任务用哪一侧的工具」**，不跨环境乱用（详见第三节）。
5. **工具调用失败时，先修参数，不换任务。** 连续失败超过三次就换实现方式（写独立脚本 / 改读文件），不硬刚。

---

## 二、参数构造：三条铁律

### 铁律 1 · 禁止把整个 shell 命令塞进 JSON 字符串里做多层转义

**反面案例（真实发生，连续七八次失败）：**
```
params = {"command": "cd /root/x && python3 - <<'PYEOF'
print(\"{\\\"a\\\": 1}\")
PYEOF"}
```
heredoc + 双引号 + 反斜杠 + `$` 变量，四层转义叠加，JSON 本身解析失败，工具压根没执行。

**正确做法：**
- 复杂脚本 → 用 `create_file` 落成独立文件（`.py` / `.sh`），再 `super_admin:terminal` 执行该文件。
- 简单命令 → 用单引号包裹字符串字面量，避免双引号嵌套。

### 铁律 2 · 中文路径与特殊字符要防编码坑

**反面案例：** `find '/storage/emulated/0/小猿口算/apktool_out' ...` 会返回 `\u0003`（0x03 控制字符）并中断，`exitCode` 却是 0，看不出失败。

**正确做法（按优先级）：**
1. 优先用 `read_file` / `read_file_part` / `list_files` / `find_files` / `grep_code` 这些结构化工具直读，它们处理路径编码更稳。
2. 必须用 shell 时，**先 `ls -d <中文父目录>` 确认可访问**，再用逐卷 `ls` 而非全盘 `find`。
3. 全盘扫描用 Python `os.walk` + 显式 `maxdepth`，但必须设短超时并准备中断。

### 铁律 3 · `find` / `grep` 全盘扫描必须设限

**事实：** 对 `/storage/emulated/0/小猿口算/apktool_out`（8 卷 smali，中文路径）做无界 `find` / `grep`，实测超时 150s ~ 900s 不等，多次返回 `KeyboardInterrupt` 或空结果。

**正确做法：**
- 先 `ls -d <目标包目录>` 定位到具体卷（如 `smali_classes6/i30`），再进目录操作。
- 需要搜索时用 `grep_code` 工具（带 `file_pattern` 过滤），而非 shell `grep -r`。
- 无法避免 shell 扫描时，显式 `timeoutMs` 提到 600000+，并接受可能失败。

---

## 三、环境选择：terminal 还是 shell？

| 任务类型 | 用哪个 | 理由 |
|---|---|---|
| 编辑 `/root/` 下的工程文件 | `super_admin:terminal`（Ubuntu） | 工程在 Ubuntu proot 里 |
| 读写 `/sdcard/`、`/storage/` | 两者都可 | proot 已挂载 sdcard/storage |
| `pm` / `am` / `getprop` / `dumpsys` | `super_admin:shell`（Android Root） | proot 里这些命令不存在 |
| 读写 `/data/data/<包名>/` | `super_admin:shell` | proot 里无权限、路径不通 |
| 跑 Gradle / Python / git | `super_admin:terminal` | Android 侧没有这些运行时 |
| 启动 Activity / 安装 APK | `super_admin:shell` | 需要 am/pm |

**关键区分：**
- `super_admin:terminal` 走 **Ubuntu proot**：有 python3 / git / gradle / java，**没有** pm / cmd / getprop。
- `super_admin:shell` 走 **Android Root**：有 pm / am / getprop / dumpsys / ss / toybox，**没有** python3。
- 两者路径空间不通：Ubuntu 侧看不到 `/data/data/`，Android 侧看不到 `/root/`。`/sdcard/` 双向可见。

---

## 四、`create_file` / `edit_file` 的 environment 参数

**铁律：给 Ubuntu 侧写文件，必须带 `environment=linux`。**

**反面案例（真实发生）：**
```
create_file(path="/root/migrate_session.py", new="...")
```
未带 `environment=linux` → 工具默认走 Android 侧 → 在 Android 路径空间里等不到 `/root/` → **卡住不返回**。

**正确写法：**
```
create_file(path="/root/migrate_session.py", environment="linux", new="...")
edit_file(path="/root/cn.apixiaoyuan.app/settings.gradle.kts", environment="linux", old="...", new="...")
```

**判断规则：**
- 路径以 `/root/`、`/home/`、`/etc/`、`/opt/` 开头 → 必须 `environment=linux`。
- 路径以 `/sdcard/`、`/storage/`、`/data/` 开头 → 可不带（默认 android），但带上更明确。
- 仓库内文件（`repo:<仓库名>`）→ 用 `environment=repo:<仓库名>`。

---

## 五、失败排查流程（按序执行）

当工具调用返回失败时：

1. **看错误类型：**
   - `params must be a valid JSON object` → 参数 JSON 本身坏了 → 检查转义层级，改用独立脚本文件。
   - `Unexpected parameters: xxx` → 传了不支持的参数 → 查工具 schema，删掉多余参数。
   - `No such file or directory` → 路径错 / environment 选错 → 确认文件在哪一侧。
   - `exitCode: 1` 但无输出 → 命令本身失败 → 看 stderr。
   - `\u0003` / `KeyboardInterrupt` → 中文路径 + 全盘扫描坑 → 换结构化工具或缩小范围。
   - 卡住不返回 → environment 选错 → 补 `environment=linux`。
2. **不重复同样的调用。** 同一参数连续失败两次，就换实现方式。
3. **连续失败三次，停止硬刚，改用「独立脚本文件」路线。**

---

## 六、正确范式（可直接照抄）

### 范式 A · 读工程文件
```
read_file(path="/root/cn.apixiaoyuan.app/app/src/main/java/cn/apixiaoyuan/app/core/session/SessionStore.kt", environment="linux")
```

### 范式 B · 在工程里执行 Gradle
```
package_proxy(tool_name="super_admin:terminal", params={"command": "cd /root/cn.apixiaoyuan.app && export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64 && ./gradlew :app:compileDebugKotlin --no-daemon --console=plain", "timeoutMs": 1800000})
```

### 范式 C · 落一个 Python 脚本再执行（避开 heredoc 转义）
```
create_file(path="/root/migrate_session.py", environment="linux", new="#!/usr/bin/env python3\n...")
package_proxy(tool_name="super_admin:terminal", params={"command": "python3 /root/migrate_session.py", "timeoutMs": 90000})
```

### 范式 D · 查 Android 侧包信息
```
package_proxy(tool_name="super_admin:shell", params={"command": "pm list packages | grep fenbi; getprop ro.product.model"})
```

### 范式 E · 用结构化工具搜代码（替代 shell grep）
```
grep_code(path="/root/cn.apixiaoyuan.app/app/src/main/java", environment="linux", pattern="addInterceptor", file_pattern="*.kt")
```

---

## 七、本会话踩过的坑（速查表）

| # | 症状 | 根因 | 修法 |
|---|---|---|---|
| 1 | `｜｜DSML｜｜` 标记导致工具未执行 | 自创标记 | 只用 `<tool>{...}</tool>` 规范块 |
| 2 | `params must be a valid JSON object`（连续七八次） | heredoc 多层转义炸了 JSON | 改用 `create_file` 落独立脚本 |
| 3 | `create_file` 卡住不返回 | 未带 `environment=linux` | 补 `environment=linux` |
| 4 | `Unexpected parameters: timeoutMs` | 给 `package_proxy` 直接传了 `timeoutMs` | `timeoutMs` 放进 `params` 对象内 |
| 5 | `\u0003` 控制字符中断 | 中文路径 + `find`/`grep` 全盘扫 | 改用 `read_file` / 逐卷 `ls` |
| 6 | `find` / `grep` 超时 150s~900s | 无界扫描 8 卷 smali | 先定位到具体卷再操作 |
| 7 | proot 里 `pm` 不存在 | 用错环境 | `pm`/`am` 走 `super_admin:shell` |
| 8 | Android 侧无 `python3` | 用错环境 | Python 走 `super_admin:terminal` |

---

## 八、一句话总结

**工具调用是「参数构造 + 环境选择」两件事；参数构造失败就落独立文件，环境选错就补 `environment` 或换工具。绝不重复同一个坏调用，绝不硬刚全盘扫描。**
