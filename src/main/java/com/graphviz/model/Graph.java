package com.graphviz.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The graph that algorithms will operate on.
 *
 * This class has no JavaFX imports on purpose. Keeping the model free of UI
 * code means algorithms can be tested independently of the application window.
 */
public class Graph {

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    // --- Mutation ---

    public void addNode(Node node) {
        nodes.add(node);
    }

    /**
     * Adds a directed edge from source to target with the given weight.
     * Call this twice (swapping source/target) to create an undirected edge.
     */
    public void addEdge(Node source, Node target, double weight) {
        edges.add(new Edge(source, target, weight));
    }

    /** Removes all nodes and edges, ready for a new graph to be built. */
    public void clear() {
        nodes.clear();
        edges.clear();
    }

    /** Removes a node and all edges that touch it (as source or target). */
    public void removeNode(Node node) {
        nodes.remove(node);
        edges.removeIf(e -> e.getSource().equals(node) || e.getTarget().equals(node));
    }

    /** Removes a single directed edge. */
    public void removeEdge(Edge edge) {
        edges.remove(edge);
    }

    // --- Queries ---

    public List<Node> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<Edge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Returns all edges that start at the given node.
     * Useful for graph traversal algorithms like Bellman-Ford.
     */
    public List<Edge> getNeighbors(Node node) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.getSource().equals(node)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Returns the next unused node id.
     * First tries single letters A–Z; once those are taken, falls back to
     * "N" followed by the current node count (e.g. "N26", "N27", …).
     * This keeps manually added node ids short and unique even after a
     * random graph has already claimed A through H.
     */
    public String nextNodeId() {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Node n : nodes) {
            used.add(n.getId());
        }
        // Single letters A–Z, then doubled letters AA–ZZ (52 total).
        for (char c = 'A'; c <= 'Z'; c++) {
            String single = String.valueOf(c);
            if (!used.contains(single)) return single;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            String doubled = String.valueOf(c) + c;
            if (!used.contains(doubled)) return doubled;
        }
        // All 52 slots taken — no more nodes allowed.
        return null;
    }

    /**
     * Returns true if an edge already exists between a and b in either direction.
     * The random graph generator uses this to avoid building two edges for the
     * same pair of nodes.
     */
    public boolean hasEdgeBetween(Node a, Node b) {
        for (Edge edge : edges) {
            boolean forward  = edge.getSource().equals(a) && edge.getTarget().equals(b);
            boolean backward = edge.getSource().equals(b) && edge.getTarget().equals(a);
            if (forward || backward) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if a directed edge already goes from source to target.
     * Because the graph is directed, this checks one direction only — so the
     * user can still add the opposite edge (target → source) separately.
     */
    public boolean hasDirectedEdge(Node source, Node target) {
        for (Edge edge : edges) {
            if (edge.getSource().equals(source) && edge.getTarget().equals(target)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Graph{nodes=" + nodes.size() + ", edges=" + edges.size() + "}";
    }
}
