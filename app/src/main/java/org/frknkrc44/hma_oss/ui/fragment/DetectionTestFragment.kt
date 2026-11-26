package org.frknkrc44.hma_oss.ui.fragment

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.androidbroadcast.vbpd.viewBinding
import icu.nullptr.hidemyapplist.MyApp.Companion.hmaApp
import icu.nullptr.hidemyapplist.common.AppPresets
import icu.nullptr.hidemyapplist.common.Constants
import icu.nullptr.hidemyapplist.common.JsonConfig
import icu.nullptr.hidemyapplist.common.SettingsPresets
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.common.app_presets.DetectorAppsPreset
import icu.nullptr.hidemyapplist.common.app_presets.RootAppsPreset
import icu.nullptr.hidemyapplist.common.app_presets.SDhizukuAppsPreset
import icu.nullptr.hidemyapplist.common.app_presets.SuspiciousAppsPreset
import icu.nullptr.hidemyapplist.common.app_presets.XposedModulesPreset
import icu.nullptr.hidemyapplist.service.ConfigManager
import icu.nullptr.hidemyapplist.service.PrefManager
import icu.nullptr.hidemyapplist.service.ServiceClient
import icu.nullptr.hidemyapplist.ui.util.ThemeUtils.getColor
import icu.nullptr.hidemyapplist.ui.util.ThemeUtils.homeItemBackgroundColor
import icu.nullptr.hidemyapplist.ui.util.ThemeUtils.themeColor
import icu.nullptr.hidemyapplist.ui.util.dp2Px
import icu.nullptr.hidemyapplist.ui.util.navController
import icu.nullptr.hidemyapplist.ui.util.navigate
import icu.nullptr.hidemyapplist.ui.util.setEdge2EdgeFlags
import icu.nullptr.hidemyapplist.ui.util.setupToolbar
import icu.nullptr.hidemyapplist.util.PackageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.frknkrc44.hma_oss.R
import org.frknkrc44.hma_oss.common.BuildConfig
import org.frknkrc44.hma_oss.databinding.FragmentDetectionTestBinding
import org.frknkrc44.hma_oss.databinding.ItemDetectionResultBinding

class DetectionTestFragment : Fragment(R.layout.fragment_detection_test) {

    private enum class CheckStatus {
        PASS,
        INFO,
        WARN,
        FAIL,
    }

    private data class AuditRow(
        val title: String,
        val subtitle: String,
        val status: CheckStatus,
        val iconDrawable: Drawable? = null,
        val onClick: (() -> Unit)? = null,
    )

    private data class AuditReport(
        val overallStatus: CheckStatus,
        val summaryTitle: String,
        val summarySubtitle: String,
        val envRows: List<AuditRow>,
        val resultRows: List<AuditRow>,
    )

    private val binding by viewBinding(FragmentDetectionTestBinding::bind)

