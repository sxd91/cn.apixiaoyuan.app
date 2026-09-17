# Skill · 工具调用规范（精简注入版）

> 用途：直接贴进任意对话开头，作为工具调用约束。完整版见 `tool-call-discipline.md`。

## 1. 标记格式

只允许 `<tool>{"name": "<工具名>", "arguments": {...}}</tool>`。

禁止：`｜｜DSML｜｜`、`<tool:name>`、`<tool_call>`、`<name>`、`<parameter>`、`id=`/`name=` 属性、`ToolName({...})`、代码围栏包裹、"Tool call:" 文本前缀。

## 2. params 是 JSON 对象，不是字符串

- `package_proxy` 的 `params` 字段值必须是**对象**，不是转义字符串。
- `timeoutMs` 放进 `params` 对象内，不能作为 `package_proxy` 顶层参数。
- 含 heredoc / 双引号 / 反斜杠 / `$` 变量的复杂命令，不塞进 JSON 字符串做多层转义 —— 用 `create_file` 落独立脚本（`.py`/`.sh`）再执行。

## 3. 环境选择

| 任务 | 工具 | 环境事实 |
|---|---|---|
| 编辑 `/root/` 工程 | `super_admin:terminal` | Ubuntu proot；有 python3/git/gradle/java，无 pm/am/getprop |
| `pm`/`am`/`getprop`/`dumpsys` | `super_admin:shell` | Android Root；无 python3 |
| 读写 `/data/data/<包名>/` | `super_admin:shell` | proot 无权限、路径不通 |
| 读写 `/sdcard/`、`/storage/` | 两者均可 | proot 已挂载 |

路径空间不通：Ubuntu 侧看不到 `/data/data/`，Android 侧看不到 `/root/`。

## 4. create_file / edit_file 必须带 environment

- 路径 `/root/`、`/home/`、`/etc/`、`/opt/` 开头 → **必须** `environment=linux`。
- 不带会卡住不返回（工具在 Android 路径空间等不到目标）。
- `/sdcard/`、`/storage/`、`/data/` 可不带，但带上更明确。

## 5. 中文路径与全盘扫描

- 对含中文的路径做 shell `find`/`grep` 全盘扫，会返回 `\u0003`（0x03）中断，`exitCode` 仍为 0，看不出失败。
- 优先用 `read_file`/`read_file_part`/`list_files`/`find_files`/`grep_code` 结构化工具。
- 必须用 shell：先 `ls -d <中文父目录>` 确认可访问，再逐卷 `ls`，不用无界 `find`。
- 无界扫描 8 卷 smali 实测超时 150s~900s，多次 `KeyboardInterrupt`。

## 6. 失败处置

| 错误 | 根因 | 修法 |
|---|---|---|
| `params must be a valid JSON object` | 转义层级炸了 | 改落独立脚本文件 |
| `Unexpected parameters: xxx` | 传了不支持参数 | 查 schema，删多余参数 |
| 卡住不返回 | environment 选错 | 补 `environment=linux` |
| `\u0003` / `KeyboardInterrupt` | 中文路径 + 全盘扫 | 换结构化工具或缩小范围 |
| `No such file or directory` | 路径错或环境错 | 确认文件在哪一侧 |

**同一参数连续失败两次即换实现方式；连续失败三次停止硬刚，改走独立脚本文件路线。**

## 7. 一句话

工具调用 = 参数构造 + 环境选择。参数坏了就落文件，环境错了就补 `environment` 或换工具。不重复坏调用，不硬刚全盘扫描。
