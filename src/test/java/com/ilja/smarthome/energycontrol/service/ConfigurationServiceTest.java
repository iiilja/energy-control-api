package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.SystemConfiguration;
import com.ilja.smarthome.energycontrol.dto.ConfigEntryDto;
import com.ilja.smarthome.energycontrol.exception.ConfigurationNotFoundException;
import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.repository.SystemConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@link ConfigurationService#update} and {@link ConfigurationService#getAll}. */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    private static final String KEY = "heating.price_optimization.peak_threshold";

    @Mock private SystemConfigurationRepository repository;
    @Mock private ApplicationEventPublisher events;

    private ConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new ConfigurationService(repository, events);
    }

    @Test
    void updateSavesTrimmedValueAndPublishesEvent() {
        SystemConfiguration config = entry(KEY, "50");
        when(repository.findByConfigKey(KEY)).thenReturn(Optional.of(config));
        when(repository.saveAndFlush(config)).thenReturn(config);

        ConfigEntryDto result = service.update(KEY, " 30 ");

        assertThat(config.getConfigValue()).isEqualTo("30");
        assertThat(result.value()).isEqualTo("30");
        verify(events).publishEvent(new ConfigurationChangedEvent(KEY));
    }

    @Test
    void invalidValueIsNotSaved() {
        when(repository.findByConfigKey(KEY)).thenReturn(Optional.of(entry(KEY, "50")));

        assertThatThrownBy(() -> service.update(KEY, "0")).isInstanceOf(InvalidValueException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void unknownKeyIsRejected() {
        when(repository.findByConfigKey("no.such.key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("no.such.key", "1")).isInstanceOf(ConfigurationNotFoundException.class);
    }

    @Test
    void getAllIsSortedByKey() {
        when(repository.findAll()).thenReturn(List.of(entry("nordpool.fetch.enabled", "true"), entry(KEY, "50")));

        assertThat(service.getAll()).extracting(ConfigEntryDto::key)
                .containsExactly(KEY, "nordpool.fetch.enabled");
    }

    private static SystemConfiguration entry(String key, String value) {
        SystemConfiguration c = new SystemConfiguration();
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setDescription("test");
        c.setUpdatedAt(LocalDateTime.of(2026, 11, 14, 18, 0));
        return c;
    }
}
