package com.medha.realtimechatservice.controller;

import com.medha.realtimechatservice.dto.ChatRoomCreateRequest;
import com.medha.realtimechatservice.dto.ChatRoomResponse;
import com.medha.realtimechatservice.exception.RoomNotFoundException;
import com.medha.realtimechatservice.service.ChatRoomService;
import com.medha.realtimechatservice.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRoomController.class)
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @MockitoBean
    private PresenceService presenceService;

    @Test
    void createRoomReturns201WithLocationBody() throws Exception {
        ChatRoomCreateRequest request = new ChatRoomCreateRequest("General", "General chat");
        ChatRoomResponse response = new ChatRoomResponse(1L, "ABC123", "General", "General chat",
                "ACTIVE", Instant.now(), 0);
        when(chatRoomService.createRoom(any())).thenReturn(response);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomCode").value("ABC123"))
                .andExpect(jsonPath("$.name").value("General"));
    }

    @Test
    void createRoomReturns400WhenNameBlank() throws Exception {
        ChatRoomCreateRequest invalidRequest = new ChatRoomCreateRequest(" ", "desc");

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").exists());
    }

    @Test
    void listRoomsReturnsAllRooms() throws Exception {
        ChatRoomResponse response = new ChatRoomResponse(1L, "ABC123", "General", null,
                "ACTIVE", Instant.now(), 2);
        when(chatRoomService.listRooms()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomCode").value("ABC123"))
                .andExpect(jsonPath("$[0].onlineUsers").value(2));
    }

    @Test
    void getRoomReturns404WhenNotFound() throws Exception {
        when(chatRoomService.getRoom("MISSING")).thenThrow(new RoomNotFoundException("MISSING"));

        mockMvc.perform(get("/api/rooms/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getParticipantsReturnsOnlineUsernames() throws Exception {
        when(chatRoomService.findRoomOrThrow("ABC123")).thenReturn(null);
        when(presenceService.getOnlineUsers("ABC123")).thenReturn(Set.of("alice", "bob"));

        mockMvc.perform(get("/api/rooms/ABC123/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.roomCode").value("ABC123"));
    }
}
