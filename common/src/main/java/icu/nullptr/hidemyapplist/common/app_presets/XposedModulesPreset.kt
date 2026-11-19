package icu.nullptr.hidemyapplist.common.app_presets

import android.content.pm.ApplicationInfo
import icu.nullptr.hidemyapplist.common.Utils.checkSplitPackages

class XposedModulesPreset : BasePreset(NAME) {
    companion object {
        const val NAME = "xposed"
    }

    override val exactPackageNames = setOf(
        org.frknkrc44.hma_oss.common.BuildConfig.APP_PACKAGE_NAME,
    )

    override fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean {
        return checkSplitPackages(appInfo) { _, zipFile ->
            // Legacy Xposed method
            if (zipFile.getEntry("assets/xposed_init") != null) {
                return@checkSplitPackages true
            }

            // Modern libxposed method
            if (zipFile.getEntry("META-INF/xposed/java_init.list") != null) {
                return@checkSplitPackages true
            }

            // LSPosed method
            if (zipFile.getEntry("META-INF/xposed/module.prop") != null) {
                return@checkSplitPackages true
            }

            return@checkSplitPackages false
        }
    }
}
