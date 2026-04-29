/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.data.DataManager
import org.fcitx.fcitx5.android.core.data.FileSource
import org.fcitx.fcitx5.android.core.data.PluginLoadFailed
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.HttpClient
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import java.io.File

class PluginFragment : PaddingPreferenceFragment() {

    private data class PluginItem(
        val name: String,
        val description: String,
        val downloadUrl: String,
    )

    private val pluginStore = listOf(
        PluginItem(
            name = "Rime Plugin",
            description = "中文输入与自定义方案支持",
            downloadUrl = "https://example.com/rime.apk"
        ), PluginItem(
            name = "Clipboard Plugin",
            description = "剪贴板历史记录支持",
            downloadUrl = "https://example.com/clipboard.apk"
        ), PluginItem(
            name = "Theme Plugin",
            description = "额外主题支持",
            downloadUrl = "https://example.com/theme.apk"
        )
    )

    private var firstRun = true

    private lateinit var synced: DataManager.PluginSet
    private lateinit var detected: DataManager.PluginSet

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshPreferencesWhenNeeded()
        }
    }

    private fun DataManager.whenSynced(block: () -> Unit) {
        lifecycleScope.launch {
            if (!synced) {
                suspendCancellableCoroutine {
                    if (synced) {
                        it.resumeWith(Result.success(Unit))
                    } else {
                        addOnNextSyncedCallback {
                            it.resumeWith(Result.success(Unit))
                        }
                    }
                }
            }
            block.invoke()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DataManager.whenSynced {
            synced = DataManager.getSyncedPluginSet()
            detected = DataManager.detectPlugins()
            preferenceScreen = createPreferenceScreen()
        }
    }

    private fun refreshPreferencesWhenNeeded() {
        DataManager.whenSynced {
            val newDetected = DataManager.detectPlugins()
            if (detected != newDetected) {
                detected = newDetected
                preferenceScreen = createPreferenceScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(packageChangeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        /**
         * [onResume] got called after [onCreatePreferences] when the fragment is created and
         * shown for the first time
         */
        if (firstRun) {
            firstRun = false
            return
        }
        // try refresh plugin list when the user navigate back from other apps
        refreshPreferencesWhenNeeded()
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(packageChangeReceiver)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view as? ViewGroup ?: return

        val fab = FloatingActionButton(requireContext()).apply {
            setImageResource(R.drawable.ic_baseline_plus_24)

            imageTintList = ColorStateList.valueOf(Color.BLACK)

            setOnClickListener {
                showPluginDialog()
            }
        }

        val margin = (16 * resources.displayMetrics.density).toInt()

        root.addView(
            fab,
            ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin, margin, margin, margin)
            }
        )

        root.post {
            fab.x = (root.width - fab.width - margin).toFloat()
            fab.y = (root.height - fab.height - margin).toFloat()
        }
    }

    private fun showPluginDialog() {
        val maxHeight = (400 * resources.displayMetrics.density).toInt()
        val scrollView = ScrollView(requireContext()).apply {
            isFillViewport = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxHeight
            )
        }

        val listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                32,
                32,
                32,
                32
            )
        }

        pluginStore.forEach { plugin ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    24,
                    24,
                    24,
                    24
                )
            }
            val textContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }
            val textParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )

            val title = TextView(requireContext()).apply {
                text = plugin.name
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            }

            val description = TextView(requireContext()).apply {
                text = plugin.description
                textSize = 14f
            }

            textContainer.addView(title)
            textContainer.addView(description)

            val downloadButton = Button(requireContext()).apply {
                text = "下载"
                setOnClickListener {
                    val button = this@apply
                    isEnabled = false
                    lifecycleScope.launch {
                        val file = File(
                            requireContext().getExternalFilesDir(null),
                            "Battle.net-Setup-CN.zip"
                        )
                        HttpClient.download(
                            url = "https://downloader.battlenet.com.cn/download/installer/mac/1.0.63/Battle.net-Setup-CN.zip",
                            targetFile = file,
                            onProgress = { current, total ->
                                val progress = if (total > 0) {
                                    (current * 100 / total).toInt()
                                } else {
                                    0
                                }
                                button.post {
                                    button.text = "$progress%"
                                }
                            }).onSuccess {
                            button.post {
                                button.text = "完成"
                            }
                        }.onFailure {
                            button.post {
                                button.text = "失败"
                                button.isEnabled = true
                            }
                        }
                    }
                }
            }

            row.addView(textContainer, textParams)
            row.addView(downloadButton)
            listContainer.addView(row)
        }
        scrollView.addView(listContainer)
        AlertDialog.Builder(requireContext())
            .setTitle("插件市场")
            .setView(scrollView)
            .setNegativeButton("关闭", null)
            .show()
    }


    private fun createPreferenceScreen(): PreferenceScreen =
        preferenceManager.createPreferenceScreen(requireContext()).apply {
            if (synced != detected) {
                addPreference(R.string.plugin_needs_reload, icon = R.drawable.ic_baseline_info_24) {
                    DataManager.addOnNextSyncedCallback {
                        synced = DataManager.getSyncedPluginSet()
                        detected = DataManager.detectPlugins()
                        preferenceScreen = createPreferenceScreen()
                    }
                    // DataManager.sync and and restart fcitx
                    FcitxDaemon.restartFcitx()
                }
            }
            val (loaded, failed) = synced
            if (loaded.isEmpty() && failed.isEmpty()) {
                // use PreferenceCategory to show a divider below the "reload" preference
                addCategory(R.string.no_plugins) {
                    isIconSpaceReserved = false
                    @SuppressLint("PrivateResource")
                    // we can't hide PreferenceCategory's title,
                    // but we can make it looks like a normal preference
                    layoutResource = androidx.preference.R.layout.preference_material
                }
                return@apply
            }
            if (loaded.isNotEmpty()) {
                addCategory(R.string.plugins_loaded) {
                    isIconSpaceReserved = false
                    loaded.forEach {
                        addPreference(it.name, "${it.versionName}\n${it.description}") {
                            startPluginAboutActivity(it.packageName)
                        }
                    }
                }
            }
            if (failed.isNotEmpty()) {
                addCategory(R.string.plugins_failed) {
                    isIconSpaceReserved = false
                    failed.forEach { (packageName, reason) ->
                        val summary = when (reason) {
                            is PluginLoadFailed.DataDescriptorParseError -> {
                                getString(R.string.invalid_data_descriptor)
                            }
                            is PluginLoadFailed.MissingDataDescriptor -> {
                                getString(R.string.missing_data_descriptor)
                            }
                            PluginLoadFailed.MissingPluginDescriptor -> {
                                getString(R.string.missing_plugin_descriptor)
                            }
                            is PluginLoadFailed.PathConflict -> {
                                val owner = when (reason.existingSrc) {
                                    FileSource.Main -> getString(R.string.main_program)
                                    is FileSource.Plugin -> reason.existingSrc.descriptor.name
                                }
                                getString(R.string.path_conflict, reason.path, owner)
                            }
                            is PluginLoadFailed.PluginAPIIncompatible -> {
                                getString(R.string.incompatible_api, reason.api)
                            }
                            PluginLoadFailed.PluginDescriptorParseError -> {
                                getString(R.string.invalid_plugin_descriptor)
                            }
                        }
                        addPreference(packageName, summary) {
                            startPluginAboutActivity(packageName)
                        }
                    }
                }
            }
        }

    private fun startPluginAboutActivity(pkg: String): Boolean {
        val ctx = requireContext()
        val pm = ctx.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                Intent(DataManager.PLUGIN_INTENT),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            pm.queryIntentActivities(Intent(DataManager.PLUGIN_INTENT), PackageManager.MATCH_ALL)
        }.firstOrNull {
            it.activityInfo.packageName == pkg
        }?.also {
            ctx.startActivity(Intent().apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
            })
        } ?: run {
            // fallback to settings app info page if activity not found
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.fromParts("package", pkg, null)
            })
        }
        return true
    }

}
