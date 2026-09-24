package cn.apixiaoyuan.app

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import cn.apixiaoyuan.app.core.network.RetrofitFactory
import cn.apixiaoyuan.app.core.network.NetworkConfig
import cn.apixiaoyuan.app.core.auth.DeviceFingerprint
import cn.apixiaoyuan.app.core.database.AppDatabase
import cn.apixiaoyuan.app.core.native.NativeDecodeInstaller
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
import cn.apixiaoyuan.app.core.session.SessionStore

/**
 * 全局 Application。
 *
 * 莫奈取色的产物（ColorScheme）在这里以 Compose 可观察状态持有，
 * 壁纸变更或用户手动改种子色时更新此状态，全应用重组。
 */
class App : Application() {

    companion object {
        lateinit var instance: App
            private set

        /** 当前莫奈色板，由壁纸取色或手动种子色驱动，全局 Compose 观察此状态重组。 */
        var colorScheme by mutableStateOf<ColorScheme?>(null)

        /** 当前取色风格，默认 Material You 的 TonalSpot。 */
        var paletteStyle by mutableStateOf(PaletteStyle.TonalSpot)

        /** 色板规范版本，2021 与 2025 两套 Tone 阶梯。 */
        var colorSpec by mutableStateOf(ColorSpec.SpecVersion.SPEC_2025)

        /** 种子色，取色失败或用户手动指定时使用。 */
        var seedColor by mutableStateOf(Color(0xFF6750A4))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 会话存储：必须在 RetrofitFactory.init 之前，因为 init 会立即
        // 取用 SessionStore.snapshot() 作为 sessionProvider 的闭包。
        SessionStore.init(this)

        // 设备指纹：YFD_U 的取值来源（`Lds/i3` 链路复刻）。
        // 登录/发码接口都用它作设备级频控键，必须在任何登录动作之前就绪。
        DeviceFingerprint.init(this)

        // 网络底座：必须先于任何 ServiceLocator.xxx 的首次访问。
        // 域名来自 NetworkConfig，由 mg/h.smali 的 d()/w() 方法链逐行确证。
        RetrofitFactory.init(
            leoBaseUrl = NetworkConfig.leoBaseUrl(),
            ytkBaseUrl = NetworkConfig.ytkBaseUrl(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            sessionProvider = { SessionStore.snapshot() },   // R2 已解：cookie 承载登录态
            logging = BuildConfig.DEBUG,
        )

        // 解码桥：把 libContentEncoder.so 的真实解码器装进网络层 DecodeBridge。
        // 必须在 RetrofitFactory.init 之后 —— init 会构造 NeedDecodeInterceptor，
        // 而拦截器读取的是 DecodeBridge 这个全局单例；先装解码器再发第一个请求即可。
        // native 库加载失败时静默退回恒等实现，不阻断启动。
        NativeDecodeInstaller.install()

        // 数据库：模块 12-13。八表实体 + SampleDao + AppDatabase。
        // 只建库不迁数据，初始化无副作用；放最后，不干扰网络与会话链路。
        AppDatabase.init(this)

        // 「老挂戏老叟」功能开关：纯本地配置（SharedPreferences），
        // 与网络/会话链路无耦合，放最后初始化即可。
        // 必须在首次进入设置页或练习页之前就绪，否则 OldSimianPrefs.prefs() 会抛错。
        OldSimianPrefs.init(this)
    }
}
