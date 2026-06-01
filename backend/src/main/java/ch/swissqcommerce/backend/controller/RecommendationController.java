package ch.swissqcommerce.backend.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final ChatClient chatClient;

    public RecommendationController(@Qualifier("openAiChatModel") ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @GetMapping
    public String getRecommendations(@RequestParam String cartItems) {
        return chatClient.prompt()
                .user("Suggest exactly 3 grocery items that complement these cart items: " + cartItems)
                .call()
                .content();
    }
}
