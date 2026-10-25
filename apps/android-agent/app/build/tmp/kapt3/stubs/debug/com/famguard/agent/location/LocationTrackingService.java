package com.famguard.agent.location;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0082\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\b\u0018\u0000 32\u00020\u0001:\u00013B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020\u0018H\u0002J\b\u0010 \u001a\u00020!H\u0002J\u0010\u0010\"\u001a\u00020!2\u0006\u0010#\u001a\u00020$H\u0002J\u0014\u0010%\u001a\u0004\u0018\u00010&2\b\u0010\'\u001a\u0004\u0018\u00010(H\u0016J\b\u0010)\u001a\u00020!H\u0016J\b\u0010*\u001a\u00020!H\u0016J\"\u0010+\u001a\u00020,2\b\u0010\'\u001a\u0004\u0018\u00010(2\u0006\u0010-\u001a\u00020,2\u0006\u0010.\u001a\u00020,H\u0016J\b\u0010/\u001a\u00020!H\u0002J\b\u00100\u001a\u00020!H\u0003J\u0010\u00101\u001a\u00020!2\u0006\u00102\u001a\u00020\u0004H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082.\u00a2\u0006\u0002\n\u0000R\u0012\u0010\u000f\u001a\u0004\u0018\u00010\u0010X\u0082\u000e\u00a2\u0006\u0004\n\u0002\u0010\u0011R\u0012\u0010\u0012\u001a\u0004\u0018\u00010\u0010X\u0082\u000e\u00a2\u0006\u0004\n\u0002\u0010\u0011R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0019\u001a\u0004\u0018\u00010\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001b\u001a\u0004\u0018\u00010\u001cX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u00064"}, d2 = {"Lcom/famguard/agent/location/LocationTrackingService;", "Landroid/app/Service;", "()V", "activeSessionId", "", "apiClient", "Lcom/famguard/agent/auth/AgentApiClient;", "bestAccuracySoFar", "", "fusedLocationClient", "Lcom/google/android/gms/location/FusedLocationProviderClient;", "isoFormat", "Ljava/text/SimpleDateFormat;", "keystoreManager", "Lcom/famguard/agent/security/DeviceKeystoreManager;", "lastUploadedLat", "", "Ljava/lang/Double;", "lastUploadedLon", "locationCallback", "Lcom/google/android/gms/location/LocationCallback;", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "sessionExpiresAtMs", "", "systemLocationListener", "Landroid/location/LocationListener;", "systemLocationManager", "Landroid/location/LocationManager;", "buildPersistentNotification", "Landroid/app/Notification;", "expiresAtMs", "createNotificationChannel", "", "handleNewLocation", "location", "Landroid/location/Location;", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "", "flags", "startId", "startExpiryWatcher", "startLocationUpdates", "stopTracking", "reason", "Companion", "app_debug"})
public final class LocationTrackingService extends android.app.Service {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    private com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient;
    private com.google.android.gms.location.LocationCallback locationCallback;
    private com.famguard.agent.security.DeviceKeystoreManager keystoreManager;
    private com.famguard.agent.auth.AgentApiClient apiClient;
    @org.jetbrains.annotations.Nullable()
    private android.location.LocationManager systemLocationManager;
    @org.jetbrains.annotations.Nullable()
    private android.location.LocationListener systemLocationListener;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String activeSessionId;
    private long sessionExpiresAtMs = 0L;
    @org.jetbrains.annotations.Nullable()
    private java.lang.Double lastUploadedLat;
    @org.jetbrains.annotations.Nullable()
    private java.lang.Double lastUploadedLon;
    private float bestAccuracySoFar = 3.4028235E38F;
    @org.jetbrains.annotations.NotNull()
    private final java.text.SimpleDateFormat isoFormat = null;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SESSION_ID = "com.famguard.agent.SESSION_ID";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_EXPIRES_AT_MS = "com.famguard.agent.EXPIRES_AT_MS";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_STOP_SESSION = "com.famguard.agent.ACTION_STOP_SESSION";
    private static final int NOTIFICATION_ID = 1001;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_ID = "famguard_location_channel";
    @org.jetbrains.annotations.NotNull()
    public static final com.famguard.agent.location.LocationTrackingService.Companion Companion = null;
    
    public LocationTrackingService() {
        super();
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    private final void startLocationUpdates() {
    }
    
    private final void handleNewLocation(android.location.Location location) {
    }
    
    private final void startExpiryWatcher() {
    }
    
    private final void stopTracking(java.lang.String reason) {
    }
    
    private final android.app.Notification buildPersistentNotification(long expiresAtMs) {
        return null;
    }
    
    private final void createNotificationChannel() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001e\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u00042\u0006\u0010\u000f\u001a\u00020\u0010J\u000e\u0010\u0011\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0012"}, d2 = {"Lcom/famguard/agent/location/LocationTrackingService$Companion;", "", "()V", "ACTION_STOP_SESSION", "", "CHANNEL_ID", "EXTRA_EXPIRES_AT_MS", "EXTRA_SESSION_ID", "NOTIFICATION_ID", "", "start", "", "context", "Landroid/content/Context;", "sessionId", "expiresAtMs", "", "stop", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final void start(@org.jetbrains.annotations.NotNull()
        android.content.Context context, @org.jetbrains.annotations.NotNull()
        java.lang.String sessionId, long expiresAtMs) {
        }
        
        public final void stop(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
    }
}