    private var selectedTargetApp: String? = null
    private var auditAllMode = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.toolbar) {
            setupToolbar(
                toolbar = this,
                title = getString(R.string.home_detection_test),
                navigationIcon = R.drawable.baseline_arrow_back_24,
                navigationOnClick = { navController.navigateUp() },
            )
            inflateMenu(R.menu.menu_detection_test)
            setOnMenuItemClickListener {
                if (it.itemId == R.id.menu_refresh) {
                    runAudit()
                    true
                } else {
                    false
                }
            }
        }

        binding.btnSelectApp.setOnClickListener {
            showAppPickerDialog()
        }

        binding.btnAuditAll.setOnClickListener {
            auditAllMode = true
            selectedTargetApp = null
            runAudit()
        }

        binding.btnConfigureApp.setOnClickListener {
            val pkg = selectedTargetApp ?: return@setOnClickListener
            val args = AppSettingsV2FragmentArgs(pkg)
            navigate(R.id.nav_app_settings, args.toBundle())
        }

        setEdge2EdgeFlags(binding.root)
    }

    override fun onResume() {
        super.onResume()
        runAudit()
    }

    private fun showAppPickerDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allApps = withContext(Dispatchers.Default) {
                val installed = PackageHelper.appList.first()
                installed
                    .filter { it != BuildConfig.APP_PACKAGE_NAME && it !in Constants.packagesShouldNotHide }
                    .sortedWith(
                        Comparator.comparing<String, Boolean> { ConfigManager.isHideEnabled(it) }
                            .reversed()
                            .then(PackageHelper.Comparators.byLabel)
                    )
            }

            if (allApps.isEmpty() || !isAdded) return@launch

            val labels = allApps.map { pkg ->
                val label = PackageHelper.loadAppLabel(pkg)
                val prefix = if (ConfigManager.isHideEnabled(pkg)) "✓ " else ""
                "$prefix$label\n$pkg"
            }.toTypedArray()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.detection_test_select_app)
                .setItems(labels) { _, which ->
                    auditAllMode = false
                    selectedTargetApp = allApps[which]
                    runAudit()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun runAudit() {
        if (!isAdded) return
        binding.btnConfigureApp.isVisible = selectedTargetApp != null && !auditAllMode

        val safeCtx: Context = hmaApp
        viewLifecycleOwner.lifecycleScope.launch {
            val report = withContext(Dispatchers.Default) {
                buildAuditReport(safeCtx)
            }
            if (!isAdded || view == null) return@launch
            renderReport(report)
        }
    }

    private suspend fun buildAuditReport(ctx: Context): AuditReport {
        val installedSet = PackageHelper.appList.first().toSet()
        val rawConfig = ConfigManager.getRawConfig(true)
        val adbEnabled = runCatching {
            Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
        }.getOrDefault(false)
        val envRows = buildEnvironmentAuditRows(ctx, installedSet, rawConfig, adbEnabled)

        val presetPackages = loadPresetPackagesMap()
        val sensitivePackages = collectInstalledSensitiveApps(installedSet, presetPackages)

        val currentTarget = selectedTargetApp
        val resultRows = mutableListOf<AuditRow>()
        var overallStatus = envRows.fold(CheckStatus.PASS) { acc, row -> maxSeverity(acc, row.status) }
        val summaryTitle: String
        val summarySubtitle: String

        if (currentTarget != null && !auditAllMode) {
            val targetAudit = auditSingleApp(ctx, currentTarget, rawConfig, presetPackages, sensitivePackages, installedSet, adbEnabled, collectDetails = true)
            resultRows.addAll(targetAudit.rows)
            overallStatus = maxSeverity(overallStatus, targetAudit.worstStatus)
            summaryTitle = statusHeadline(ctx, overallStatus)
            summarySubtitle = ctx.getString(
                R.string.detection_test_summary_desc,
                sensitivePackages.size,
                targetAudit.hiddenCount,
                targetAudit.exposedCount,
            )
        } else {
            val scopedApps = rawConfig.scope.keys.filter { it in installedSet }.sortedWith(PackageHelper.Comparators.byLabel)
            if (scopedApps.isEmpty()) {
                overallStatus = maxSeverity(overallStatus, CheckStatus.WARN)
                summaryTitle = statusHeadline(ctx, overallStatus)
                summarySubtitle = ctx.getString(R.string.detection_test_no_scoped_apps)
                resultRows += AuditRow(
                    title = ctx.getString(R.string.detection_test_no_scoped_apps),
                    subtitle = ctx.getString(R.string.detection_test_select_app),
                    status = CheckStatus.INFO,
                )
            } else {
                var cleanCount = 0
                var flaggedCount = 0
                for (pkg in scopedApps) {
                    val appAudit = auditSingleApp(ctx, pkg, rawConfig, presetPackages, sensitivePackages, installedSet, adbEnabled, collectDetails = false)
                    overallStatus = maxSeverity(overallStatus, appAudit.worstStatus)
                    val rowStatus = appAudit.worstStatus
                    if (rowStatus == CheckStatus.PASS || rowStatus == CheckStatus.INFO) {
                        cleanCount++
                    } else {
                        flaggedCount++
                    }
                    resultRows += AuditRow(
                        title = PackageHelper.loadAppLabel(pkg),
                        subtitle = "$pkg • " + ctx.getString(
                            R.string.detection_test_app_exposure_item,
                            appAudit.exposedCount,
                            appAudit.hiddenCount,
                        ),
                        status = rowStatus,
                        iconDrawable = PackageHelper.loadAppIcon(pkg),
                        onClick = {
                            auditAllMode = false
                            selectedTargetApp = pkg
                            runAudit()
                        },
                    )
                }
                summaryTitle = statusHeadline(ctx, overallStatus)
                summarySubtitle = ctx.getString(
                    R.string.detection_test_all_scoped_summary,
                    scopedApps.size,
                    cleanCount,
                    flaggedCount,
                )
            }
        }

        return AuditReport(overallStatus, summaryTitle, summarySubtitle, envRows, resultRows)
    }

    private fun buildEnvironmentAuditRows(
        ctx: Context,
        installedSet: Set<String>,
        rawConfig: JsonConfig,
        adbEnabled: Boolean,
    ): List<AuditRow> {
        val rows = mutableListOf<AuditRow>()
        val serviceVer = ServiceClient.serviceVersion
        val serviceRow = when {
            serviceVer <= 0 -> AuditRow(
                title = ctx.getString(R.string.detection_test_service_title),
                subtitle = ctx.getString(R.string.detection_test_service_off),
                status = CheckStatus.FAIL,
            )
            serviceVer < BuildConfig.SERVICE_VERSION -> AuditRow(
                title = ctx.getString(R.string.detection_test_service_title),
                subtitle = ctx.getString(R.string.detection_test_service_old, serviceVer),
                status = CheckStatus.WARN,
            )
            else -> AuditRow(
                title = ctx.getString(R.string.detection_test_service_title),
                subtitle = ctx.getString(R.string.detection_test_service_ok, serviceVer, ServiceClient.filterCount),
                status = CheckStatus.PASS,
            )
        }
        rows += serviceRow

        val installedConflicts = Utils.conflictedModules.filter { it in installedSet }
        rows += if (installedConflicts.isEmpty()) {
            AuditRow(
                title = ctx.getString(R.string.detection_test_conflict_title),
                subtitle = ctx.getString(R.string.detection_test_conflict_none),
                status = CheckStatus.PASS,
            )
        } else {
            AuditRow(
                title = ctx.getString(R.string.detection_test_conflict_title),
                subtitle = ctx.getString(R.string.detection_test_conflict_found, installedConflicts.joinToString()),
                status = CheckStatus.FAIL,
            )
        }

        rows += if (rawConfig.disableActivityLaunchProtection) {
            AuditRow(
                title = ctx.getString(R.string.detection_test_protection_title),
                subtitle = ctx.getString(R.string.detection_test_protection_off),
                status = CheckStatus.WARN,
            )
        } else {
            AuditRow(
                title = ctx.getString(R.string.detection_test_protection_title),
                subtitle = ctx.getString(R.string.detection_test_protection_on),
                status = CheckStatus.PASS,
            )
        }

        rows += if (adbEnabled) {
            AuditRow(
                title = ctx.getString(R.string.detection_test_adb_title),
                subtitle = ctx.getString(R.string.detection_test_adb_on),
                status = CheckStatus.INFO,
            )
        } else {
            AuditRow(
                title = ctx.getString(R.string.detection_test_adb_title),
                subtitle = ctx.getString(R.string.detection_test_adb_off),
                status = CheckStatus.PASS,
            )
        }

        val remnantCount = countConfigRemnants(rawConfig)
        rows += if (remnantCount > 0) {
            AuditRow(
                title = ctx.getString(R.string.detection_test_config_title),
                subtitle = ctx.getString(R.string.detection_test_config_remnants, remnantCount),
                status = CheckStatus.WARN,
            )
        } else {
            AuditRow(
                title = ctx.getString(R.string.detection_test_config_title),
                subtitle = ctx.getString(
                    R.string.detection_test_config_ok,
                    rawConfig.scope.size,
                    rawConfig.templates.size + rawConfig.settingsTemplates.size,
                ),
                status = CheckStatus.PASS,
            )
        }

        return rows
    }

    private fun countConfigRemnants(config: JsonConfig): Int {
        var issues = 0
        issues += config.templates.count { (_, tpl) -> tpl.appList.isEmpty() }
        issues += config.settingsTemplates.count { (_, tpl) -> tpl.settingsList.isEmpty() }
        for (app in config.scope.values) {
            issues += app.applyTemplates.count { !config.templates.containsKey(it) }
            issues += app.applyPresets.count { it !in AppPresets.instance.presetNames }
            issues += app.applySettingTemplates.count { !config.settingsTemplates.containsKey(it) }
            issues += app.applySettingsPresets.count { it !in SettingsPresets.instance.presetNames }
        }
        return issues
    }

    private fun loadPresetPackagesMap(): Map<String, Set<String>> {
        val result = mutableMapOf<String, Set<String>>()
        for (presetName in AppPresets.instance.presetNames) {
            val fromService = if (ServiceClient.serviceVersion > 0) {
                runCatching { ServiceClient.getPackagesForPreset(presetName)?.toSet() }.getOrNull()
            } else {
                null
            }
            val packages = fromService ?: AppPresets.instance.getPresetByName(presetName)?.packages.orEmpty()
            result[presetName] = packages
        }
        return result
    }

    private fun collectInstalledSensitiveApps(
        installedSet: Set<String>,
        presetPackages: Map<String, Set<String>>,
    ): List<String> {
        val targetPresets = listOf(
            RootAppsPreset.NAME,
            XposedModulesPreset.NAME,
            DetectorAppsPreset.NAME,
            SuspiciousAppsPreset.NAME,
            SDhizukuAppsPreset.NAME,
        )
        val collected = linkedSetOf<String>()
        for (presetName in targetPresets) {
            presetPackages[presetName].orEmpty().forEach { pkg ->
                if (pkg in installedSet) {
                    collected.add(pkg)
                }
            }
        }
        if (BuildConfig.APP_PACKAGE_NAME in installedSet) {
            collected.add(BuildConfig.APP_PACKAGE_NAME)
        }
        return collected.sortedWith(PackageHelper.Comparators.byLabel)
    }

    private data class SingleAppAudit(
        val hiddenCount: Int,
        val exposedCount: Int,
        val worstStatus: CheckStatus,
        val rows: List<AuditRow>,
    )

    private fun auditSingleApp(
        ctx: Context,
        targetPkg: String,
        rawConfig: JsonConfig,
        presetPackages: Map<String, Set<String>>,
        sensitiveApps: List<String>,
        installedSet: Set<String>,
        adbEnabled: Boolean,
        collectDetails: Boolean,
    ): SingleAppAudit {
        val rows = mutableListOf<AuditRow>()
        val appConfig = rawConfig.scope[targetPkg]
        val pm = ctx.packageManager

        val pkgInfo = runCatching {
            pm.getPackageInfo(targetPkg, PackageManager.GET_PERMISSIONS)
        }.getOrNull()

        if (collectDetails) {
            val hasQueryAll = pkgInfo?.requestedPermissions?.contains("android.permission.QUERY_ALL_PACKAGES") == true
            rows += AuditRow(
                title = ctx.getString(R.string.detection_test_query_all_title),
                subtitle = ctx.getString(
                    if (hasQueryAll) R.string.detection_test_query_all_yes else R.string.detection_test_query_all_no
                ),
                status = if (hasQueryAll) CheckStatus.INFO else CheckStatus.PASS,
            )
        }

        val sharedUid = pkgInfo?.sharedUserId
        var hasSharedUidWarning = false
        if (!sharedUid.isNullOrEmpty()) {
            val unhookedSiblings = installedSet.filter { otherPkg ->
                otherPkg != targetPkg &&
                    otherPkg !in Constants.packagesShouldNotHide &&
                    PackageHelper.exists(otherPkg) &&
                    PackageHelper.loadPackageInfo(otherPkg).sharedUserId == sharedUid &&
                    !rawConfig.scope.containsKey(otherPkg)
            }
            if (unhookedSiblings.isNotEmpty()) {
                hasSharedUidWarning = true
                if (collectDetails) {
                    rows += AuditRow(
                        title = ctx.getString(R.string.detection_test_shared_uid_title),
                        subtitle = ctx.getString(
                            R.string.detection_test_shared_uid_warn,
                            sharedUid,
                            unhookedSiblings.joinToString(),
                        ),
                        status = CheckStatus.WARN,
                    )
                }
            } else if (collectDetails) {
                rows += AuditRow(
                    title = ctx.getString(R.string.detection_test_shared_uid_title),
                    subtitle = ctx.getString(R.string.detection_test_shared_uid_ok),
                    status = CheckStatus.PASS,
                )
            }
        }

        if (appConfig == null) {
            if (collectDetails) {
                rows += AuditRow(
                    title = ctx.getString(R.string.detection_test_app_protection_title),
                    subtitle = ctx.getString(R.string.detection_test_not_scoped, sensitiveApps.size),
                    status = CheckStatus.FAIL,
                    iconDrawable = PackageHelper.loadAppIcon(targetPkg),
                )
            }
            return SingleAppAudit(0, sensitiveApps.size, CheckStatus.FAIL, rows)
        }

        val activityProtectionActive = if (appConfig.invertActivityLaunchProtection) {
            rawConfig.disableActivityLaunchProtection
        } else {
            !rawConfig.disableActivityLaunchProtection
        }

        if (collectDetails) {
            rows += AuditRow(
                title = ctx.getString(R.string.detection_test_app_protection_title),
                subtitle = ctx.getString(
                    R.string.detection_test_app_protection_desc,
                    ctx.getString(if (appConfig.useWhitelist) R.string.whitelist else R.string.blacklist),
                    ctx.getString(if (activityProtectionActive) R.string.enabled else R.string.disabled),
                    ctx.getString(if (appConfig.hideInstallationSource) R.string.enabled else R.string.disabled),
                ),
                status = if (activityProtectionActive) CheckStatus.PASS else CheckStatus.WARN,
                iconDrawable = PackageHelper.loadAppIcon(targetPkg),
            )
        }

        val hasSettingsAdbWarn = adbEnabled && appConfig.applySettingTemplates.isEmpty() && appConfig.applySettingsPresets.isEmpty()
        if (collectDetails && hasSettingsAdbWarn) {
            rows += AuditRow(
                title = ctx.getString(R.string.detection_test_adb_title),
                subtitle = ctx.getString(R.string.detection_test_settings_adb_warn),
                status = CheckStatus.WARN,
            )
        }

        if (collectDetails && targetPkg in Constants.gmsPackages) {
            rows += AuditRow(
                title = PackageHelper.loadAppLabel(targetPkg),
                subtitle = ctx.getString(R.string.detection_test_exposed_gms_hint),
                status = CheckStatus.INFO,
            )
        }

        var hiddenCount = 0
        var exposedCount = 0
        val sensitiveRows = if (collectDetails) mutableListOf<AuditRow>() else null

        for (sensitivePkg in sensitiveApps) {
            if (sensitivePkg == targetPkg) continue
            val isHidden = evaluateShouldHide(targetPkg, appConfig, sensitivePkg, rawConfig, presetPackages)
            if (isHidden) {
                hiddenCount++
                if (sensitiveRows != null) {
                    sensitiveRows += AuditRow(
                        title = PackageHelper.loadAppLabel(sensitivePkg),
                        subtitle = "$sensitivePkg • ${ctx.getString(R.string.detection_test_hidden_item)}",
                        status = CheckStatus.PASS,
                        iconDrawable = PackageHelper.loadAppIcon(sensitivePkg),
                    )
                }
            } else {
                exposedCount++
                if (sensitiveRows != null) {
                    val hints = mutableListOf(sensitivePkg)
                    if (appConfig.useWhitelist && appConfig.excludeSystemApps && PackageHelper.exists(sensitivePkg) && PackageHelper.isSystem(sensitivePkg)) {
                        hints += ctx.getString(R.string.detection_test_exposed_system_hint)
                    }
                    val hasLaunchIntent = runCatching { pm.getLaunchIntentForPackage(sensitivePkg) != null }.getOrDefault(false)
                    if (hasLaunchIntent) {
                        hints += ctx.getString(R.string.detection_test_exposed_intent_hint)
                    }
                    sensitiveRows += AuditRow(
                        title = PackageHelper.loadAppLabel(sensitivePkg),
                        subtitle = hints.joinToString(" • "),
                        status = CheckStatus.FAIL,
                        iconDrawable = PackageHelper.loadAppIcon(sensitivePkg),
                    )
                }
            }
        }

        // Put exposed items first so user immediately sees leaks at the top
        if (sensitiveRows != null) {
            rows.addAll(sensitiveRows.sortedBy { if (it.status == CheckStatus.FAIL) 0 else 1 })
        }

        val worstStatus = when {
            exposedCount > 0 -> CheckStatus.FAIL
            !activityProtectionActive || hasSharedUidWarning || hasSettingsAdbWarn -> CheckStatus.WARN
            else -> CheckStatus.PASS
        }

        return SingleAppAudit(hiddenCount, exposedCount, worstStatus, rows)
    }

    private fun evaluateShouldHide(
        caller: String,
        appConfig: JsonConfig.AppConfig,
        query: String,
        rawConfig: JsonConfig,
        presetPackages: Map<String, Set<String>>,
    ): Boolean {
        if (caller == BuildConfig.APP_PACKAGE_NAME) return false
        if (caller in Constants.packagesShouldNotHide || query in Constants.packagesShouldNotHide) return false
        if (caller == query) return false

        if (query in appConfig.extraAppList) return !appConfig.useWhitelist
        if (query in appConfig.extraOppositeAppList) return appConfig.useWhitelist

        for (tplName in appConfig.applyTemplates) {
            val tpl = rawConfig.templates[tplName] ?: continue
            if (query in tpl.appList) {
                return !appConfig.useWhitelist
            }
        }

        for (presetName in appConfig.applyPresets) {
            val pkgs = presetPackages[presetName].orEmpty()
            if (query in pkgs) {
                return true
            }
        }

        if (appConfig.useWhitelist && appConfig.excludeSystemApps && PackageHelper.exists(query) && PackageHelper.isSystem(query)) {
            return false
        }

        return appConfig.useWhitelist
    }

    private fun renderReport(report: AuditReport) {
        val summaryColor = statusCardColor(report.overallStatus)
        val contentColor = when (report.overallStatus) {
            CheckStatus.WARN, CheckStatus.FAIL -> getColor(android.R.color.white)
            else -> themeColor(android.R.attr.colorBackground)
        }
        binding.summaryCard.setCardBackgroundColor(summaryColor)
        binding.summaryIcon.setImageResource(
            when (report.overallStatus) {
                CheckStatus.PASS, CheckStatus.INFO -> R.drawable.sentiment_calm_24px
                else -> R.drawable.sentiment_very_dissatisfied_24px
            }
        )
        binding.summaryIcon.setColorFilter(contentColor)
        binding.summaryTitle.setTextColor(contentColor)
        binding.summarySubtitle.setTextColor(contentColor)
        binding.summaryTitle.text = report.summaryTitle
        binding.summarySubtitle.text = report.summarySubtitle

        binding.btnSelectApp.text = selectedTargetApp?.let { PackageHelper.loadAppLabel(it) }
            ?: getString(R.string.detection_test_select_app)

        binding.envContainer.removeAllViews()
        report.envRows.forEach { addResultRow(binding.envContainer, it) }

        binding.resultsContainer.removeAllViews()
        report.resultRows.forEach { addResultRow(binding.resultsContainer, it) }
    }

    private fun addResultRow(container: ViewGroup, row: AuditRow) {
        val itemBinding = ItemDetectionResultBinding.inflate(layoutInflater, container, false)
        itemBinding.root.setCardBackgroundColor(homeItemBackgroundColor())

        if (row.iconDrawable != null) {
            itemBinding.itemIcon.setImageDrawable(row.iconDrawable)
            itemBinding.itemIcon.clearColorFilter()
            itemBinding.itemIcon.imageTintList = null
        } else {
            val iconRes = when (row.status) {
                CheckStatus.PASS -> R.drawable.check_24px
                CheckStatus.INFO -> R.drawable.outline_info_24
                CheckStatus.WARN -> R.drawable.outline_bug_report_24
                CheckStatus.FAIL -> R.drawable.sentiment_very_dissatisfied_24px
            }
            itemBinding.itemIcon.setImageResource(iconRes)
            itemBinding.itemIcon.setColorFilter(statusBadgeColor(row.status))
        }

        itemBinding.itemTitle.text = row.title
        itemBinding.itemSubtitle.text = row.subtitle

        val badgeColor = statusBadgeColor(row.status)
        itemBinding.itemBadge.text = when (row.status) {
            CheckStatus.PASS -> getString(R.string.detection_test_badge_ok)
            CheckStatus.INFO -> getString(R.string.detection_test_badge_info)
            CheckStatus.WARN -> getString(R.string.detection_test_badge_warn)
            CheckStatus.FAIL -> getString(R.string.detection_test_badge_fail)
        }
        itemBinding.itemBadge.setTextColor(getColor(android.R.color.white))
        itemBinding.itemBadge.background = GradientDrawable().apply {
            cornerRadius = dp2Px(resources, 12)
            setColor(badgeColor)
        }

        if (row.onClick != null) {
            itemBinding.root.setOnClickListener { row.onClick.invoke() }
        }

        container.addView(itemBinding.root)
    }

    private fun statusHeadline(ctx: Context, status: CheckStatus): String = when (status) {
        CheckStatus.PASS, CheckStatus.INFO -> ctx.getString(R.string.detection_test_summary_protected)
        CheckStatus.WARN -> ctx.getString(R.string.detection_test_summary_warning)
        CheckStatus.FAIL -> ctx.getString(R.string.detection_test_summary_exposed)
    }

    private fun statusCardColor(status: CheckStatus): Int {
        val color = when (status) {
            CheckStatus.PASS, CheckStatus.INFO -> themeColor(android.R.attr.colorPrimary)
            CheckStatus.WARN -> getColor(R.color.invalid)
            CheckStatus.FAIL -> getColor(R.color.error)
        }
        return if (PrefManager.systemWallpaper) color - 0x55000000 else color
    }

    private fun statusBadgeColor(status: CheckStatus): Int = when (status) {
        CheckStatus.PASS -> getColor(R.color.warn)
        CheckStatus.INFO -> getColor(R.color.info)
        CheckStatus.WARN -> getColor(R.color.invalid)
        CheckStatus.FAIL -> getColor(R.color.error)
    }

    private fun maxSeverity(a: CheckStatus, b: CheckStatus): CheckStatus =
        if (a.ordinal >= b.ordinal) a else b
}
