package com.graphviz.model;

/**
 * One recorded step in Kruskal's algorithm.
 *
 * The solver produces an ordered list of these steps. The controller then
 * plays the list back as an animation: for each step it highlights the edge,
 * then shows whether it was accepted into the tree or rejected as a cycle.
 *
 * Keeping the result as plain data (an Edge plus a decision) means the
 * algorithm has no idea about colors, threads, or the screen — that is the
 * controller's job.
 */
public class MstStep {

    /** Whether the checked edge was added to the tree or skipped. */
    public enum Decision {
        ACCEPTED, // edge joined two separate groups — it is part of the MST
        REJECTED  // edge's endpoints were already connected — it would form a cycle
    }

    private final Edge edge;
    private final Decision decision;

    public MstStep(Edge edge, Decision decision) {
        this.edge = edge;
        this.decision = decision;
    }

    public Edge getEdge() {
        return edge;
    }

    public Decision getDecision() {
        return decision;
    }

    @Override
    public String toString() {
        return "MstStep{" + edge + ", " + decision + "}";
    }
}
