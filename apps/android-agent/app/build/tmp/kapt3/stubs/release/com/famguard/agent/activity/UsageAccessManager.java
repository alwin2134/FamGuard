package com.famguard.agent.activity;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\u0005\u001a\u00020\u0006J\u0006\u0010\u0007\u001a\u00020\bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/famguard/agent/activity/UsageAccessManager;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "createUsageAccessSettingsIntent", "Landroid/content/Intent;", "hasUsageAccess", "", "app_release"})
public final class UsageAccessManager {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    
    public UsageAccessManager(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    /**
     * Checks whether the user has granted PACKAGE_USAGE_STATS special access in Android Settings.
     */
    public final boolean hasUsageAccess() {
        return false;
    }
    
    /**
     * Creates an Intent to navigate directly to Usage Access settings screen.
     */
    @org.jetbrains.annotations.NotNull()
    public final android.content.Intent createUsageAccessSettingsIntent() {
        return null;
    }
}