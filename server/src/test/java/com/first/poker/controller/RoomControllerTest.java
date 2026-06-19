package com.first.poker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateRoom() throws Exception {
        String body = """
            {"name":"Alice\u7684\u724c\u5c40","maxSeats":6,"smallBlind":5,"initialChips":2000}
            """;

        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").isString())
                .andExpect(jsonPath("$.name").value("Alice的牌局"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.config.maxSeats").value(6));
    }

    @Test
    void shouldJoinRoom() throws Exception {
        String createBody = "{\"name\":\"test\"}";
        var createRes = mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        String roomId = com.jayway.jsonpath.JsonPath
                .read(createRes.getResponse().getContentAsString(), "$.roomId");

        String joinBody = "{\"playerId\":\"p1\",\"nickname\":\"Alice\"}";
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(joinBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.players[0].nickname").value("Alice"));
    }

    @Test
    void shouldBorrowChips() throws Exception {
        // Create a room first
        String createBody = "{\"name\":\"borrow-test\",\"initialChips\":1000}";
        var createRes = mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        String roomId = com.jayway.jsonpath.JsonPath
                .read(createRes.getResponse().getContentAsString(), "$.roomId");

        // Join the room
        String joinBody = "{\"playerId\":\"p1\",\"nickname\":\"Borrower\"}";
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(joinBody))
                .andExpect(status().isOk());

        // Borrow chips — ensures executeWithLock wrapping doesn't break functionality
        String borrowBody = "{\"playerId\":\"p1\"}";
        mockMvc.perform(post("/api/rooms/" + roomId + "/borrow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(borrowBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value("p1"))
                .andExpect(jsonPath("$.chips").isNumber())
                .andExpect(jsonPath("$.borrowCount").value(1));
    }

    @Test
    void shouldReturn404ForNonExistentRoom() throws Exception {
        mockMvc.perform(post("/api/rooms/NOEXIST/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"p1\",\"nickname\":\"Bob\"}"))
                .andExpect(status().isNotFound());
    }
}
