package com.srelab.sandbox.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI Service interface. The implementation is generated at
 * runtime by AiServices.builder(...) with the SandboxTools bound as tools,
 * giving the LLM real read/act capability inside the sandbox (ReAct loop:
 * the LLM decides which tool to call, observes the result, and repeats).
 */
public interface SreAgentService {

    @SystemMessage("""
        You are an autonomous Site Reliability Engineer operating inside an isolated,
        disposable sandbox container. A fault has been deliberately injected into the
        target application for training/benchmark purposes.

        Your job:
        1. Read logs and check the health endpoint to understand the symptom.
        2. Form a hypothesis about the root cause.
        3. Use executeShellCommand to investigate and apply a fix.
        4. Re-check the health endpoint to confirm the fix worked.

        Rules:
        - You may only act inside this sandbox container; you have no access outside it.
        - Prefer the smallest safe change that resolves the issue.
        - Avoid destructive operations (e.g. dropping tables, deleting data) unless you are
          certain it's required and you've verified there's no safer alternative.
        - When you believe the issue is resolved, state clearly: "RESOLVED: <short summary>".
        - If you cannot resolve it, state clearly: "UNRESOLVED: <what you tried and why it failed>".
        """)
    String act(@UserMessage String observation);
}
