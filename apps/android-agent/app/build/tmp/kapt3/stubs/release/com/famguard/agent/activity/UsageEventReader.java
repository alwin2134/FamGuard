package com.famguard.agent.activity;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u000f\u001a\u00020\t2\u0006\u0010\u0010\u001a\u00020\tH\u0002J\u001c\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00130\u00122\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0015R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\n\u001a\n \f*\u0004\u0018\u00010\u000b0\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0017"}, d2 = {"Lcom/famguard/agent/activity/UsageEventReader;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "isoFormat", "Ljava/text/SimpleDateFormat;", "labelCache", "Ljava/util/concurrent/ConcurrentHashMap;", "", "packageManager", "Landroid/content/pm/PackageManager;", "kotlin.jvm.PlatformType", "usageStatsManager", "Landroid/app/usage/UsageStatsManager;", "getAppLabel", "packageName", "readEvents", "", "Lcom/famguard/agent/activity/RawUsageEvent;", "startTimeMs", "", "endTimeMs", "app_release"})
public final class UsageEventReader {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.Nullable()
    private final android.app.usage.UsageStatsManager usageStatsManager = null;
    private final android.content.pm.PackageManager packageManager = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.ConcurrentHashMap<java.lang.String, java.lang.String> labelCache = null;
    @org.jetbrains.annotations.NotNull()
    private final java.text.SimpleDateFormat isoFormat = null;
    
    public UsageEventReader(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    /**
     * Reads usage events within the bounded window [startTimeMs, endTimeMs].
     * Excludes system noise, filters to foreground/background transitions.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.famguard.agent.activity.RawUsageEvent> readEvents(long startTimeMs, long endTimeMs) {
        return null;
    }
    
    private final java.lang.String getAppLabel(java.lang.String packageName) {
        return null;
    }
}