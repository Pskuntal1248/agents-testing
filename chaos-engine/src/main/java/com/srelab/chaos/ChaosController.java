package com.srelab.chaos;

import com.srelab.chaos.injector.FaultInjector;
import com.srelab.chaos.agent.AIAgent;
import com.srelab.chaos.evaluator.Evaluator;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chaos")
public class ChaosController {
    
    private final FaultInjector injector;
    private final AIAgent agent;
    private final Evaluator evaluator;
    
    public ChaosController(FaultInjector injector, AIAgent agent, Evaluator evaluator) {
        this.injector = injector;
        this.agent = agent;
        this.evaluator = evaluator;
    }
    
    @PostMapping("/inject/{containerId}")
    public String injectFault(@PathVariable String containerId, @RequestParam String faultType) {
        switch (faultType) {
            case "db-timeout" -> injector.injectDatabaseTimeout(containerId, 5000);
            case "memory-starvation" -> injector.injectMemoryStarvation(containerId, 10 * 1024 * 1024);
            case "config-corruption" -> injector.injectConfigCorruption(containerId, "/app/application.properties");
            default -> throw new IllegalArgumentException("Unknown fault: " + faultType);
        }
        return "Fault injected: " + faultType;
    }
    
    @PostMapping("/diagnose/{containerId}")
    public String diagnose(@PathVariable String containerId, @RequestBody String logs) {
        return agent.diagnose(containerId, logs);
    }
    
    @GetMapping("/evaluate")
    public Evaluator.EvaluationResult evaluate(@RequestParam boolean resolved) {
        return evaluator.finish(resolved);
    }
}
