package com.dbhelp.service.generate;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 运行中任务的瞬时 TPS 估算与取消标记（进程内，重启丢失）。
 */
@Component
public class GenerateTaskMetricsRegistry {

    private static final int MAX_SAMPLES = 20;

    private final ConcurrentHashMap<Long, TaskRuntime> runtimes = new ConcurrentHashMap<>();

    public void register(long taskId) {
        runtimes.put(taskId, new TaskRuntime());
    }

    public void unregister(long taskId) {
        runtimes.remove(taskId);
    }

    public void cancel(long taskId) {
        TaskRuntime rt = runtimes.get(taskId);
        if (rt != null) {
            rt.cancelled.set(true);
        }
    }

    public boolean isCancelled(long taskId) {
        TaskRuntime rt = runtimes.get(taskId);
        return rt != null && rt.cancelled.get();
    }

    /** 记录本批次写入行数与时间戳，用于 TPS 估算 */
    public void recordRows(long taskId, long rows, long epochMillis) {
        TaskRuntime rt = runtimes.get(taskId);
        if (rt == null || rows <= 0) {
            return;
        }
        synchronized (rt.samples) {
            rt.samples.addLast(new Sample(epochMillis, rows));
            while (rt.samples.size() > MAX_SAMPLES) {
                rt.samples.removeFirst();
            }
        }
    }

    public double computeInstantTps(long taskId) {
        TaskRuntime rt = runtimes.get(taskId);
        if (rt == null) {
            return 0d;
        }
        synchronized (rt.samples) {
            if (rt.samples.size() < 2) {
                return 0d;
            }
            Sample first = rt.samples.peekFirst();
            Sample last = rt.samples.peekLast();
            if (first == null || last == null) {
                return 0d;
            }
            long dt = last.tMillis - first.tMillis;
            if (dt <= 0) {
                return 0d;
            }
            long sumRows = 0;
            for (Sample s : rt.samples) {
                sumRows += s.rows;
            }
            return sumRows * 1000.0 / dt;
        }
    }

    public Boolean isRegisteredCancelled(long taskId) {
        TaskRuntime rt = runtimes.get(taskId);
        if (rt == null) {
            return null;
        }
        return rt.cancelled.get();
    }

    private static final class TaskRuntime {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Deque<Sample> samples = new ArrayDeque<>();
    }

    private static final class Sample {
        private final long tMillis;
        private final long rows;

        private Sample(long tMillis, long rows) {
            this.tMillis = tMillis;
            this.rows = rows;
        }
    }
}
