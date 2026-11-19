package icu.nullptr.hidemyapplist.xposed

import android.app.ActivityThread
import android.os.Binder
import android.os.Build
import icu.nullptr.hidemyapplist.xposed.XposedCompat.findField
import icu.nullptr.hidemyapplist.xposed.XposedHelpers.callMethod
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.Utils

object Utils4Xposed {
    private val packageNameAccessors = java.util.concurrent.ConcurrentHashMap<Class<*>, (Any) -> String?>()

    fun getPackageNameFromPackageSettings(packageSettings: Any?): String? {
        if (packageSettings == null) return null

        val accessor = packageNameAccessors.getOrPut(packageSettings.javaClass) {
            resolvePackageNameAccessor(packageSettings.javaClass)
        }
        return runCatching { accessor(packageSettings) }.getOrNull()
    }

    private fun resolvePackageNameAccessor(clazz: Class<*>): (Any) -> String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val method = runCatching { clazz.getMethod("getPackageName") }.getOrNull()
            if (method != null && method.parameterCount == 0) {
                method.isAccessible = true
                return { method.invoke(it) as? String }
            }
        }

        for (name in arrayOf("mName", "name")) {
            val field = runCatching {
                var c: Class<*>? = clazz
                var found: java.lang.reflect.Field? = null
                while (c != null && found == null) {
                    try {
                        found = c.getDeclaredField(name)
                    } catch (_: NoSuchFieldException) {
                        c = c.superclass
                    }
                }
                found?.takeIf { it.type == String::class.java }
            }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return { field.get(it) as? String }
            }
        }

        return { null }
    }

    fun getPackageManager() = ActivityThread.currentActivityThread().application.packageManager!!

    fun getCallingApps(service: HMAService): Array<String> {
        return getCallingApps(service, Binder.getCallingUid())
    }

    fun getCallingApps(service: HMAService, callingUid: Int): Array<String> {
        if (callingUid == Constants.UID_SYSTEM) return arrayOf()
        return Utils.binderLocalScope {
            service.pms.getPackagesForUid(callingUid)
        } ?: arrayOf()
    }
}
