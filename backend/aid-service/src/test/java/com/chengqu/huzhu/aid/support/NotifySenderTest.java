package com.chengqu.huzhu.aid.support;

import com.chengqu.huzhu.api.client.NotifyApiClient;
import com.chengqu.huzhu.api.dto.NotifyCreateCommand;
import com.chengqu.huzhu.common.api.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 通知投递的两条硬约束：失败必须降级、投递必须发生在事务提交之后。
 *
 * <p>这里只覆盖 aid-service 的一份实现；community-service 与 auth-service 的
 * {@code NotifySender} 逻辑完全相同（仅日志前缀不同）。
 */
@ExtendWith(MockitoExtension.class)
class NotifySenderTest {

    @Mock
    private NotifyApiClient notifyApiClient;

    private NotifySender notifySender;

    @AfterEach
    void tearDown() {
        // 用例里手工初始化过同步器，避免污染其他用例
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 无事务上下文时立即投递（例如单元测试或未来的非事务调用方）。 */
    @Test
    @DisplayName("没有事务时立即投递")
    void sendAfterCommit_sendsImmediatelyWithoutTransaction() {
        notifySender = new NotifySender(notifyApiClient);
        when(notifyApiClient.create(any())).thenReturn(ApiResponse.okMessage("通知已创建"));

        notifySender.sendAfterCommit(command());

        verify(notifyApiClient).create(any());
    }

    @Test
    @DisplayName("有事务时推迟到提交之后，提交前不发起远程调用")
    void sendAfterCommit_defersUntilCommit() {
        notifySender = new NotifySender(notifyApiClient);
        when(notifyApiClient.create(any())).thenReturn(ApiResponse.okMessage("通知已创建"));

        TransactionSynchronizationManager.initSynchronization();
        notifySender.sendAfterCommit(command());
        verifyNoInteractions(notifyApiClient);

        // 模拟事务管理器在提交后触发的回调
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(notifyApiClient).create(any());
    }

    @Test
    @DisplayName("事务回滚时不投递")
    void sendAfterCommit_skipsOnRollback() {
        notifySender = new NotifySender(notifyApiClient);

        TransactionSynchronizationManager.initSynchronization();
        notifySender.sendAfterCommit(command());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(notifyApiClient);
    }

    @Test
    @DisplayName("下游抛异常时只降级，不影响调用方")
    void send_swallowsRemoteFailure() {
        notifySender = new NotifySender(notifyApiClient);
        when(notifyApiClient.create(any())).thenThrow(new RuntimeException("connection refused"));

        assertDoesNotThrow(() -> notifySender.send(command()));
    }

    @Test
    @DisplayName("下游返回业务错误码时同样只降级")
    void send_swallowsBusinessError() {
        notifySender = new NotifySender(notifyApiClient);
        when(notifyApiClient.create(any())).thenReturn(ApiResponse.fail(400, "标题最长100字"));

        assertDoesNotThrow(() -> notifySender.send(command()));
    }

    @Test
    @DisplayName("没有接收者时不调用下游")
    void sendAfterCommit_skipsWithoutReceiver() {
        notifySender = new NotifySender(notifyApiClient);

        notifySender.sendAfterCommit(new NotifyCreateCommand(
                null, "AID_COMPLETED", "求助已标记完成", null, "AID", 1L, 2L, "某用户"));

        verifyNoInteractions(notifyApiClient);
    }

    private NotifyCreateCommand command() {
        return new NotifyCreateCommand(100L, "AID_ACCEPTED", "有人接下了你的求助",
                "内容", "AID", 1L, 9L, "帮助者");
    }
}
