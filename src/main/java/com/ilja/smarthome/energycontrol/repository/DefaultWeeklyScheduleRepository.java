package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.DefaultWeeklySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DefaultWeeklyScheduleRepository extends JpaRepository<DefaultWeeklySchedule, Long> {

    List<DefaultWeeklySchedule> findByDayOfWeekOrderByStartTimeAsc(Short dayOfWeek);

    List<DefaultWeeklySchedule> findAllByOrderByDayOfWeekAscStartTimeAsc();
}
