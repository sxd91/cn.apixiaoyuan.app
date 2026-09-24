package cn.apixiaoyuan.app.feature.api

/**
 * 接口元数据注册表。
 *
 * 模块 9（接口浏览器）的核心数据源。把 `ServiceLocator` 里已落盘的
 * Service 方法逐条编码成 [ApiEndpoint]，UI 从它渲染列表与参数表单，
 * 不需要反射——反射在 R8 混淆后会失效，而且拿不到 `@Query` / `@Field`
 * 参数名（Kotlin 默认不保留参数名到字节码）。
 *
 * **数据来源**：每一条都是从 `smali` 逐行读出的真实路径与参数，不是猜的。
 * 已收录的 Service：
 *  - `YtkApiService`（账号域，5 方法）
 *  - `LeoGatewayService`（主域，3 核心 + 5 厂商留档）
 *  - `LeoProfileApiService`（主域，5 方法）
 *  - `LeoExerciseCommonLegacyApiService`（主域，5 方法）
 *  - `LeoEnglishExerciseWritingApiService`（主域，8 方法）
 *  - `LeoUserApiService`（主域，12 方法）
 *
 * 未收录的（第二批待落盘）：`LeoPoemsParadiseApiService` /
 * `ShepherdApiService` / `LeoShareApiService` / `YtkUserCenterApiService`。
 * 它们的路径已从 smali 读出，见 `docs/API-INVENTORY.md`。
 */
object ApiRegistry {

    /** 参数位置类型。 */
    enum class ParamIn { QUERY, PATH, FIELD, BODY, HEADER }

    /** 单个参数描述。 */
    data class ApiParam(
        val name: String,
        val type: String,
        val location: ParamIn,
        val required: Boolean = true,
        val defaultValue: String? = null,
        val note: String? = null,
    )

    /**
     * 单个接口描述。
     *
     * @param group   分组名（Service 简称），UI 按此折叠
     * @param method  HTTP 方法
     * @param path    路径（PATH 参数已写成 `{name}` 形式）
     * @param baseUrl 域名别名（`leo` / `ytk`）
     * @param params  参数列表
     * @param needDecode  是否需要 native 解码响应
     * @param needEncode  是否需要 native 编码请求
     * @param deprecated  是否为旧版 `Call` 接口（新代码优先用 suspend 版）
     * @param note    备注
     */
    data class ApiEndpoint(
        val group: String,
        val name: String,
        val method: String,
        val path: String,
        val baseUrl: String,
        val params: List<ApiParam> = emptyList(),
        val needDecode: Boolean = false,
        val needEncode: Boolean = false,
        val deprecated: Boolean = false,
        val note: String? = null,
    )

