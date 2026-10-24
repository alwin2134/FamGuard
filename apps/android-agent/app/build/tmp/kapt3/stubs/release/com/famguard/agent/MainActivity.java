package com.famguard.agent;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u0014R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/famguard/agent/MainActivity;", "Landroidx/activity/ComponentActivity;", "()V", "apiClient", "Lcom/famguard/agent/auth/AgentApiClient;", "keystoreManager", "Lcom/famguard/agent/security/DeviceKeystoreManager;", "usageAccessManager", "Lcom/famguard/agent/activity/UsageAccessManager;", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "app_release"})
public final class MainActivity extends androidx.activity.ComponentActivity {
    private com.famguard.agent.security.DeviceKeystoreManager keystoreManager;
    private com.famguard.agent.activity.UsageAccessManager usageAccessManager;
    private com.famguard.agent.auth.AgentApiClient apiClient;
    
    public MainActivity() {
        super(0);
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
}