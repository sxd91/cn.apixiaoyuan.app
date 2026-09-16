# 逆向系老挂 · 小猿口算接口清单

> 来源：`com.fenbi.android.leo` 3.141.1（versionCode 31410199）逆向结果
> 目标工程：`cn.apixiaoyuan.app`
> 用途：本工程 `core/network/` 层的 Retrofit Service 定义依据

---

## 0. 网络层约定

### 0.1 双 BaseUrl

**已从原版 `mg/h.smali`（`leo-host_release` 模块）逐行挖出真实域名：**

| 别名 | 线上（com） | 内部（biz） | 方法链 |
|---|---|---|---|
| `leo_base_url` | `https://xyks.yuanfudao.com` | `https://xyks.yuanfudao.biz` | `d()` → `f()` → `e(z())` → `"xyks.yuanfudao." + i(z())` |
| `ytk_base_url` | `https://ape-api.yuanfudao.com` | `https://ape-api.yuanfudao.biz` | `w()` → `y()` → `x(z())` → `"ape-api.yuanfudao." + i(z())` |

`i(Z)` 是后缀切换器：`Z=true` → `"com"`，`false` → `"biz"`；`z()` 在环境未命中时返回 `true`，故线上取 `"com"`。

**同文件挖出的其他 host（本工程暂不用，留档）：**

| 方法 | 域 |
|---|---|
| `s(Z)` | `xyst.yuanfudao.{com\|biz}`（小猿搜题） |
| `g(Z)` | `xyks-h3.yuanfudao.{com\|biz}`（H3 网关） |
| `u(Z)` | `ke.yuanfudao.{com\|biz}` |
| `j(Z)` | `gallery.yuanfudao.com` / `ytkgallery.yuanfudao.biz` |
| `c()` | `solar.fbcontent.cn` |
| `A(s)` | 补 `https://` 前缀的规范化函数 |


| 别名 | 用途 | 覆盖范围 |
|---|---|---|
| `leo_base_url` | 主域 | 练习 / PK / 中文 / 英文 / 试卷 / 用户资料 |
| `ytk_base_url` | 账号域 | 登录 / 账号 / 子账号 |

实现方式：自定义注解 `@BaseUrl("leo_base_url")` / `@BaseUrl("ytk_base_url")`，OkHttp Interceptor 按注解重写请求 host。

### 0.2 转换器与校验注解

| 注解 | 语义 |
|---|---|
| `@GsonConverter` | Gson 反序列化（含自定义 `GsonConverterAdapter`） |
| `@MoshiConverter` | 历史接口，Moshi 反序列化 |
| `@NotNullAndValid` | 响应非空 + 业务校验 |
| `@CheckNothing` | 不做业务校验（logout 等） |
| `@NeedDecode` | **响应体需 native 层解码**，纯 Gson 解析拿到密文 |

### 0.3 服务定位

原版用 `sp/n` 类集中静态持有全部 ApiService：

```
sp/n;->n() = LeoChineseApiService
sp/n;->u() = LeoPaperExerciseApiService
sp/n;->H() = 同上（别名）
```

本工程改为 `core/network/ServiceLocator.kt` + `RetrofitFactory`。

### 0.4 鉴权

- 账号域接口带 Query 参数 `YFD_U`（Long，猿辅导用户 ID）
- token 疑似存于 MMKV：`/data/data/com.fenbi.android.leo/files/mmkv/`
- 请求头鉴权细节待抓包确认

---

## 1. 登录 / 注册 / 账号

### 1.1 LeoProfileApiService（`leo_base_url`）

| 方法 | HTTP | 路径 | 参数 | 返回 | 注解 |
|---|---|---|---|---|---|
| getUserInfo | GET | `/leo-profile/android/user-infos` | — | `UserVO` | `@CheckNothing` |
| updateUserInfo | PUT | `/leo-profile/android/user-infos` | Body `UserVO` | `UserVO` | — |
| getUserSetting | GET | `/leo-profile/android/user-setting` | — | `UserSetting` | `@CheckNothing` |
| updateUserSetting | POST | `/leo-profile/android/user-setting/update` | Body `UserSetting` | `Boolean` | — |
| leoLogout | POST | `/leo-profile/android/logout` | — | `Unit` | `@CheckNothing` |

