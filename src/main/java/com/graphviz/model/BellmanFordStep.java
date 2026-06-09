package com.graphviz.model;

/**
 * One step in the Bellman-Ford animation.
 *
 * Bellman-Ford works by "relaxing" edges over and over. Relaxing an edge
 * source → target means: "if going through source reaches target with a
 * smaller distance than we have so far, write down that smaller distance."
 *
 * Each step records one relaxation attempt:
 *   - the edge that was checked,
 *   - whether the target's distance actually improved,
 *   - and if it improved, the target node plus its new distance value.
 *
 * The controller replays these steps to flash edges and update the distance
 * numbers shown on the nodes. Plain data — no JavaFX.
 */
public class BellmanFordStep implements AlgorithmStep {

    private final Edge edge;
    private final boolean improved;
    private final Node updatedNode;     // the target node, only meaningful when improved
    private final double newDistance;   // the target's new distance, only when improved

    public BellmanFordStep(Edge edge, boolean improved, Node updatedNode, double newDistance) {
        this.edge = edge;
        this.improved = improved;
        this.updatedNode = updatedNode;
        this.newDistance = newDistance;
    }

    @Override
    public Edge getEdge() {
        return edge;
    }

    /** True if this relaxation lowered the target node's distance. */
    public boolean isImproved() {
        return improved;
    }

    /** The node whose distance changed (the edge's target). */
    public Node getUpdatedNode() {
        return updatedNode;
    }

    /** The new, smaller distance written onto the target node. */
    public double getNewDistance() {
        return newDistance;
    }

    @Override
    public String toString() {
        return "BellmanFordStep{" + edge + ", improved=" + improved
                + (improved ? ", " + updatedNode.getId() + "=" + newDistance : "") + "}";
    }
}
