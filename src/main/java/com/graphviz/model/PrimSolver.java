package com.graphviz.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Runs Prim's algorithm to find a Minimum Spanning Tree (MST) starting from a
 * chosen node.
 *
 * Prim's idea, in plain words:
 *   1. Start with just the source node in the tree.
 *   2. Look at every edge that leads out of the tree to a node not yet in it.
 *      Pick the lightest one and add that edge (and the new node) to the tree.
 *   3. Repeat until no more edges reach new nodes.
 *
 * A min-heap (PriorityQueue) keeps track of candidate edges so we always pull
 * the lightest one in O(log n) time rather than scanning everything.
 *
 * Directed edges are treated as undirected for MST purposes — an edge A→B can
 * be used to grow the tree toward either A or B. This is consistent with how
 * Kruskal handles the same directed edges.
 *
 * Like KruskalSolver, this runs instantly and returns a plain list of steps.
 * No JavaFX, no animation — that is the controller's job.
 */
public class PrimSolver {

    public List<MstStep> solve(Graph graph, Node start) {
        List<MstStep> steps = new ArrayList<>();

        // Build an undirected adjacency map from the directed edge list.
        // Each edge is reachable from both its source and its target.
        Map<Node, List<Edge>> adjacency = new HashMap<>();
        for (Node n : graph.getNodes()) {
            adjacency.put(n, new ArrayList<>());
        }
        for (Edge edge : graph.getEdges()) {
            adjacency.get(edge.getSource()).add(edge);
            adjacency.get(edge.getTarget()).add(edge);
        }

        // visited = nodes already inside the growing tree.
        Set<Node> visited = new HashSet<>();
        visited.add(start);

        // The heap always holds candidate edges sorted by weight (lightest first).
        PriorityQueue<Edge> queue = new PriorityQueue<>(Comparator.comparingDouble(Edge::getWeight));
        queue.addAll(adjacency.get(start));

        while (!queue.isEmpty()) {
            Edge edge = queue.poll();
            Node source = edge.getSource();
            Node target = edge.getTarget();

            // Figure out which endpoint is already in the tree and which is new.
            boolean sourceVisited = visited.contains(source);
            boolean targetVisited = visited.contains(target);

            if (sourceVisited && targetVisited) {
                // Both ends are already in the tree — accepting this edge would
                // create a cycle. Reject it (will show red then fade to gray).
                steps.add(new MstStep(edge, MstStep.Decision.REJECTED));
                continue;
            }

            // One end is new — accept the edge and grow the tree toward it.
            steps.add(new MstStep(edge, MstStep.Decision.ACCEPTED));
            Node newNode = sourceVisited ? target : source;
            visited.add(newNode);

            // Add all edges from the new node that lead somewhere not yet visited.
            for (Edge candidate : adjacency.get(newNode)) {
                Node other = candidate.getSource().equals(newNode)
                        ? candidate.getTarget()
                        : candidate.getSource();
                if (!visited.contains(other)) {
                    queue.add(candidate);
                }
            }
        }

        return steps;
    }
}
