package ch.swissqcommerce.backend.domain.agent.adapter.in.web;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentMetrics;
import ch.swissqcommerce.backend.domain.agent.port.in.AgentUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://localhost:3002", "http://localhost:3003", "http://localhost:8081"})
public class AgentController {

    @Autowired
    private AgentUseCase agentUseCase;

    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request) {
        AgentResponse response = agentUseCase.processMessage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<AgentMetrics> getMetrics() {
        return ResponseEntity.ok(agentUseCase.getMetrics());
    }
}

