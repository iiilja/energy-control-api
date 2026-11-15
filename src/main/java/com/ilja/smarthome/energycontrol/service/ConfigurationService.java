package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.SystemConfiguration;
import com.ilja.smarthome.energycontrol.dto.ConfigEntryDto;
import com.ilja.smarthome.energycontrol.exception.ConfigurationNotFoundException;
import com.ilja.smarthome.energycontrol.repository.SystemConfigurationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Service for managing system configuration.
 * Handles reading and updating configuration key-value pairs.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class ConfigurationService {

    private final SystemConfigurationRepository repository;
    private final ApplicationEventPublisher events;

    @Autowired
    public ConfigurationService(SystemConfigurationRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /** All entries, sorted by key, for the settings page. */
    public List<ConfigEntryDto> getAll() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(SystemConfiguration::getConfigKey))
                .map(ConfigurationService::toDto)
                .toList();
    }

    /**
     * Changes an existing value after checking it ({@link ConfigValueValidator}), and publishes a
     * {@link ConfigurationChangedEvent} so dependent jobs can react once the change has committed.
     *
     * @throws ConfigurationNotFoundException if the key does not exist
     * @throws com.ilja.smarthome.energycontrol.exception.InvalidValueException if the value is invalid
     */
    @Transactional
    public ConfigEntryDto update(String key, String value) {
        SystemConfiguration config = repository.findByConfigKey(key)
                .orElseThrow(() -> new ConfigurationNotFoundException(key));
        String trimmed = value == null ? null : value.trim();
        ConfigValueValidator.validate(key, trimmed);

        log.info("Updating configuration {} from '{}' to '{}'", key, config.getConfigValue(), trimmed);
        config.setConfigValue(trimmed);
        SystemConfiguration saved = repository.saveAndFlush(config);
        events.publishEvent(new ConfigurationChangedEvent(key));
        return toDto(saved);
    }

    private static ConfigEntryDto toDto(SystemConfiguration c) {
        return new ConfigEntryDto(c.getConfigKey(), c.getConfigValue(), c.getDescription(),
                c.getUpdatedAt() == null ? null
                        : c.getUpdatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
    }

    /**
     * Get a configuration value by key.
     *
     * @param key Configuration key
     * @return Configuration value
     * @throws ConfigurationNotFoundException if key not found
     */
    public String getConfigValue(String key) {
        return repository.findByConfigKey(key)
                .map(SystemConfiguration::getConfigValue)
                .orElseThrow(() -> new ConfigurationNotFoundException(key));
    }

    /**
     * Get a configuration value with a default fallback.
     *
     * @param key Configuration key
     * @param defaultValue Default value if key not found
     * @return Configuration value or default
     */
    public String getConfigValue(String key, String defaultValue) {
        return repository.findByConfigKey(key)
                .map(SystemConfiguration::getConfigValue)
                .orElse(defaultValue);
    }

    /**
     * Update a configuration value.
     *
     * @param key Configuration key
     * @param value New value
     * @return Updated configuration
     * @throws ConfigurationNotFoundException if key not found
     */
    @Transactional
    public SystemConfiguration updateConfiguration(String key, String value) {
        SystemConfiguration config = repository.findByConfigKey(key)
                .orElseThrow(() -> new ConfigurationNotFoundException(key));

        log.info("Updating configuration {} from '{}' to '{}'", key, config.getConfigValue(), value);
        config.setConfigValue(value);

        return repository.save(config);
    }

    /**
     * Create or update a configuration.
     *
     * @param key Configuration key
     * @param value Configuration value
     * @param description Description of the configuration
     * @return Created or updated configuration
     */
    @Transactional
    public SystemConfiguration createOrUpdateConfiguration(String key, String value, String description) {
        SystemConfiguration config = repository.findByConfigKey(key)
                .orElse(new SystemConfiguration());

        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);

        log.info("Creating/updating configuration: {}", key);
        return repository.save(config);
    }

    /**
     * Delete a configuration by key.
     *
     * @param key Configuration key
     */
    @Transactional
    public void deleteConfiguration(String key) {
        if (!repository.existsByConfigKey(key)) {
            throw new ConfigurationNotFoundException(key);
        }

        log.warn("Deleting configuration: {}", key);
        repository.deleteByConfigKey(key);
    }

    /**
     * Check if a configuration key exists.
     *
     * @param key Configuration key
     * @return true if exists, false otherwise
     */
    public boolean exists(String key) {
        return repository.existsByConfigKey(key);
    }
}
