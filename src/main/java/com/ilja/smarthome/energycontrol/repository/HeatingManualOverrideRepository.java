package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface HeatingManualOverrideRepository extends JpaRepository<HeatingManualOverride, Long> {

    /** The newest override that has not been ended yet (it may already be past valid_until). */
    @Query("SELECT o FROM HeatingManualOverride o WHERE o.endedAt IS NULL ORDER BY o.startedAt DESC LIMIT 1")
    Optional<HeatingManualOverride> findOpen();
}
