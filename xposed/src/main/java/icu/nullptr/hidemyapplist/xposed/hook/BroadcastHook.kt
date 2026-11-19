package icu.nullptr.hidemyapplist.xposed.hook

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import icu.nullptr.hidemyapplist.common.Utils.generateRandomHex
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.XposedCompat
import icu.nullptr.hidemyapplist.xposed.XposedCompat.Unhook
import icu.nullptr.hidemyapplist.xposed.XposedConstants.ACTION_USB_STATE
import icu.nullptr.hidemyapplist.xposed.XposedConstants.ACTIVITY_MANAGER_SERVICE_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.BROADCAST_PROCESS_QUEUE_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.BROADCAST_QUEUE_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.BROADCAST_QUEUE_IMPL_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.USB_FUNCTION_ADB
import icu.nullptr.hidemyapplist.xposed.hookBefore
import org.frknkrc44.hma_oss.common.BuildConfig

class BroadcastHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "BroadcastHook"
    }

    private val hookList = mutableSetOf<Unhook>()

    override fun load() {
        logI(TAG) { "Load hook" }

        // 1. Hook Broadcast Queue
        val targetClassNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            listOf(BROADCAST_PROCESS_QUEUE_CLASS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(BROADCAST_QUEUE_IMPL_CLASS, BROADCAST_QUEUE_CLASS)
        } else {
            listOf(BROADCAST_QUEUE_CLASS)
        }

        val methodNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            listOf("enqueueOutgoingBroadcast", "enqueueOrReplaceBroadcast")
        } else {
            listOf("enqueueParallelBroadcastLocked", "enqueueOrderedBroadcastLocked")
        }

        for (className in targetClassNames) {
            val clazz = runCatching { XposedCompat.findClass(className) }.getOrNull() ?: continue
            for (methodName in methodNames) {
                runCatching {
                    XposedCompat.findMethodOrNull(clazz) { name == methodName }?.let { method ->
                        hookList += method.hookBefore { param ->
                            handleEnqueueBroadcast(param)
                        }
                    }
                }
            }
        }

        // 2. Hook broadcastIntentLocked for USB state
        runCatching {
            val amsClass = XposedCompat.findClass(ACTIVITY_MANAGER_SERVICE_CLASS)
            for (m in amsClass.declaredMethods) {
                if (m.name == "broadcastIntentLocked") {
                    hookList += m.hookBefore { param ->
                        for (arg in param.args) {
                            if (arg is Intent) {
                                changeUsbStateBroadcast(arg)
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleEnqueueBroadcast(param: XposedCompat.HookParam) {
        if (param.args.isEmpty()) return
        // In queue methods, the broadcast record is typically the 1st or 2nd argument
        val record = param.args.firstOrNull { it?.javaClass?.simpleName?.contains("BroadcastRecord") == true }
            ?: (if (param.args.size > 1) param.args[1] else null) ?: return

        val caller = runCatching {
            val field = record.javaClass.getDeclaredField("callerPackage").apply { isAccessible = true }
            field.get(record) as? String
        }.getOrNull() ?: return

        val component = runCatching {
            val field = record.javaClass.getDeclaredField("targetComp").apply { isAccessible = true }
            field.get(record) as? ComponentName
        }.getOrNull() ?: return

        val targetApp = component.packageName

        if (service.shouldHideActivityLaunch(caller, targetApp)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                param.method.name == "enqueueOrderedBroadcastLocked") {
                // ignore the broadcasts for replacement if they have no receivers
                val receivers = runCatching {
                    val field = record.javaClass.getDeclaredField("receivers").apply { isAccessible = true }
                    field.get(record) as? List<*>
                }.getOrNull()
                if (receivers.isNullOrEmpty()) return
            }

            logD(TAG) { "@${param.method.name}: insecure broadcast query from $caller, target: $component" }

            runCatching {
                val intentField = record.javaClass.getDeclaredField("intent").apply { isAccessible = true }
                (intentField.get(record) as? Intent)?.apply {
                    this.component = null
                    this.`package` = "${BuildConfig.APP_PACKAGE_NAME}.${generateRandomHex(4)}"
                }
                service.increaseALFilterCount(caller)
            }
        }
    }

    private fun changeUsbStateBroadcast(intent: Intent) {
        if (service.config.disableActivityLaunchProtection) return
        if (intent.action == ACTION_USB_STATE) {
            intent.removeExtra(USB_FUNCTION_ADB)
        }
    }

    override fun unload() {
        hookList.forEach { it.unhook() }
        hookList.clear()
    }
}
