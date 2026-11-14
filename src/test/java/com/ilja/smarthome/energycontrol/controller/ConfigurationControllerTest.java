package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.config.SecurityConfig;
import com.ilja.smarthome.energycontrol.dto.ConfigEntryDto;
import com.ilja.smarthome.energycontrol.exception.ConfigurationNotFoundException;
import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.repository.UserRepository;
import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link ConfigurationController}: roles, status codes and keys containing dots. */
@WebMvcTest(ConfigurationController.class)
@Import(SecurityConfig.class)
class ConfigurationControllerTest {

    private static final String KEY = "heating.price_optimization.peak_threshold";

    @Autowired private MockMvc mvc;

    @MockBean private ConfigurationService service;
    @MockBean private UserRepository userRepository; // required by SecurityConfig

    @Test
    @WithMockUser(roles = "READONLY")
    void listIsReadable() throws Exception {
        when(service.getAll()).thenReturn(List.of(new ConfigEntryDto(KEY, "50", "desc", null)));

        mvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value(KEY))
                .andExpect(jsonPath("$[0].value").value("50"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUpdatesKeyWithDots() throws Exception {
        when(service.update(KEY, "30")).thenReturn(new ConfigEntryDto(KEY, "30", "desc", null));

        mvc.perform(put("/api/v1/config/" + KEY).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("30"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotUpdate() throws Exception {
        mvc.perform(put("/api/v1/config/" + KEY).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"30\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).update(anyString(), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidValueIs400() throws Exception {
        when(service.update(any(), any())).thenThrow(new InvalidValueException(KEY + " must be a number above 0"));

        mvc.perform(put("/api/v1/config/" + KEY).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(KEY + " must be a number above 0"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownKeyIs404() throws Exception {
        when(service.update(any(), any())).thenThrow(new ConfigurationNotFoundException("no.such.key"));

        mvc.perform(put("/api/v1/config/no.such.key").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingValueIs400() throws Exception {
        mvc.perform(put("/api/v1/config/" + KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
