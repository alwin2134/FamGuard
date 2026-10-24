package com.famguard.agent.security;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0002\b\u000e\u0018\u0000 #2\u00020\u0001:\u0001#B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\t\u001a\u00020\nJ\u0006\u0010\u000b\u001a\u00020\fJ\b\u0010\r\u001a\u0004\u0018\u00010\u000eJ\b\u0010\u000f\u001a\u0004\u0018\u00010\u000eJ\b\u0010\u0010\u001a\u0004\u0018\u00010\u000eJ\b\u0010\u0011\u001a\u0004\u0018\u00010\u000eJ\b\u0010\u0012\u001a\u0004\u0018\u00010\u000eJ\u0006\u0010\u0013\u001a\u00020\fJ\u0006\u0010\u0014\u001a\u00020\fJ\u0006\u0010\u0015\u001a\u00020\u0016J\u001f\u0010\u0017\u001a\u00020\n2\b\u0010\u0018\u001a\u0004\u0018\u00010\u000e2\b\u0010\u0019\u001a\u0004\u0018\u00010\f\u00a2\u0006\u0002\u0010\u001aJ&\u0010\u001b\u001a\u00020\n2\u0006\u0010\u001c\u001a\u00020\u000e2\u0006\u0010\u001d\u001a\u00020\u000e2\u0006\u0010\u001e\u001a\u00020\u000e2\u0006\u0010\u001f\u001a\u00020\u000eJ\u000e\u0010 \u001a\u00020\n2\u0006\u0010!\u001a\u00020\fJ\u000e\u0010\"\u001a\u00020\n2\u0006\u0010!\u001a\u00020\fR\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006$"}, d2 = {"Lcom/famguard/agent/security/DeviceKeystoreManager;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "masterKey", "Landroidx/security/crypto/MasterKey;", "prefs", "Landroid/content/SharedPreferences;", "clearCredentials", "", "getActiveSessionExpiresAt", "", "getActiveSessionId", "", "getDeviceId", "getDeviceSecret", "getFamilyId", "getFamilyName", "getLastLocationFix", "getLastSyncTimestamp", "isPaired", "", "saveActiveSession", "sessionId", "expiresAtMs", "(Ljava/lang/String;Ljava/lang/Long;)V", "saveDeviceCredentials", "deviceId", "deviceSecret", "familyId", "familyName", "updateLastLocationFix", "timestampMs", "updateLastSyncTimestamp", "Companion", "app_debug"})
public final class DeviceKeystoreManager {
    @org.jetbrains.annotations.NotNull()
    private final androidx.security.crypto.MasterKey masterKey = null;
    @org.jetbrains.annotations.NotNull()
    private final android.content.SharedPreferences prefs = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_DEVICE_ID = "device_id";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_DEVICE_SECRET = "device_secret";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_FAMILY_ID = "family_id";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_FAMILY_NAME = "family_name";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_IS_PAIRED = "is_paired";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_ACTIVE_SESSION_ID = "active_session_id";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_ACTIVE_SESSION_EXPIRES = "active_session_expires";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_LAST_LOCATION_FIX = "last_location_fix";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp";
    @org.jetbrains.annotations.NotNull()
    public static final com.famguard.agent.security.DeviceKeystoreManager.Companion Companion = null;
    
    public DeviceKeystoreManager(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    public final void saveDeviceCredentials(@org.jetbrains.annotations.NotNull()
    java.lang.String deviceId, @org.jetbrains.annotations.NotNull()
    java.lang.String deviceSecret, @org.jetbrains.annotations.NotNull()
    java.lang.String familyId, @org.jetbrains.annotations.NotNull()
    java.lang.String familyName) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getDeviceId() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getDeviceSecret() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getFamilyId() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getFamilyName() {
        return null;
    }
    
    public final boolean isPaired() {
        return false;
    }
    
    public final void saveActiveSession(@org.jetbrains.annotations.Nullable()
    java.lang.String sessionId, @org.jetbrains.annotations.Nullable()
    java.lang.Long expiresAtMs) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getActiveSessionId() {
        return null;
    }
    
    public final long getActiveSessionExpiresAt() {
        return 0L;
    }
    
    public final void updateLastLocationFix(long timestampMs) {
    }
    
    public final long getLastLocationFix() {
        return 0L;
    }
    
    public final void updateLastSyncTimestamp(long timestampMs) {
    }
    
    public final long getLastSyncTimestamp() {
        return 0L;
    }
    
    public final void clearCredentials() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\t\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/famguard/agent/security/DeviceKeystoreManager$Companion;", "", "()V", "KEY_ACTIVE_SESSION_EXPIRES", "", "KEY_ACTIVE_SESSION_ID", "KEY_DEVICE_ID", "KEY_DEVICE_SECRET", "KEY_FAMILY_ID", "KEY_FAMILY_NAME", "KEY_IS_PAIRED", "KEY_LAST_LOCATION_FIX", "KEY_LAST_SYNC_TIMESTAMP", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}