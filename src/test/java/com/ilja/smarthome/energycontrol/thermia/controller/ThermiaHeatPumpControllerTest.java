package com.ilja.smarthome.energycontrol.thermia.controller;

import com.ilja.smarthome.energycontrol.config.SecurityConfig;
import com.ilja.smarthome.energycontrol.repository.UserRepository;
import com.ilja.smarthome.energycontrol.service.HeatingControlService;
import com.ilja.smarthome.energycontrol.thermia.service.ThermiaHeatPumpService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link ThermiaHeatPumpController}: a setpoint set through this API is a manual override, not a raw register write. */
@WebMvcTest(ThermiaHeatPumpController.class)
@Import(SecurityConfig.class)
class ThermiaHeatPumpControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private ThermiaHeatPumpService heatPumpService;
    @MockBean private HeatingControlService heatingControlService;
    @MockBean private UserRepository userRepository; // required by SecurityConfig

    @Test
    @DisplayName("POST /setpoint starts a manual override with the configured duration")
    @WithMockUser(roles = "USER")
    void setpointStartsManualOverride() throws Exception {
        mvc.perform(post("/api/v1/thermia/setpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature\": 23.5}"))
                .andExpect(status().isNoContent());

        verify(heatingControlService).startOverride(eq(new BigDecimal("23.5")), isNull());
        verify(heatPumpService, never()).setComfortSetpoint(anyDouble());
    }

    @Test
    @WithMockUser(roles = "USER")
    void setpointOutOfRangeIs400() throws Exception {
        mvc.perform(post("/api/v1/thermia/setpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature\": 35}"))
                .andExpect(status().isBadRequest());

        verify(heatingControlService, never()).startOverride(any(), any());
    }
}
