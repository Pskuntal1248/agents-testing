package com.srelab.sandbox.evaluator;

import org.springframework.stereotype.Service;

@Service
public class Evaluator {

    private long startTime;
    private int commandCount;

    public void startEvaluation() {
        this.startTime = System.currentTimeMillis();
        this.commandCount = 0;
    }

    public void recordCommand() {
        this.commandCount++;
    }

    public EvaluationResult finish(boolean resolved) {
        long ttr = System.currentTimeMillis() - startTime;
        int score = calculateScore(ttr, commandCount, resolved);
        return new EvaluationResult(ttr, commandCount, score, resolved);
    }

    private int calculateScore(long ttr, int commands, boolean resolved) {
        if (!resolved) return 0;
        int timeScore = Math.max(0, 50 - (int) (ttr / 1000));
        int efficiencyScore = Math.max(0, 50 - (commands * 5));
        return Math.min(100, timeScore + efficiencyScore);
    }

    public record EvaluationResult(long timeToResolveMs, int commandsExecuted, int score, boolean resolved) {}
}
