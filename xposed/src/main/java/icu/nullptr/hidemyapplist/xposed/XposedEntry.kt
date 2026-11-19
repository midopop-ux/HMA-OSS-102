package icu.nullptr.hidemyapplist.xposed

import android.content.pm.IPackageManager
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logE
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import kotlin.concurrent.thread

private const val TAG = "HMA-XposedEntry"

@Suppress("unused")
class XposedEntry : XposedModule() {
    private var isHooked = false
    private val targetsLeft = mutableListOf("package", "package_native")
    private val targetStorage = mutableMapOf<String, Any?>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        XposedCompat.init(this)
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        XposedCompat.init(this, param.classLoader)
        initHooks(param.classLoader)
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName == Constants.ANDROID_PACKAGE_NAME || param.packageName == "system") {
            XposedCompat.init(this, param.defaultClassLoader)
            initHooks(param.defaultClassLoader)
        }
    }

    @Synchronized
    private fun initHooks(classLoader: ClassLoader) {
        if (isHooked) return
        logI(TAG) { "Hook entry (libxposed API 102)" }

        var serviceManagerHook: XposedCompat.Unhook? = null
        try {
            serviceManagerHook = XposedCompat.findMethod("android.os.ServiceManager", classLoader = classLoader) {
                name == "addService"
            }.hookBefore { param ->
                val name = param.args[0] as String
                if (targetsLeft.contains(name)) {
                    when (name) {
                        "package", "package_native" -> {
                            targetStorage[name] = param.args[1]
                            targetsLeft.remove(name)
                        }
                        else -> {
                            // skip if there is no package_native available
                            if (targetStorage.containsKey("package")) {
                                targetsLeft.remove("package_native")
                            }
                        }
                    }
                }

                if (targetsLeft.isEmpty()) {
                    serviceManagerHook?.unhook()
                    val pms = targetStorage["package"] as IPackageManager
                    val pmn = targetStorage["package_native"]
                    logD(TAG) { "Got pms: $pms, $pmn" }
                    thread {
                        runCatching {
                            UserService.register(pms, pmn)
                            targetStorage.clear()
                            logI(TAG) { "User service started" }
                        }.onFailure {
                            logE(TAG, it) { "System service crashed" }
                        }
                    }
                }
            }
            isHooked = true
        } catch (t: Throwable) {
            logE(TAG, t) { "Failed to hook ServiceManager.addService" }
        }
    }
}
