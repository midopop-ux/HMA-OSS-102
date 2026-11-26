package icu.nullptr.hidemyapplist.xposed.hook

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.content.pm.ParceledListSlice
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.Logcat.logD
import icu.nullptr.hidemyapplist.xposed.Logcat.logI
import icu.nullptr.hidemyapplist.xposed.Utils4Xposed
import icu.nullptr.hidemyapplist.xposed.XposedCompat
import icu.nullptr.hidemyapplist.xposed.XposedCompat.Unhook
import icu.nullptr.hidemyapplist.xposed.XposedConstants.USAGE_STATS_BINDER_SERVICE_CLASS
import icu.nullptr.hidemyapplist.xposed.XposedConstants.USAGE_STATS_SERVICE_CLASS
import icu.nullptr.hidemyapplist.xposed.hookAfter
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicReference

class UsageStatsHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "UsageStatsHook"
    }

    private val hookList = mutableSetOf<Unhook>()
    private val lastFilteredApp = AtomicReference<String?>(null)
    private val eventsToWriteField: Field? by lazy {
        runCatching {
            UsageEvents::class.java.getDeclaredField("mEventsToWrite").apply { isAccessible = true }
        }.getOrNull()
    }

    override fun load() {
        logI(TAG) { "Load hook" }

        val targetClass = runCatching {
            XposedCompat.findClass(USAGE_STATS_BINDER_SERVICE_CLASS)
        }.getOrNull() ?: runCatching {
            XposedCompat.findClass(USAGE_STATS_SERVICE_CLASS)
        }.getOrNull()

        if (targetClass == null) {
            logD(TAG) { "UsageStatsService class not found, skipping hook" }
            return
        }

        // 1. Hook queryUsageStats
        runCatching {
            XposedCompat.findMethodOrNull(targetClass) {
                name == "queryUsageStats"
            }?.let { method ->
                hookList += method.hookAfter { param ->
                    @Suppress("UNCHECKED_CAST")
                    val result = param.result as? ParceledListSlice<UsageStats> ?: return@hookAfter
                    val caller = findCaller(param.args) ?: return@hookAfter
                    if (!service.isHookEnabled(caller)) return@hookAfter

                    logD(TAG) { "@${param.method.name}: $caller requested stats" }

                    val filtered = result.list.filterTo(mutableListOf()) {
                        !service.shouldHide(caller, it.packageName)
                    }

                    val count = result.list.size - filtered.size
                    if (count > 0) {
                        val last = lastFilteredApp.getAndSet(caller)
                        if (last != caller) logI(TAG) { "@${param.method.name}: query from $caller" }
                        logD(TAG) { "@${param.method.name}: $count entries hidden from $caller" }

                        service.increasePMFilterCount(caller, count)
                        param.result = ParceledListSlice(filtered)
                    }
                }
            }
        }

        // 2. Hook queryEvents and variants
        val eventMethods = listOf("queryEvents", "queryEventsForUser", "queryEventsWithFilter", "queryEventsForPackageForUser")
        for (mName in eventMethods) {
            runCatching {
                XposedCompat.findMethodOrNull(targetClass) {
                    name == mName
                }?.let { method ->
                    hookList += method.hookAfter { param ->
                        val result = param.result as? UsageEvents ?: return@hookAfter
                        val caller = findCaller(param.args) ?: return@hookAfter
                        applyUsageEventsHiding(param.method.name, result, caller)
                    }
                }
            }
        }
    }

    private fun findCaller(args: Array<Any?>): String? {
        val callers = Utils4Xposed.getCallingApps(service)
        callers.firstOrNull { service.isHookEnabled(it) }?.let { return it }
        if (callers.isNotEmpty()) {
            return callers[0]
        }
        for (i in args.indices.reversed()) {
            val arg = args[i]
            if (arg is String && arg.contains(".")) {
                return arg
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyUsageEventsHiding(methodName: String, events: UsageEvents, caller: String) {
        if (!service.isHookEnabled(caller)) return

        val field = eventsToWriteField ?: return
        val eventsList = field.get(events) as? List<UsageEvents.Event> ?: return

        val filtered = eventsList.filterTo(mutableListOf()) {
            !service.shouldHide(caller, it.packageName)
        }

        val count = eventsList.size - filtered.size
        if (count > 0) {
            val last = lastFilteredApp.getAndSet(caller)
            if (last != caller) logI(TAG) { "@$methodName: query from $caller" }
            logD(TAG) { "@$methodName: $count entries hidden from $caller" }

            service.increasePMFilterCount(caller, count)
            field.set(events, filtered)
        }
    }

    override fun unload() {
        hookList.forEach { it.unhook() }
        hookList.clear()
    }
}
