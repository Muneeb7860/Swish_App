package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.agent.AgentOrchestrator;
import ch.swissqcommerce.backend.model.AgentEventLog;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/suggest/all")
    public ResponseEntity<Map<String, String>> suggestAll(@RequestBody(required = false) Map<String, String> request) {
        String inputSummary = (request != null && request.containsKey("inputSummary"))
                ? request.get("inputSummary")
                : "Manual trigger";
        
        orchestrator.runOrchestrationAsync(inputSummary);
        
        return ResponseEntity.accepted().body(Map.of(
                "status", "processing",
                "message", "Agent orchestration started asynchronously"
        ));
    }

    @PostMapping("/suggest/debug")
    public ResponseEntity<List<AgentEventLog>> suggestDebug(@RequestBody(required = false) Map<String, String> request) {
        String inputSummary = (request != null && request.containsKey("inputSummary"))
                ? request.get("inputSummary")
                : "Manual debug trigger";
        
        List<AgentEventLog> logs = orchestrator.runOrchestrationSync(inputSummary);
        return ResponseEntity.ok(logs);
    }
}
