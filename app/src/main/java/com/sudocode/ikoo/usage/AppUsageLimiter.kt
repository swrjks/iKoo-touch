package com.sudocode.ikoo.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object AppUsageLimiter {
    private const val PREFS = "ikoo_app_usage_limiter"
    private const val KEY_PACKAGE = "limited_package"
    private const val KEY_LIMIT_MINUTES = "limit_minutes"

    fun saveLimit(context: Context, packageName: String, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PACKAGE, packageName)
            .putInt(KEY_LIMIT_MINUTES, minutes.coerceAtLeast(1))
            .apply()
    }

    fun clearLimit(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PACKAGE)
            .remove(KEY_LIMIT_MINUTES)
            .apply()
    }

    fun readLimit(context: Context): UsageLimit? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val packageName = prefs.getString(KEY_PACKAGE, null) ?: return null
        val minutes = prefs.getInt(KEY_LIMIT_MINUTES, 0).takeIf { it > 0 } ?: return null
        return UsageLimit(packageName, minutes)
    }

    fun todayUsageMillis(context: Context, packageName: String): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            start,
            System.currentTimeMillis()
        ).orEmpty()
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }
}

data class UsageLimit(
    val packageName: String,
    val limitMinutes: Int
) {
    val limitMillis: Long get() = limitMinutes * 60_000L
}