    /** 全部接口，按 Service 分组排列。 */
    val all: List<ApiEndpoint> = buildList {

        // ---- YtkApiService（账号域）----
        add(ApiEndpoint(
            group = "账号域",
            name = "logout",
            method = "POST",
            path = "/accounts/android/logout",
            baseUrl = "ytk",
            note = "登出，无参数",
        ))
        add(ApiEndpoint(
            group = "账号域",
            name = "passwordLoginCall",
            method = "POST",
            path = "/accounts/android/safe/login",
            baseUrl = "ytk",
            params = listOf(
                ApiParam("YFD_U", "Long", ParamIn.QUERY, defaultValue = "0"),
                ApiParam("phone", "String", ParamIn.FIELD),
                ApiParam("password", "String", ParamIn.FIELD),
            ),
            deprecated = true,
            note = "旧版密码登录，返回 Call<UserAccount>",
        ))
        add(ApiEndpoint(
            group = "账号域",
            name = "smsLogin",
            method = "POST",
            path = "/accounts/android/safe/login",
            baseUrl = "ytk",
            params = listOf(
                ApiParam("YFD_U", "Long", ParamIn.QUERY, required = false),
                ApiParam("phone", "String", ParamIn.FIELD, required = false),
                ApiParam("verification", "String", ParamIn.FIELD, required = false),
                ApiParam("autoRegister", "Boolean", ParamIn.FIELD, defaultValue = "false"),
            ),
            deprecated = true,
            note = "旧版短信登录，返回 Call<UserAccount>",
        ))
        add(ApiEndpoint(
            group = "账号域",
            name = "smsVerify",
            method = "POST",
            path = "/verifier/android/sms",
            baseUrl = "ytk",
            params = listOf(
                ApiParam("YFD_U", "Long", ParamIn.QUERY, required = false),
                ApiParam("phone", "String", ParamIn.FIELD, required = false),
            ),
            note = "发送短信验证码。注意前缀是 /verifier/ 不是 /accounts/",
        ))
        add(ApiEndpoint(
            group = "账号域",
            name = "validateVerifyCode",
            method = "POST",
            path = "/verifier/android/validate",
            baseUrl = "ytk",
            params = listOf(
                ApiParam("phone", "String", ParamIn.FIELD),
                ApiParam("verification", "String", ParamIn.FIELD),
            ),
            note = "校验验证码（可选步骤）",
        ))

        // ---- LeoGatewayService（主域）----
        add(ApiEndpoint(
            group = "登录网关",
            name = "passwordLogin",
            method = "POST",
            path = "/leo-gateway/android/auth/password",
            baseUrl = "leo",
            params = listOf(
                ApiParam("YFD_U", "Long", ParamIn.QUERY, defaultValue = "0", note = "首次登录传 0"),
                ApiParam("phone", "String", ParamIn.FIELD),
                ApiParam("password", "String", ParamIn.FIELD),
            ),
            note = "密码登录。返回 code==1 才是成功",
        ))
        add(ApiEndpoint(
            group = "登录网关",
            name = "smsLogin",
            method = "POST",
            path = "/leo-gateway/android/auth/sms",
            baseUrl = "leo",
            params = listOf(
                ApiParam("YFD_U", "Long", ParamIn.QUERY, required = false),
                ApiParam("phone", "String", ParamIn.FIELD, required = false),
                ApiParam("verification", "String", ParamIn.FIELD, required = false),
                ApiParam("autoRegister", "Boolean", ParamIn.FIELD, defaultValue = "false"),
            ),
            note = "短信验证码登录",
        ))
        add(ApiEndpoint(
            group = "登录网关",
            name = "tokenLogin",
            method = "POST",
            path = "/leo-gateway/android/auth/token-login",
            baseUrl = "leo",
            params = listOf(
                ApiParam("YFD_U", "Long", ParamIn.QUERY, required = false),
                ApiParam("token", "String", ParamIn.FIELD, required = false),
            ),
            note = "token 登录（会话续期 / 第三方渠道换登录态）",
        ))
        add(ApiEndpoint(
            group = "登录网关",
            name = "aliLoginWithOperator",
            method = "POST",
            path = "/leo-gateway/android/auth/ali-login-with-operator",
            baseUrl = "leo",
            params = listOf(
                ApiParam("operatorId", "Int", ParamIn.FIELD),
                ApiParam("token", "String", ParamIn.FIELD),
                ApiParam("autoRegister", "Boolean", ParamIn.FIELD, defaultValue = "false"),
                ApiParam("pMask", "String", ParamIn.FIELD, required = false, note = "脱敏标记位（推测）"),
            ),
            note = "阿里一键登录（运营商通道）。注意无 YFD_U 参数",
        ))
        add(ApiEndpoint(
            group = "登录网关",
            name = "cmccLogin",
            method = "POST",
            path = "/leo-gateway/android/auth/cmcc",
            baseUrl = "leo",
            params = listOf(
                ApiParam("token", "String", ParamIn.FIELD),
                ApiParam("phone", "String", ParamIn.FIELD, required = false),
                ApiParam("autoRegister", "Boolean", ParamIn.FIELD, defaultValue = "false"),
                ApiParam("pMask", "String", ParamIn.FIELD, required = false, note = "脱敏标记位（推测）"),
            ),
            note = "中国移动一键登录",
        ))

        // ---- LeoProfileApiService（主域）----
        add(ApiEndpoint(
            group = "用户资料",
            name = "getUserInfo",
            method = "GET",
            path = "/leo-profile/android/user-infos",
            baseUrl = "leo",
            note = "拉当前用户资料，返回 UserVO",
        ))
        add(ApiEndpoint(
            group = "用户资料",
            name = "getUserSetting",
            method = "GET",
            path = "/leo-profile/android/user-setting",
            baseUrl = "leo",
            note = "拉用户设置",
        ))
        add(ApiEndpoint(
            group = "用户资料",
            name = "leoLogout",
            method = "POST",
            path = "/leo-profile/android/logout",
            baseUrl = "leo",
            note = "主域侧登出",
        ))

        // ---- LeoExerciseCommonLegacyApiService（主域）----
        add(ApiEndpoint(
            group = "通用练习",
            name = "getCurrentUserExp",
            method = "GET",
            path = "/leo-star/android/exercise/rank/pre-fetch",
            baseUrl = "leo",
            note = "拉当前用户经验值",
        ))
        add(ApiEndpoint(
            group = "通用练习",
            name = "getCurrentUserTasks",
            method = "GET",
            path = "/leo-star/android/exercise/task/home",
            baseUrl = "leo",
            note = "拉当前用户任务（首页任务卡）",
        ))
        add(ApiEndpoint(
            group = "通用练习",
            name = "postChineseExercise",
            method = "POST",
            path = "/leo-chinese/android/exercises/homePage/sync",
            baseUrl = "leo",
            params = listOf(
                ApiParam("textbookKeypointId", "Long", ParamIn.QUERY),
            ),
            deprecated = true,
            note = "语文练习首页同步，返回 Call<Int>",
        ))
        add(ApiEndpoint(
            group = "通用练习",
            name = "postSavedExp",
            method = "POST",
            path = "/leo-star/android/exercise/rank/login/attend",
            baseUrl = "leo",
            params = listOf(
                ApiParam("body", "JSON", ParamIn.BODY, note = "LeoTodayExerciseListData"),
            ),
            needEncode = true,
            note = "上报今日练习列表。请求体需 native 编码（@NeedEncode）",
        ))

        // ---- LeoEnglishExerciseWritingApiService（主域）----
        add(ApiEndpoint(
            group = "英语练习",
            name = "getEnglishDictationExerciseResult",
            method = "GET",
            path = "/leo-english/android/exercises/history/listen/{exerciseId}",
            baseUrl = "leo",
            params = listOf(ApiParam("exerciseId", "Long", ParamIn.PATH)),
            note = "拉听写练习历史结果",
        ))
        add(ApiEndpoint(
            group = "英语练习",
            name = "getEnglishExercisesDictationContentList",
            method = "GET",
            path = "/leo-english/android/exercises/listen/mergedx",
            baseUrl = "leo",
            params = listOf(ApiParam("unitIds", "String", ParamIn.QUERY)),
            note = "拉听写内容列表",
        ))
        add(ApiEndpoint(
            group = "英语练习",
            name = "getEnglishExercisesSections",
            method = "GET",
            path = "/leo-english/android/exercise/{type}",
            baseUrl = "leo",
            params = listOf(
                ApiParam("type", "Int", ParamIn.PATH),
                ApiParam("grade", "Int", ParamIn.QUERY),
                ApiParam("semester", "Int", ParamIn.QUERY),
                ApiParam("book", "Int", ParamIn.QUERY),
            ),
            note = "按类型拉练习章节",
        ))
        add(ApiEndpoint(
            group = "英语练习",
            name = "postEnglishDictationExercise",
            method = "POST",
            path = "/leo-english/android/exercises/listen",
            baseUrl = "leo",
            params = listOf(ApiParam("body", "JSON", ParamIn.BODY)),
            note = "提交听写练习",
        ))
        add(ApiEndpoint(
            group = "英语练习",
            name = "postEnglishDictationRetrainExercise",
            method = "POST",
            path = "/leo-english/android/exercises/listen/wrong",
            baseUrl = "leo",
            params = listOf(ApiParam("body", "JSON", ParamIn.BODY)),
            note = "提交听写重练（错题重做）",
        ))
        add(ApiEndpoint(
            group = "英语练习",
            name = "repeatEnglishOnlineDictation",
            method = "GET",
            path = "/leo-english/android/handwriting/exercises/listen/repeat/{exerciseId}",
            baseUrl = "leo",
            params = listOf(ApiParam("exerciseId", "Long", ParamIn.PATH)),
            note = "重做线上听写",
        ))
        add(ApiEndpoint(
            group = "英语练习",
            name = "reportEnglishOnlineDictationStart",
            method = "POST",
            path = "/leo-english/android/handwriting/exercises/listen/lastUnits",
            baseUrl = "leo",
            params = listOf(ApiParam("body", "JSON", ParamIn.BODY)),
            note = "上报线上听写开始",
        ))

        // ---- LeoUserApiService（主域）----
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "register",
            method = "POST",
            path = "/leo-auth/android/user-devices",
            baseUrl = "leo",
            params = listOf(
                ApiParam("device", "String", ParamIn.QUERY),
                ApiParam("deviceInfo", "String", ParamIn.QUERY),
            ),
            deprecated = true,
            note = "注册设备，返回 Call<Void>",
        ))
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "getUserInfo (legacy)",
            method = "GET",
            path = "/leo-profile/android/user-infos",
            baseUrl = "leo",
            deprecated = true,
            note = "旧版 Call<UserVO>，与 profile.getUserInfo 同路径",
        ))
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "isParentCertificated",
            method = "GET",
            path = "/leo-profile/android/user-id-card",
            baseUrl = "leo",
            note = "查询家长认证状态",
        ))
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "closeParentCertification",
            method = "GET",
            path = "/leo-profile/android/user-id-card/delete",
            baseUrl = "leo",
            note = "关闭家长认证",
        ))
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "getPaperFreeTrials",
            method = "GET",
            path = "/leo-exam/android/photograph/paper/experience",
            baseUrl = "leo",
            note = "拉试卷免费试用次数",
        ))
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "getUserPendants",
            method = "GET",
            path = "/leo-alchemy-account/android/user-pendant",
            baseUrl = "leo",
            params = listOf(ApiParam("biz", "Int", ParamIn.QUERY)),
            note = "拉用户头像挂件列表",
        ))
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "getUserVipInfoV2",
            method = "GET",
            path = "/leo-alchemy-account/android/vip/user/info/v2",
            baseUrl = "leo",
            params = listOf(ApiParam("bizTypes", "List<Int>", ParamIn.QUERY, note = "多值，逗号分隔")),
            note = "拉用户 VIP 信息 v2",
        ))
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "replacePhone",
            method = "PUT",
            path = "/leo-profile/android/user-infos/replace-phone",
            baseUrl = "leo",
            params = listOf(
                ApiParam("phone", "String", ParamIn.QUERY),
                ApiParam("verification", "String", ParamIn.QUERY),
            ),
            note = "更换手机号",
        ))
        add(ApiEndpoint(
            group = "用户（扩展）",
            name = "submitUserPendant",
            method = "PUT",
            path = "/leo-alchemy-account/android/user-pendant",
            baseUrl = "leo",
            params = listOf(
                ApiParam("pendantId", "Int", ParamIn.QUERY),
                ApiParam("pendantType", "Int", ParamIn.QUERY),
            ),
            note = "提交用户头像挂件",
        ))
    }

    /** 按分组聚合，UI 按组折叠展示。 */
    val grouped: Map<String, List<ApiEndpoint>> = all.groupBy { it.group }

    /** 按名字模糊搜索。 */
    fun search(keyword: String): List<ApiEndpoint> {
        if (keyword.isBlank()) return all
        val k = keyword.lowercase()
        return all.filter {
            it.name.lowercase().contains(k) ||
                it.path.lowercase().contains(k) ||
                it.group.lowercase().contains(k)
        }
    }
}