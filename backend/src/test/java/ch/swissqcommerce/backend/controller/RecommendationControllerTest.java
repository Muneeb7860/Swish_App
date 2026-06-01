package ch.swissqcommerce.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

@WebMvcTest(RecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(name = "openAiChatModel")
    private ChatModel chatModel;

    @Test
    public void testGetRecommendations() throws Exception {
        Generation generation = new Generation("Milk, Bread, Eggs");
        ChatResponse chatResponse = new ChatResponse(List.of(generation));

        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse);

        mockMvc.perform(get("/api/recommendations?cartItems=Butter"))
                .andExpect(status().isOk())
                .andExpect(content().string("Milk, Bread, Eggs"));
    }
}