### 1.2 YtkAccountService（`ytk_base_url`）

| 方法 | HTTP | 路径 | 参数 | 返回 |
|---|---|---|---|---|
| getCurrentUserInfo | GET | `/accounts/android/current` | Query `YFD_U: Long` | `Call<CurrentUserInfo>` |
| getUserInfo | GET | `/profile/android/user-info` | — | `Call<YtkUserSchoolInfo>` |

### 1.3 子账号（`business.user.subaccount.api.*`）

| 方法 | 说明 |
|---|---|
| `YtkAccountsService.createSubAccount` | 创建子账号 |
| `YtkAccountsService.destorySubAccount(int, int, String)` | 销毁子账号（**拼写就是 destory**） |
| `LeoProfileApiService.getSubAccounts` | 列子账号（与登录同名不同包） |

### 1.4 登录流程

```
FastLoginActivity
  -> 运营商 SDK 拿 token
  -> YtkAccountService.getCurrentUserInfo(YFD_U)
  -> RoleAndGradeSettingActivity（选角色 + 年级）
  -> updateUserInfo 落库
  -> HomeActivity
```

登录入口 Activity（Manifest 实测）：

- `login.v2.activity.FastLoginActivity`（一键登录主入口）
- `login.quick.QuickLoginAuthActivity` / `QuickLoginTransferActivity`
- `login.qrcode.phone.PhoneQRCodeScanLoginActivity` / `PhoneQRCodeLoginConfirmActivity`
- `login.qrcode.pad.PadQRCodeLoginActivity`
- `login.huawei.*` / `login.honor.*` / `login.vivo.VivoLoginActivity` / `login.xiaomi.XiaomiLoginTransferActivity`
- `business.user.account.SetAccountActivity` / `ChangeAccountActivity` / `DeregisterAccountActivity`
- `business.user.grade.RoleAndGradeSettingActivity`

---

## 2. 练习模块

### 2.1 数学 LeoMathApiService（`leo_base_url`）

| 方法 | HTTP | 路径 | 参数 |
|---|---|---|---|
| getExercisesKeyPoints | GET | `/leo-math/android/exams/exercises/type/{type}` | type / gradeId / semester / book |
| getMathExercisesTabV6 | GET | `/leo-math/android/exams/v5/exercises` | — |
| getMathExercisesCall | GET | `/leo-math/android/exams/{examId}` | — |
| getMathExerciseKeypointListData | GET | `/leo-math/android/exams/listen` | — |
| getMathChallengeExercisesCall | GET | `/leo-math/android/exams/all-exercises` | — |
| uploadExamResult | POST | `/leo-math/android/exams` | Body |
| getExamResult | GET | `/leo-math/android/exams/{examId}` | — |
| fetchExamHistory | GET | `/leo-math/android/exams/v3` | cursorTime + limit |
| deleteExamHistory | DELETE | `/leo-math/android/exams/delete/v2` | Body 列表 |
| getMathExercisesMathThoughtInfo | GET | `/leo-math/android/math-thinking/album-recommend` | — |
| getMathExerciseMedal | GET | `/leo-star/android/exercise-medal/detail` | — |
| getExerciseHomeStudyGroupCardV2 | GET | `/leo-homework/android/study-group/schedule/popularize/math` | — |
| getHomeKeypointCallV2 | GET | `/leo-exam/android/video/keypoint/fourlevel/homepage` | grade / practiceGrade |
| getBookCoverInfoData | GET | `/leo-exam/android/textbook/info` | — |

### 2.2 语文 LeoChineseApiService（`leo_base_url`）

