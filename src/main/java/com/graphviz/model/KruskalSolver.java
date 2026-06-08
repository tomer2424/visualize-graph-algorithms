package com.graphviz.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs Kruskal's algorithm to find a Minimum Spanning Tree (MST).
 *
 * Kruskal's idea, in plain words:
 *   1. Sort all edges from lightest to heaviest.
 *   2. Go through them in that order. Add an edge to the tree only if it
 *      connects two parts that are not already joined. If both ends are
 *      already in the same group, adding it would make a cycle, so skip it.
 *
 * This solver does NOT animate anything. It runs instantly and returns an
 * ordered list of MstStep objects describing what happened to each edge.
 * The controller replays that list to create the visual animation.
 *
 * No JavaFX imports — pure Java, easy to test on its own.
 */
public class KruskalSolver {

    /**
     * Solves the MST for the given graph and returns the ordered list of steps.
     * Each step says which edge was checked and whether it was accepted or rejected.
     */
    public List<MstStep> solve(Graph graph) {
        List<MstStep> steps = new ArrayList<>();

        // Make a copy of the edges so we can sort without touching the graph,
        // then sort them from lightest to heaviest weight.
        List<Edge> sortedEdges = new ArrayList<>(graph.getEdges());
        sortedEdges.sort(Comparator.comparingDouble(Edge::getWeight));

        // Start with every node in its own group.
        UnionFind unionFind = new UnionFind(graph.getNodes());

        // Check each edge in weight order.
        for (Edge edge : sortedEdges) {
            Node a = edge.getSource();
            Node b = edge.getTarget();

            if (unionFind.connected(a, b)) {
                // Both ends already connected — adding this edge makes a cycle.
                steps.add(new MstStep(edge, MstStep.Decision.REJECTED));
            } else {
                // Safe to add — join the two groups and keep the edge.
                unionFind.union(a, b);
                steps.add(new MstStep(edge, MstStep.Decision.ACCEPTED));
            }
        }

        return steps;
    }
}
