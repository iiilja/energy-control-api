package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.config.SecurityConfig;
import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride;
import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride.Source;
import com.ilja.smarthome.energycontrol.repository.UserRepository;
import com.ilja.smarthome.energycontrol.service.HeatingControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link HeatingOverrideController}: validation, status codes and roles, with the real {@link SecurityConfig}. */
@WebMvcTest(HeatingOverrideController.class)
@Import(SecurityConfig.class)
class HeatingOverrideControllerTest {

    private static final String URL = "/api/v1/heating/setpoint/override";

    @Autowired private MockMvc mvc;

    @MockBean private HeatingControlService service;
    @MockBean private UserRepository userRepository; // required by SecurityConfig

    @Test
    @WithMockUser(roles = "READONLY")
    void getWithoutOverrideIs204() throws Exception {
        when(service.getActiveOverride()).thenReturn(Optional.empty());

        mvc.perform(get(URL)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "READONLY")
    void getActiveOverride() throws Exception {
        when(service.getActiveOverride()).thenReturn(Optional.of(override("23.0", Source.HEAT_PUMP)));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setpoint").value(23.0))
                .andExpect(jsonPath("$.source").value("HEAT_PUMP"))
                .andExpect(jsonPath("$.validUntil").value("2026-10-26T12:00:00Z"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void postStartsOverride() throws Exception {
        when(service.startOverride(any(), any())).thenReturn(override("24.0", Source.API));

        postJson("{\"setpoint\": 24.0, \"hours\": 2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("API"));

        verify(service).startOverride(eq(new BigDecimal("24.0")), eq(2.0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void hoursAreOptional() throws Exception {
        when(service.startOverride(any(), any())).thenReturn(override("24.0", Source.API));

        postJson("{\"setpoint\": 24.0}").andExpect(status().isOk());

        verify(service).startOverride(eq(new BigDecimal("24.0")), isNull());
    }

    @ParameterizedTest(name = "{0} -> 400")
    @ValueSource(strings = {
            "{\"setpoint\": 14.5}",
            "{\"setpoint\": 30.5}",
            "{\"setpoint\": 24.0, \"hours\": 0}",
            "{\"setpoint\": 24.0, \"hours\": 25}",
            "{\"hours\": 2}",
    })
    @WithMockUser(roles = "USER")
    void rejectsInvalidRequest(String json) throws Exception {
        postJson(json).andExpect(status().isBadRequest());

        verify(service, never()).startOverride(any(), any());
    }

    @Test
    @WithMockUser(roles = "READONLY")
    void readonlyCannotSetOverride() throws Exception {
        postJson("{\"setpoint\": 24.0}").andExpect(status().isForbidden());

        verify(service, never()).startOverride(any(), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void cancel() throws Exception {
        when(service.cancelOverride()).thenReturn(true);

        mvc.perform(delete(URL)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void cancelWithoutOverrideIs404() throws Exception {
        when(service.cancelOverride()).thenReturn(false);

        mvc.perform(delete(URL)).andExpect(status().isNotFound());
    }

    private ResultActions postJson(String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(URL)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static HeatingManualOverride override(String setpoint, Source source) {
        return new HeatingManualOverride(new BigDecimal(setpoint), source,
                Instant.parse("2026-10-26T08:00:00Z"), Instant.parse("2026-10-26T12:00:00Z"));
    }
}