| 方法 | HTTP | 路径 | 备注 |
|---|---|---|---|
| getChinesDictationHistory | GET | `/leo-chinese/android/exercises/history/listen/v3` | — |
| deleteDictationHistory | DELETE | `/leo-chinese/android/exercises/history/listen/delete` | — |
| addChineseItem | POST | `/leo-chinese/android/exercises/history/ebook/listen/{exerciseId}` | — |
| addOnlineChineseItem | POST | `/leo-chinese/android/exercises/history/ebook/listen/write/{exerciseId}` | FormUrlEncoded `wordId` |
| getChineseAIClassroomHistory | GET | `/leo-chinese/android/preview/exercise/loadmore` | — |
| deleteChineseAIClassroomHistory | DELETE | `/leo-chinese/android/preview/exercise/delete` | — |
| getChineseCharactersPracticeListenTabData | GET | `/leo-chinese/android/tab/listen` | book / grade / semester |
| getChineseKnowledgeUsageExamInfo | POST | `/leo-chinese/android/question-exam` | FormUrlEncoded `keypointId`/`limit`，**`@NeedDecode`** |
| getChineseKnowledgeUsageExamResult | GET | `/leo-chinese/android/question-exam/{examId}` | **`@NeedDecode`** |
| uploadChineseWordStudyExerciseHandWriting | POST | `/leo-chinese/android/exercises/upload/handwriting/v2/{exerciseId}` | ruleTypeId + scripts |
| getChineseWordStudyExerciseResult | GET | `/leo-chinese/android/exercises/upload/handwriting/{examId}` | — |
| getPictureStoryTabData | GET | `/leo-chinese/android/image-practice/tab` | — |
| getLearningPinyinTabData | GET | `/leo-chinese/android/pinyin-practice/tab` | — |

字典前缀 `/leo-cn-dictionary/android/`：

| 方法 | 路径 | 语义 |
|---|---|---|
| 精确搜索 | `/search/actual-search` | — |
| 联想搜索 | `/search/fuzzy-search` | — |
| 分类搜索 | `/search/classified-search` | — |
| 字词学习 | `/text-learning/{textId}` | — |

点读 LeoDianduApiService：`/leo-chinese/android/exercises/read/catalog`、`read/catalog/{textbookId}`、`read`。

### 2.3 英语 LeoEnglishApiService（`leo_base_url`）

前缀 `/leo-english/android/`：

| 分类 | 路径 |
|---|---|
| 历史 | `/exercises/history/listen/v2` |
| 手写 | `/handwriting/exercises/listen`、`/listen/wrong`、`/listen/repeat/{exerciseId}`、`/listen/lastUnits`、`/listen/write/judge` |
| 单词 | `/word-memorize/book/list`、`/book/switch`、`/module` |
| 阅读 | `/readExercise/info`、`/readExercise/result/{exerciseId}`、`/readExercise` |
| 口语 | `/spokenExercise`、`/spokenExercise/{exerciseId}`、`/spokenExercise/dialogue`、`/spokenExercise/dialogue/{exerciseId}`、`/exercise/spoken/{keypointId}` |
| 其他 | `/exercise/{type}`、`/exercise/{ruleType}`、`/exercise/recommend`、`/exercise/result/{exerciseId}`、`/tab/listen`、`/textbook-version/list`、`/preview/exercise/loadmore`、`/preview/exercise/delete` |

### 2.4 试卷 LeoPaperExerciseApiService（`leo_base_url`）

> **坑：路径前缀是 `leo-exam`，不是 `leo-paper`。**

| 方法 | HTTP | 路径 | 备注 |
|---|---|---|---|
| getMathPaperExerciseSearchData | GET | `/leo-exam/android/paper/search` | Query `name` |
| getPaperExerciseFilterData | GET | `/leo-exam/android/...` | courseId / gradeId / semester / lat / lng |
| getPaperExerciseListData | GET | — | typeId / provinceId / phaseId |
| getPaperExerciseDetailData | GET | `/leo-exam/android/paper/{id}` | Path id + Query reportId，**`@NeedDecode`** |
| getPaperExerciseResult | GET | — | id |
| getPaperDownloadedTimes | GET | `/leo-exam/android/paperDownload/printing-record` | paperId / sourcePlatform / paperType |
| recordDownloadPaper | POST | — | paperId / sourcePlatform / paperType |

---

## 3. PK 模块

### 3.1 数学 PK（LeoMathApiService，`leo_base_url`）

