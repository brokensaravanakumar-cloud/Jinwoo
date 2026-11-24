package com.jinwoo.ai.actions

import android.content.Context
import android.content.Intent
import java.util.Locale

/** Finds ANY installed launcher app by spoken name (fuzzy), so "open Spotify" works without hard-coding apps. */
object AppResolver {
    data class AppInfo(val label: String, val pkg: String)

    /** Spoken aliases -> package names (used only if that package is actually installed). */
    private val aliases = mapOf(
        "youtube" to "com.google.android.youtube", "chrome" to "com.android.chrome", "google chrome" to "com.android.chrome",
        "whatsapp" to "com.whatsapp", "gmail" to "com.google.android.gm", "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps", "play store" to "com.android.vending",
        "instagram" to "com.instagram.android", "telegram" to "org.telegram.messenger", "spotify" to "com.spotify.music",
    )

    fun installed(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    fun launchIntent(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Pure function (unit tested): best installed app for a spoken name. */
    fun bestMatch(query: String, apps: List<AppInfo>): AppInfo? {
        val q = norm(query)
        if (q.isEmpty()) return null
        aliases[q]?.let { pkg -> apps.firstOrNull { it.pkg == pkg }?.let { return it } }
        val labeled = apps.map { it to norm(it.label) }
        labeled.firstOrNull { it.second == q }?.let { return it.first }
        labeled.filter { it.second.startsWith(q) }.minByOrNull { it.second.length }?.let { return it.first }
        labeled.filter { it.second.contains(q) }.minByOrNull { it.second.length }?.let { return it.first }
        val qTokens = q.split(" ")
        labeled.filter { (_, l) -> qTokens.all { l.contains(it) } }.minByOrNull { it.second.length }?.let { return it.first }
        if (q.length >= 5) {   // tolerate speech-recognition slips ("whats up" vs "whatsapp")
            val squeezed = q.replace(" ", "")
            labeled.map { it to levenshtein(squeezed, it.second.replace(" ", "")) }
                .filter { it.second <= 2 }.minByOrNull { it.second }?.let { return it.first.first }
        }
        return null
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
