package com.famguard.agent.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000T\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\u001a \u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007H\u0007\u001a \u0010\b\u001a\u00020\u00012\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\fH\u0007\u001aY\u0010\u000e\u001a\u00020\u00012\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00102\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00102\u0006\u0010\u0017\u001a\u00020\u00102\u0015\b\u0002\u0010\u0018\u001a\u000f\u0012\u0004\u0012\u00020\u0001\u0018\u00010\u0019\u00a2\u0006\u0002\b\u001aH\u0007\u00f8\u0001\u0000\u00a2\u0006\u0004\b\u001b\u0010\u001c\u001a\u0010\u0010\u001d\u001a\u00020\f2\u0006\u0010\u001e\u001a\u00020\u001fH\u0002\u001a\u0010\u0010 \u001a\u00020\f2\u0006\u0010\u001e\u001a\u00020\u001fH\u0002\u001a\u0010\u0010!\u001a\u00020\f2\u0006\u0010\u001e\u001a\u00020\u001fH\u0002\u001a\u0010\u0010\"\u001a\u00020\f2\u0006\u0010\u001e\u001a\u00020\u001fH\u0002\u0082\u0002\u0007\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006#"}, d2 = {"AgentMainScreen", "", "keystoreManager", "Lcom/famguard/agent/security/DeviceKeystoreManager;", "usageAccessManager", "Lcom/famguard/agent/activity/UsageAccessManager;", "apiClient", "Lcom/famguard/agent/auth/AgentApiClient;", "OemSetupGuideCard", "oemManager", "Lcom/famguard/agent/security/OemCompatibilityManager;", "isIgnoringBattery", "", "canDrawOverlay", "StatusCard", "title", "", "icon", "Landroidx/compose/ui/graphics/vector/ImageVector;", "statusText", "statusColor", "Landroidx/compose/ui/graphics/Color;", "description", "lastActionText", "actionContent", "Lkotlin/Function0;", "Landroidx/compose/runtime/Composable;", "StatusCard-ww6aTOc", "(Ljava/lang/String;Landroidx/compose/ui/graphics/vector/ImageVector;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function0;)V", "checkGpsEnabled", "ctx", "Landroid/content/Context;", "checkHasCamera", "checkHasLocation", "checkHasNotification", "app_debug"})
public final class AgentScreensKt {
    
    private static final boolean checkHasLocation(android.content.Context ctx) {
        return false;
    }
    
    private static final boolean checkGpsEnabled(android.content.Context ctx) {
        return false;
    }
    
    private static final boolean checkHasNotification(android.content.Context ctx) {
        return false;
    }
    
    private static final boolean checkHasCamera(android.content.Context ctx) {
        return false;
    }
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable()
    public static final void AgentMainScreen(@org.jetbrains.annotations.NotNull()
    com.famguard.agent.security.DeviceKeystoreManager keystoreManager, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.activity.UsageAccessManager usageAccessManager, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.auth.AgentApiClient apiClient) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void OemSetupGuideCard(@org.jetbrains.annotations.NotNull()
    com.famguard.agent.security.OemCompatibilityManager oemManager, boolean isIgnoringBattery, boolean canDrawOverlay) {
    }
}