package com.famguard.agent.commands;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\u0018\u0000 \u00162\u00020\u0001:\u0001\u0016B!\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0016\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0010H\u0086@\u00a2\u0006\u0002\u0010\u0011J\b\u0010\u0012\u001a\u00020\u0013H\u0002J\b\u0010\u0014\u001a\u00020\u0013H\u0002J\u0006\u0010\u0015\u001a\u00020\u0013R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0017"}, d2 = {"Lcom/famguard/agent/commands/CommandExecutor;", "", "context", "Landroid/content/Context;", "apiClient", "Lcom/famguard/agent/auth/AgentApiClient;", "keystoreManager", "Lcom/famguard/agent/security/DeviceKeystoreManager;", "(Landroid/content/Context;Lcom/famguard/agent/auth/AgentApiClient;Lcom/famguard/agent/security/DeviceKeystoreManager;)V", "activeRingtone", "Landroid/media/Ringtone;", "scope", "Lkotlinx/coroutines/CoroutineScope;", "executeCommand", "Lcom/famguard/contracts/CommandStatus;", "command", "Lcom/famguard/contracts/DeviceCommand;", "(Lcom/famguard/contracts/DeviceCommand;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "playRingAlert", "", "showRingNotification", "silenceAlert", "Companion", "app_release"})
public final class CommandExecutor {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.auth.AgentApiClient apiClient = null;
    @org.jetbrains.annotations.NotNull()
    private final com.famguard.agent.security.DeviceKeystoreManager keystoreManager = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    @org.jetbrains.annotations.Nullable()
    private android.media.Ringtone activeRingtone;
    private static final int RING_NOTIFICATION_ID = 5002;
    @org.jetbrains.annotations.NotNull()
    public static final com.famguard.agent.commands.CommandExecutor.Companion Companion = null;
    
    public CommandExecutor(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.auth.AgentApiClient apiClient, @org.jetbrains.annotations.NotNull()
    com.famguard.agent.security.DeviceKeystoreManager keystoreManager) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object executeCommand(@org.jetbrains.annotations.NotNull()
    com.famguard.contracts.DeviceCommand command, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.famguard.contracts.CommandStatus> $completion) {
        return null;
    }
    
    private final void playRingAlert() {
    }
    
    public final void silenceAlert() {
    }
    
    private final void showRingNotification() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/famguard/agent/commands/CommandExecutor$Companion;", "", "()V", "RING_NOTIFICATION_ID", "", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}