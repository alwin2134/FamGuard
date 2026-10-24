package com.famguard.agent.activity;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u001a\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00070\u0004\u00a8\u0006\b"}, d2 = {"Lcom/famguard/agent/activity/AppSessionTracker;", "", "()V", "processEvents", "", "Lcom/famguard/agent/activity/PendingAppEventEntity;", "rawEvents", "Lcom/famguard/agent/activity/RawUsageEvent;", "app_release"})
public final class AppSessionTracker {
    
    public AppSessionTracker() {
        super();
    }
    
    /**
     * Correlates foreground and background raw events to compute session durations where possible.
     * When a background event is missing, preserves the foreground event with null duration.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.famguard.agent.activity.PendingAppEventEntity> processEvents(@org.jetbrains.annotations.NotNull()
    java.util.List<com.famguard.agent.activity.RawUsageEvent> rawEvents) {
        return null;
    }
}