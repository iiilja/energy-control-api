package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for SystemConfiguration entities.
 * Provides data access methods for system configuration key-value pairs.
 */
@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, Long> {

    /**
     * Find a configuration by its key.
     */
    Optional<SystemConfiguration> findByConfigKey(String configKey);

    /**
     * Check if a configuration key exists.
     */
    boolean existsByConfigKey(String configKey);

    /**
     * Delete a configuration by its key.
     */
    void deleteByConfigKey(String configKey);
}
