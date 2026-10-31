package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.HeatingControlState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeatingControlStateRepository extends JpaRepository<HeatingControlState, Short> {
}
