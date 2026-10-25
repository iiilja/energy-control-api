package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.config.SecurityConfig;
import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.repository.UserRepository;
import com.ilja.smarthome.energycontrol.service.HeatingSetpointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request validation and access rules of {@link HeatingSetpointController},
 * with the real {@link SecurityConfig} and {@code GlobalExceptionHandler}.
 */
@WebMvcTest(HeatingSetpointController.class)
@Import(SecurityConfig.class)
class HeatingSetpointControllerTest {

    private static final String SCHEDULE_URL = "/api/v1/heating/setpoint/schedule";
    private static final String WEEKLY_URL = "/api/v1/heating/setpoint/weekly-schedule";

    @Autowired private MockMvc mvc;

    @MockBean private HeatingSetpointService service;
    @MockBean private UserRepository userRepository; // required by SecurityConfig

    @Nested
    @DisplayName("POST /schedule")
    class SaveSchedule {

        @Test
        @WithMockUser(roles = "USER")
        void acceptsValidSetpoint() throws Exception {
            postSchedule("[{\"nordpoolPriceId\": 1, \"setpoint\": 21.5}]")
                    .andExpect(status().isOk());

            verify(service).saveSchedules(anyList());
        }

        @ParameterizedTest(name = "setpoint {0} -> 400")
        @ValueSource(strings = {"14.9", "30.1", "50", "-5"})
        @WithMockUser(roles = "USER")
        void rejectsOutOfRangeSetpoint(String setpoint) throws Exception {
            postSchedule("[{\"nordpoolPriceId\": 1, \"setpoint\": " + setpoint + "}]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("setpoint")));

            verify(service, never()).saveSchedules(anyList());
        }

        @Test
        @WithMockUser(roles = "USER")
        void rejectsMissingFields() throws Exception {
            postSchedule("[{\"setpoint\": 21.0}]").andExpect(status().isBadRequest());
            postSchedule("[{\"nordpoolPriceId\": 1}]").andExpect(status().isBadRequest());

            verify(service, never()).saveSchedules(anyList());
        }

        @Test
        @DisplayName("one invalid item rejects the whole batch")
        @WithMockUser(roles = "USER")
        void rejectsBatchWithOneInvalidItem() throws Exception {
            postSchedule("[{\"nordpoolPriceId\": 1, \"setpoint\": 21.0}, {\"nordpoolPriceId\": 2, \"setpoint\": 99}]")
                    .andExpect(status().isBadRequest());

            verify(service, never()).saveSchedules(anyList());
        }

        @Test
        @DisplayName("unknown price id from the service -> 400 problem detail, not 500")
        @WithMockUser(roles = "USER")
        void unknownPriceIdIsBadRequest() throws Exception {
            when(service.saveSchedules(anyList()))
                    .thenThrow(new InvalidValueException("Nordpool price not found: 999"));

            postSchedule("[{\"nordpoolPriceId\": 999, \"setpoint\": 21.0}]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Nordpool price not found: 999"));
        }

        @Test
        @DisplayName("a null item in the list -> 400, not a NullPointerException")
        @WithMockUser(roles = "USER")
        void rejectsNullItem() throws Exception {
            postSchedule("[null]").andExpect(status().isBadRequest());

            verify(service, never()).saveSchedules(anyList());
        }

        @Test
        @DisplayName("a plain IllegalArgumentException (e.g. broken config number) is NOT turned into 400")
        @WithMockUser(roles = "USER")
        void serverSideIllegalArgumentIsNotBadRequest() {
            when(service.saveSchedules(anyList())).thenThrow(new NumberFormatException("For input string: \"abc\""));

            // no handler matches, so MockMvc rethrows it (in a running server this becomes 500)
            assertThatThrownBy(() -> postSchedule("[{\"nordpoolPriceId\": 1, \"setpoint\": 21.0}]"))
                    .hasRootCauseInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("READONLY users cannot change the schedule")
        @WithMockUser(roles = "READONLY")
        void readonlyIsForbidden() throws Exception {
            postSchedule("[{\"nordpoolPriceId\": 1, \"setpoint\": 21.0}]")
                    .andExpect(status().isForbidden());

            verify(service, never()).saveSchedules(anyList());
        }

        private ResultActions postSchedule(String json) throws Exception {
            return mvc.perform(post(SCHEDULE_URL).contentType(MediaType.APPLICATION_JSON).content(json));
        }
    }

    @Nested
    @DisplayName("PUT /weekly-schedule")
    class SaveWeeklySchedule {

        @Test
        @WithMockUser(roles = "USER")
        void acceptsValidTemplate() throws Exception {
            putWeekly("[{\"dayOfWeek\": 1, \"startTime\": \"05:00\", \"setpoint\": 22.0},"
                    + " {\"dayOfWeek\": 7, \"startTime\": \"23:45:00\", \"setpoint\": 20.0}]")
                    .andExpect(status().isOk());

            verify(service).saveWeeklySchedule(anyList());
        }

        @ParameterizedTest(name = "{0} -> 400")
        @ValueSource(strings = {
                "{\"dayOfWeek\": 0, \"startTime\": \"05:00\", \"setpoint\": 22.0}",
                "{\"dayOfWeek\": 8, \"startTime\": \"05:00\", \"setpoint\": 22.0}",
                "{\"dayOfWeek\": 1, \"startTime\": \"24:00\", \"setpoint\": 22.0}",
                "{\"dayOfWeek\": 1, \"startTime\": \"5:00\", \"setpoint\": 22.0}",
                "{\"dayOfWeek\": 1, \"startTime\": \"morning\", \"setpoint\": 22.0}",
                "{\"dayOfWeek\": 1, \"startTime\": \"05:00\", \"setpoint\": 10.0}",
                "{\"dayOfWeek\": 1, \"startTime\": \"05:00\"}",
        })
        @WithMockUser(roles = "USER")
        void rejectsInvalidEntry(String entry) throws Exception {
            putWeekly("[" + entry + "]").andExpect(status().isBadRequest());

            verify(service, never()).saveWeeklySchedule(anyList());
        }

        @Test
        @DisplayName("an empty list would wipe the whole template -> 400")
        @WithMockUser(roles = "USER")
        void rejectsEmptyList() throws Exception {
            putWeekly("[]").andExpect(status().isBadRequest());

            verify(service, never()).saveWeeklySchedule(anyList());
        }

        @Test
        @WithMockUser(roles = "USER")
        void rejectsNullItem() throws Exception {
            putWeekly("[null]").andExpect(status().isBadRequest());

            verify(service, never()).saveWeeklySchedule(anyList());
        }

        private ResultActions putWeekly(String json) throws Exception {
            return mvc.perform(put(WEEKLY_URL).contentType(MediaType.APPLICATION_JSON).content(json));
        }
    }
}
