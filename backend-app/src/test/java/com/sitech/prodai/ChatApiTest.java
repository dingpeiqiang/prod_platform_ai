package com.sitech.prodai;

import com.sitech.prodai.dto.ChatCompletionRequest;
import com.sitech.prodai.service.LlmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LlmService llmService;

    @Test
    void completionRejectedWhenLlmDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/chat/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"hello"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error_code").value("service_unavailable"));
    }

    @Test
    void streamRejectedWhenLlmDisabled() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setPrompt("hello");
        assertThrows(IllegalStateException.class, () -> llmService.streamEvents(request).blockFirst());
    }
}
