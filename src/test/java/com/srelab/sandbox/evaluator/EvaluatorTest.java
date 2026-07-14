package com.srelab.sandbox.evaluator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorTest {

    @Test
    void unresolvedRunScoresZero() {
        Evaluator evaluator = new Evaluator();
        evaluator.startEvaluation();
        evaluator.recordCommand();
        evaluator.recordCommand();

        Evaluator.EvaluationResult result = evaluator.finish(false);

        assertFalse(result.resolved());
        assertEquals(0, result.score());
        assertEquals(2, result.commandsExecuted());
    }

    @Test
    void resolvedRunWithFewCommandsScoresHigh() throws InterruptedException {
        Evaluator evaluator = new Evaluator();
        evaluator.startEvaluation();
        evaluator.recordCommand();
        Thread.sleep(10);

        Evaluator.EvaluationResult result = evaluator.finish(true);

        assertTrue(result.resolved());
        assertTrue(result.score() > 0, "expected a positive score for a fast, resolved run");
        assertEquals(1, result.commandsExecuted());
    }

    @Test
    void resolvedRunWithManyCommandsScoresLowerThanFewCommands() {
        Evaluator fewCommands = new Evaluator();
        fewCommands.startEvaluation();
        fewCommands.recordCommand();
        Evaluator.EvaluationResult fewResult = fewCommands.finish(true);

        Evaluator manyCommands = new Evaluator();
        manyCommands.startEvaluation();
        for (int i = 0; i < 20; i++) {
            manyCommands.recordCommand();
        }
        Evaluator.EvaluationResult manyResult = manyCommands.finish(true);

        assertTrue(fewResult.score() >= manyResult.score(),
            "fewer commands should score at least as high as many commands, given similar TTR");
    }
}
