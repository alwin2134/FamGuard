package com.famguard.agent.activity;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000H\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\b\u0005\u0018\u00002\u00020\u0001BC\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\b\b\u0002\u0010\n\u001a\u00020\u000b\u0012\b\b\u0002\u0010\f\u001a\u00020\r\u0012\b\b\u0002\u0010\u000e\u001a\u00020\u000f\u00a2\u0006\u0002\u0010\u0010J\u001c\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00150\u0014H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0016\u0010\u0017J\u001c\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00150\u0014H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0019\u0010\u0017R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\u001a"}, d2 = {"Lcom/famguard/agent/activity/ActivityRepository;", "", "context", "Landroid/content/Context;", "database", "Lcom/famguard/agent/activity/AgentDatabase;", "keystoreManager", "Lcom/famguard/agent/security/DeviceKeystoreManager;", "apiClient", "Lcom/famguard/agent/auth/AgentApiClient;", "usageAccessManager", "Lcom/famguard/agent/activity/UsageAccessManager;", "eventReader", "Lcom/famguard/agent/activity/UsageEventReader;", "sessionTracker", "Lcom/famguard/agent/activity/AppSessionTracker;", "(Landroid/content/Context;Lcom/famguard/agent/activity/AgentDatabase;Lcom/famguard/agent/security/DeviceKeystoreManager;Lcom/famguard/agent/auth/AgentApiClient;Lcom/famguard/agent/activity/UsageAccessManager;Lcom/famguard/agent/activity/UsageEventReader;Lcom/famguard/agent/activity/AppSessionTracker;)V", "dao", "Lcom/famguard/agent/activity/AgentDao;", "collectAndQueueEvents", "Lkotlin/Result;", "", "collectAndQueueEvents-IoAF18A", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "syncPendingEventsToServer", "syncPendingEventsToServer-IoAF18A", "app_release"})
public final class ActivityRepository {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.activity.AgentDatabase database = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.security.DeviceKeystoreManager keystoreManager = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.auth.AgentApiClient apiClient = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.activity.UsageAccessManager usageAccessManager = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.activity.UsageEventReader eventReader = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.activity.AppSessionTracker sessionTracker = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.activity.AgentDao dao = null;
    
    public ActivityRepository(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.activity.AgentDatabase database, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.security.DeviceKeystoreManager keystoreManager, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.auth.AgentApiClient apiClient, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.activity.UsageAccessManager usageAccessManager, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.activity.UsageEventReader eventReader, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.activity.AppSessionTracker sessionTracker) {
        super();
    }
}