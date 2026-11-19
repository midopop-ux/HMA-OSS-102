package icu.nullptr.hidemyapplist.xposed.hook

import icu.nullptr.hidemyapplist.xposed.XposedCompat.Unhook
import icu.nullptr.hidemyapplist.xposed.XposedCompat.findMethodOrNull
import icu.nullptr.hidemyapplist.xposed.hookBefore
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logV
import icu.nullptr.hidemyapplist.xposed.XposedConstants.ZYGOTE_PROCESS_CLASS

class ZygoteHook(private val service: HMAService): IFrameworkHook {
    companion object {
        private const val TAG = "ZygoteHook"
    }

    private val hooks = mutableListOf<Unhook>()

    override fun load() {
        findMethodOrNull(ZYGOTE_PROCESS_CLASS) {
            name == "start"
        }?.hookBefore { param ->
            logV(TAG) { "@startZygoteProcess: Starting ${param.args.contentToString()}" }

            // ignore if the GIDs array is null
            val gIDsIndex = param.args.indexOfFirst { it is IntArray }
            if (gIDsIndex < 0) return@hookBefore

            val caller = param.args.lastOrNull { it is String } as String? ?: return@hookBefore
            var perms = service.getRestrictedZygotePermissions(caller) ?: return@hookBefore
            if (perms.isNotEmpty()) {
                val gIDs = param.args[gIDsIndex] as IntArray

                // add more security, reject if not available in GID_PAIRS
                perms = perms.filter { Constants.GID_PAIRS.containsValue(it) }

                logD(TAG) { "@startZygoteProcess: GIDs are ${gIDs.contentToString()}, removing $perms now" }
                param.args[gIDsIndex] = gIDs.filter { it !in perms }.toIntArray()
                service.increaseOthersFilterCount(caller)
            }
        }?.let {
            logD(TAG) { "Loaded ZygoteProcess start hook!" }
            hooks += it
        }
    }

    override fun unload() {
        hooks.forEach(Unhook::unhook)
        hooks.clear()
    }
}
