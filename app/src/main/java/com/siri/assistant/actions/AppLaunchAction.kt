package com.siri.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

class AppLaunchAction(private val context: Context) {

    data class AppEntry(
        val label: String,
        val packageName: String
    )

    /**
     * Dynamically queries all launchable apps on the device.
     */
    fun getInstalledApps(): List<AppEntry> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        val apps = mutableListOf<AppEntry>()
        for (info in resolveInfos) {
            val label = info.loadLabel(packageManager).toString().trim()
            val pkg = info.activityInfo.packageName
            if (label.isNotEmpty() && pkg.isNotEmpty()) {
                apps.add(AppEntry(label = label, packageName = pkg))
            }
        }
        return apps
    }

    /**
     * Resolves app by name and launches it.
     */
    fun execute(appNameQuery: String): ActionResult {
        val query = appNameQuery.trim().lowercase(Locale.getDefault())
        if (query.isEmpty()) {
            return ActionResult(
                success = false,
                spokenResponse = "Which app would you like to open?"
            )
        }

        val installed = getInstalledApps()

        // 1. Exact match
        var match = installed.firstOrNull { it.label.lowercase(Locale.getDefault()) == query }

        // 2. Contains match
        if (match == null) {
            match = installed.firstOrNull {
                it.label.lowercase(Locale.getDefault()).contains(query) ||
                query.contains(it.label.lowercase(Locale.getDefault()))
            }
        }

        // 3. Package name match (e.g. "chrome" in com.android.chrome)
        if (match == null) {
            match = installed.firstOrNull {
                it.packageName.lowercase(Locale.getDefault()).contains(query)
            }
        }

        // 4. Common known aliases
        if (match == null) {
            val alias = when (query) {
                "whatsapp" -> "com.whatsapp"
                "instagram", "insta" -> "com.instagram.android"
                "youtube" -> "com.google.android.youtube"
                "spotify" -> "com.spotify.music"
                "chrome", "google chrome" -> "com.android.chrome"
                "gmail" -> "com.google.android.gm"
                "maps", "google maps" -> "com.google.android.apps.maps"
                "camera" -> "camera"
                "settings" -> "com.android.settings"
                else -> null
            }
            if (alias != null) {
                match = installed.firstOrNull {
                    it.packageName.lowercase(Locale.getDefault()).contains(alias) ||
                    it.label.lowercase(Locale.getDefault()).contains(alias)
                }
            }
        }

        if (match == null) {
            return ActionResult(
                success = false,
                spokenResponse = "I couldn't find an app named $appNameQuery installed on your device.",
                uiDetails = "App not installed: $appNameQuery"
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(launchIntent)
                ActionResult(
                    success = true,
                    spokenResponse = "Opening ${match.label}.",
                    uiDetails = "Launched ${match.label} (${match.packageName})"
                )
            } catch (e: Exception) {
                ActionResult(
                    success = false,
                    spokenResponse = "Failed to open ${match.label}.",
                    uiDetails = "Error launching ${match.packageName}: ${e.message}"
                )
            }
        }

        return ActionResult(
            success = false,
            spokenResponse = "I found ${match.label}, but couldn't launch it.",
            uiDetails = "No launch intent for: ${match.packageName}"
        )
    }
}
