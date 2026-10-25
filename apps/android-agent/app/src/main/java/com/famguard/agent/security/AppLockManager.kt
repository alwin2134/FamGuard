package com.famguard.agent.security

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

class AppLockManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory set of packages temporarily bypassed via the Emergency Unlock button during testing
    private val bypassedPackages = ConcurrentHashMap.newKeySet<String>()

    fun lockPackage(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return
        val current = getLockedPackages().toMutableSet()
        current.add(cleanPkg)
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
        // Removing from bypassed if newly locked
        bypassedPackages.remove(cleanPkg)
        android.util.Log.d(TAG, "Locked package: $cleanPkg (Total locked: ${current.size})")
    }

    fun unlockPackage(packageName: String) {
        val cleanPkg = packageName.trim()
        val current = getLockedPackages().toMutableSet()
        if (cleanPkg.equals("all", ignoreCase = true) || cleanPkg.isBlank()) {
            unlockAll()
            return
        }
        current.remove(cleanPkg)
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
        bypassedPackages.remove(cleanPkg)
        android.util.Log.d(TAG, "Unlocked package: $cleanPkg")
    }

    fun unlockAll() {
        prefs.edit().remove(KEY_LOCKED_PACKAGES).apply()
        bypassedPackages.clear()
        android.util.Log.d(TAG, "Unlocked all packages")
    }

    /**
     * Emergency bypass triggered by the user on the lock screen overlay.
     * Prevents lock screen from reappearing for this package during testing.
     */
    fun bypassPackage(packageName: String) {
        bypassedPackages.add(packageName.trim())
        android.util.Log.w(TAG, "EMERGENCY BYPASS ACTIVE for: $packageName")
    }

    fun clearBypasses() {
        bypassedPackages.clear()
    }

    fun isPackageLocked(packageName: String): Boolean {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return false
        if (bypassedPackages.contains(cleanPkg)) return false
        return getLockedPackages().contains(cleanPkg)
    }

    fun getLockedPackages(): Set<String> {
        return prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    companion object {
        private const val TAG = "AppLockManager"
        private const val PREFS_NAME = "famguard_app_lock_prefs"
        private const val KEY_LOCKED_PACKAGES = "locked_packages"
    }
}
