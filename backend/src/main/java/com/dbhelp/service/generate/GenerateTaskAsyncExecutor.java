package com.dbhelp.service.generate;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class GenerateTaskAsyncExecutor {

    private final GenerateTaskWorker worker;

    public GenerateTaskAsyncExecutor(GenerateTaskWorker worker) {
        this.worker = worker;
    }

    @Async("generateTaskExecutor")
    public void execute(long taskId, boolean resume) {
        worker.run(taskId, resume);
    }
}
