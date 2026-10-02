package com.tradingplatform.paper;

import com.tradingplatform.persistence.PaperAutomationControlEntity;
import com.tradingplatform.persistence.PaperAutomationControlRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PaperAutomationControlService {
    private static final int CONTROL_ID = 1;

    private final PaperAutomationControlRepository repository;
    private volatile boolean stopped;

    public PaperAutomationControlService(PaperAutomationControlRepository repository) {
        this.repository = repository;
        this.stopped = repository.findById(CONTROL_ID)
                .map(control -> control.stopped)
                .orElse(false);
    }

    public synchronized void stop() {
        setStopped(true);
    }

    public synchronized void resume() {
        setStopped(false);
    }

    public boolean isStopped() {
        return stopped;
    }

    private void setStopped(boolean value) {
        PaperAutomationControlEntity control = repository.findById(CONTROL_ID)
                .orElseGet(PaperAutomationControlEntity::new);
        control.id = CONTROL_ID;
        control.stopped = value;
        control.updatedAt = Instant.now();
        repository.save(control);
        stopped = value;
    }
}
