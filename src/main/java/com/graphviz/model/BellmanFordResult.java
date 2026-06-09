package com.graphviz.model;

import java.util.List;
import java.util.Map;

/**
 * The full outcome of a Bellman-Ford run.
 *
 * Holds everything the controller needs to animate and to show the final
 * picture:
 *   - steps: the ordered relaxation steps to play back,
 *   - finalDistances: each node's shortest distance from the source at the end,
 *   - negativeCycle: true if a negative-weight cycle was found,
 *   - cycleEdges: if a cycle was found, the exact edges that form it (for the
 *     red highlight); empty otherwise.
 *
 * Plain data — no JavaFX.
 */
public class BellmanFordResult {

    private final List<AlgorithmStep> steps;
    private final Map<Node, Double> finalDistances;
    private final boolean negativeCycle;
    private final List<Edge> cycleEdges;

    public BellmanFordResult(List<AlgorithmStep> steps,
                             Map<Node, Double> finalDistances,
                             boolean negativeCycle,
                             List<Edge> cycleEdges) {
        this.steps = steps;
        this.finalDistances = finalDistances;
        this.negativeCycle = negativeCycle;
        this.cycleEdges = cycleEdges;
    }

    public List<AlgorithmStep> getSteps() {
        return steps;
    }

    public Map<Node, Double> getFinalDistances() {
        return finalDistances;
    }

    public boolean hasNegativeCycle() {
        return negativeCycle;
    }

    public List<Edge> getCycleEdges() {
        return cycleEdges;
    }
}
