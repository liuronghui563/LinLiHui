package com.chengqu.huzhu.aid.support;

import com.chengqu.huzhu.api.client.NotifyApiClient;
import com.chengqu.huzhu.api.dto.NotifyCreateCommand;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 站内通知投递入口：notify-service 的 Feign 调用唯一出口。
 *
 * <p>为什么不直接在业务方法里调 {@link NotifyApiClient}，而要抽这一层：
 * <ol>
 *   <li><b>不让远程调用污染业务事务</b>。业务方法都带 {@code @Transactional}，
 *       在事务内发起 HTTP 调用会把网络耗时（乃至下游超时）算进事务持有时间，
 *       数据库连接与行锁被无谓拉长；下游抛异常还会连带回滚已经写好的业务数据。
 *       这里用 {@link #sendAfterCommit} 把投递推迟到「当前事务提交之后」。</li>
 *   <li><b>强制降级</b>。通知是业务动作的附属产物，少一条通知远好过业务失败，
 *       因此任何异常、非 0 返回都只落 WARN，绝不外抛（afterCommit 里抛出的异常
 *       虽然不会回滚已提交的事务，但会一路冒泡到用户请求，同样不可接受）。</li>
 * </ol>
 *
 * <p>调用约定：在事务方法内、用「此刻已经确定的数据」构造好命令对象再调用本类。
 * afterCommit 回调里不再访问 SecurityContext、请求上下文或懒加载实体，
 * 避免投递时机与这些上下文（或 EntityManager）的生命周期不一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifySender {

    private final NotifyApiClient notifyApiClient;

    /**
     * 把通知投递推迟到当前事务提交之后执行；没有事务上下文时（如单元测试、
     * 非事务调用方）立即投递。
     *
     * <p>事务回滚时 afterCommit 不会被调用，通知自然不发——这正是期望行为：
     * 业务没成功就不该留下「有人接下了你的求助」这类事实记录。
     */
    public void sendAfterCommit(NotifyCreateCommand command) {
        if (command == null || command.userId() == null) {
            // 没有接收者就没有通知可言（例如求助还没有帮助者），静默跳过，不打日志刷屏
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(command);
                }
            });
            return;
        }
        send(command);
    }

    /** 真正发起一次跨服务调用；任何失败都降级为 WARN 日志。 */
    public void send(NotifyCreateCommand command) {
        try {
            ApiResponse<Void> response = notifyApiClient.create(command);
            if (response == null || response.getCode() != 0) {
                log.warn("[求助] 通知投递未成功，忽略 type={}, userId={}, actorId={}, code={}",
                        command.type(), command.userId(), command.actorId(),
                        response == null ? "null" : response.getCode());
            }
        } catch (Exception e) {
            // 故意捕获宽泛异常：跨服务调用失败（服务未注册、超时、熔断）不得影响主流程
            log.warn("[求助] 通知投递异常，忽略 type={}, userId={}, actorId={}, err={}",
                    command.type(), command.userId(), command.actorId(), e.getMessage());
        }
    }
}
