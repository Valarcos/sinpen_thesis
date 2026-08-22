package com.centralizesys.service;

import com.centralizesys.repository.AuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository repository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @Test
    public void registrarAccion_withEmojiAt254_doesNotSplitSurrogatePair() {
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 254; i++) {
            sb2.append("A");
        }
        sb2.append("\uD83D\uDE00");

        auditoriaService.registrarAccion(1L, "TEST", sb2.toString());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).save(eq(1L), eq("TEST"), captor.capture());

        String saved = captor.getValue();
        char lastChar = saved.charAt(saved.length() - 1);
        assertFalse(Character.isHighSurrogate(lastChar), "Surrogate pair was split!");
    }
}
