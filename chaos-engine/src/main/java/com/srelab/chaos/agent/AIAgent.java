package com.srelab.chaos.agent;

import org.springframework.stereotype.Service;

@Service
public class AIAgent {
    
    public String diagnose(String containerId, String logs) {
       
        return "Analysis pending";
    }
    
    public String executeAction(String containerId, String action) {
       
        return "Action executed";
    }
}
