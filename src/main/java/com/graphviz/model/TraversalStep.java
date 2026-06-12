package com.graphviz.model;

/**
 * One recorded step in a BFS or DFS traversal.
 *
 * Each step records the edge the algorithm examined, whether it led to a new
 * (undiscovered) node, and which node that was. Shared by BfsSolver and
 * DfsSolver because their steps mean the same thing — the only difference
 * between the two algorithms is the order in which steps are produced.
 *
 * If discovered is true, discoveredNode is the newly reached node (the edge's
 * target). If false, the target was already visited and discoveredNode is null.
 *
 * Plain data — no JavaFX.
 */
public class TraversalStep implements AlgorithmStep {

    private final Edge edge;
    private final boolean discovered;
    private final Node discoveredNode; // the edge's target, only meaningful when discovered

    public TraversalStep(Edge edge, boolean discovered, Node discoveredNode) {
        this.edge = edge;
        this.discovered = discovered;
        this.discoveredNode = discoveredNode;
    }

    @Override
    public Edge getEdge() {
        return edge;
    }

    /** True if this step reached an unvisited node for the first time. */
    public boolean isDiscovered() {
        return discovered;
    }

    /** The node that was just discovered (the edge's target). Null if not discovered. */
    public Node getDiscoveredNode() {
        return discoveredNode;
    }

    @Override
    public String toString() {
        return "TraversalStep{" + edge + ", discovered=" + discovered
                + (discovered ? ", " + discoveredNode.getId() : "") + "}";
    }
}
