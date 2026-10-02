package com.tradingplatform.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.tradingplatform.persistence.PaperAutomationControlEntity;
import com.tradingplatform.persistence.PaperAutomationControlRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaperAutomationControlServiceTest {
    @Test
    void stopAndResumePersistTheEmergencyControl() {
        PaperAutomationControlRepository repository = mock(PaperAutomationControlRepository.class);
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaperAutomationControlService control = new PaperAutomationControlService(repository);

        control.stop();

        assertTrue(control.isStopped());
        verify(repository).save(argThat(saved -> saved.id.equals(1) && saved.stopped));

        control.resume();

        assertFalse(control.isStopped());
        verify(repository).save(argThat(saved -> saved.id.equals(1) && !saved.stopped));
    }

    @Test
    void restoresStoppedStateFromPersistentRecord() {
        PaperAutomationControlEntity saved = new PaperAutomationControlEntity();
        saved.id = 1;
        saved.stopped = true;
        PaperAutomationControlRepository repository = mock(PaperAutomationControlRepository.class);
        when(repository.findById(1)).thenReturn(Optional.of(saved));

        PaperAutomationControlService control = new PaperAutomationControlService(repository);

        assertTrue(control.isStopped());
    }
}
