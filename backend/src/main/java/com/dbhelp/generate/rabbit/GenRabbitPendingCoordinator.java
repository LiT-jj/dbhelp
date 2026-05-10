package com.dbhelp.generate.rabbit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统计已投递未确认的批次数，使生产者可在进程内阻塞至消费者全部处理完毕。
 */
@Component
public class GenRabbitPendingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(GenRabbitPendingCoordinator.class);

    private final ConcurrentHashMap<Long, AtomicInteger> pending = new ConcurrentHashMap<Long, AtomicInteger>();

    public void published(long taskId) {
        pending.computeIfAbsent(taskId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void consumed(long taskId) {
        AtomicInteger a = pending.get(taskId);
        if (a == null) {
            return;
        }
        int v = a.decrementAndGet();
        if (v < 0) {
            log.warn("Rabbit pending counter underflow taskId={}", taskId);
        }
    }

    /**
     * 阻塞直至该任务已发布批次全部被 {@link #consumed(long)} 抵消，或超时。
     */
    public void awaitZero(long taskId, long timeoutMillis) throws TimeoutException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            AtomicInteger a = pending.get(taskId);
            if (a == null || a.get() <= 0) {
                pending.remove(taskId);
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pending.remove(taskId);
                throw new TimeoutException("interrupted waiting for Rabbit batches taskId=" + taskId);
            }
        }
        throw new TimeoutException("timeout waiting for Rabbit batches taskId=" + taskId);
    }
}