| 方法 | HTTP | 路径 | 参数 | 返回 |
|---|---|---|---|---|
| getGamePkEntryData | GET | `/leo-game-pk/android/game/homepage/account/total` | — | `GamePkEntryData` |
| getGamePkHomeData | GET | `/leo-game-pk/android/math/pk/home` | Query `grade` | `OralPKHomaData` |

### 3.2 诗 PK（LeoPoemsParadiseApiService，`leo_base_url`）

| 方法 | HTTP | 路径 |
|---|---|---|
| getPoemsPkEntryData | GET | `/leo-game-pk/android/game/homepage` |
| getPoemsReciteRecords | GET | `/leo-poetry/android/article/recite` |
| getPoetryGardenContentGroup | GET | `/leo-poetry/android/poetry-garden/content-set-group/{type}` |
| getPoetryGardenContentSetInfo | GET | `/leo-poetry/android/poetry-garden/content-set/{type}/{contentSetId}` |
| getPoetryGardenMainInfo | GET | `/leo-poetry/android/poetry-garden` |

### 3.3 口算 PK 是 H5

`LeoPkAppWidgetProvider` 逐行还原：

```
{base}/bh5/leo-web-oral-pk/pk.html#/
  -> URLEncoder.encode(url, "utf-8")
  -> leo://openWebView?url={encoded}
       &hideNavigation=true
       &hideStatusBar=true
       &autoHideLoading=false
       &origin=widget
       &_source=widgetpk
       &targetModule=mathExercise
```

**结论：原生侧只做入口 + 埋点参数。真实对战协议在 H5 的 XHR，需 mitmproxy + 系统证书注入抓，或走 WebView JS bridge。**

---

## 4. native 编码层（`@NeedDecode`）

| so | 大小 | 作用 |
|---|---|---|
| `libRequestEncoder.so` | 919,568 | 请求编码器 |
| `libContentEncoder.so` | 298,144 | 内容编码器 |
| `libRedressProcess.so` | 4,942,568 | 响应解码 + 风控（体量最大） |

其他 so：`libhttpdns.so`（HTTPDNS）、`libmsaoaidsec.so`/`libmsaoaidauth.so`（MSA OAID 设备指纹）、`libmarsxlog.so`（腾讯 mars 日志）、`libmmkv.so`（本地存储，token 大概率在这）。

**已确认需 `@NeedDecode` 的接口：**

- `getChineseKnowledgeUsageExamInfo`（知识点运用题目）
- `getChineseKnowledgeUsageExamResult`（知识点运用结果）
- `getPaperExerciseDetailData`（试卷详情）

**复刻路径：** IDA 定位 `libRequestEncoder.so` 的 JNI 导出（`Java_com_fenbi_..._encode`），先找字符串常量（S 盒、密钥）再 hook。

---

## 5. 待补全的 Service

原版在 `sp/n` 注册但本清单尚未展开的：

- `LeoErrorBookApiService`（错题本）
- `LeoHomeworkApiService`（作业）
- `LeoPrintApiService`（打印）
- `LeoUserCenterApiService`（用户中心）
- `SolarApiService`
- `LeoFeedbackApiService`（反馈）

---

## 6. 关键文件路径索引（原 APK 侧）

```
apktool_out/smali_classes6/com/yuanfudao/android/leo/appwidget/LeoPkAppWidgetProvider.smali
apktool_out/smali_classes3/com/fenbi/android/leo/network/api/LeoMathApiService.smali
apktool_out/smali_classes3/com/fenbi/android/leo/network/api/LeoChineseApiService.smali
apktool_out/smali_classes3/com/fenbi/android/leo/network/api/LeoEnglishApiService.smali
apktool_out/smali_classes3/com/fenbi/android/leo/network/api/LeoPaperExerciseApiService.smali
apktool_out/smali_classes6/com/yuanfudao/android/leo/login/api/LeoProfileApiService.smali
apktool_out/smali_classes3/com/fenbi/android/leo/network/api/YtkAccountService.smali
apktool_out/smali_classes6/com/yuanfudao/android/leo/poems_paradise/LeoPoemsParadiseApiService.smali
apktool_out/smali_classes3/sp/n.smali
```
