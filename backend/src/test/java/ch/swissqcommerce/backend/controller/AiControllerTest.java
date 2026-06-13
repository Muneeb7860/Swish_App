package ch.swissqcommerce.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swissqcommerce.backend.service.AiOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AiOrchestrationService aiOrchestrationService;

    @Test
    public void testOrchestrate_Success() throws Exception {
        when(aiOrchestrationService.orchestrateComplexTask(eq("test-prompt")))
                .thenReturn(Flux.just("token1", "token2"));

        MvcResult mvcResult =
                mockMvc.perform(
                                post("/api/ai/orchestrate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"prompt\":\"test-prompt\"}"))
                        .andExpect(status().isOk())
                        .andReturn();

        // Standard WebMvc async handling
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string("data:token1\n\ndata:token2\n\n"));
    }

    @Test
    public void testOrchestrate_EmptyPrompt() throws Exception {
        mockMvc.perform(
                        post("/api/ai/orchestrate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prompt\":\"\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testLocalTask_Success() throws Exception {
        when(aiOrchestrationService.executeLocalTask(eq("local-prompt")))
                .thenReturn(Flux.just("response1"));

        MvcResult mvcResult =
                mockMvc.perform(
                                post("/api/ai/local")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"prompt\":\"local-prompt\"}"))
                        .andExpect(status().isOk())
                        .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string("data:response1\n\n"));
    }

    @Test
    public void testLocalTask_EmptyPrompt() throws Exception {
        mockMvc.perform(post("/api/ai/local").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }
}
