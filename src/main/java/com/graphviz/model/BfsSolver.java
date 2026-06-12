package com.graphviz.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Runs Breadth-First Search from a chosen starting node.
 *
 * BFS explores the graph in waves — first all nodes one edge away from the
 * start, then two edges away, and so on. It uses a FIFO queue: the node that
 * has been waiting longest is always explored next, which is what produces the
 * level-by-level wave order.
 *
 * Traversal follows arrow directions only. An edge A→B can only be used to
 * reach B from A, not the other way around. Nodes with no directed path from
 * the start will not be visited.
 *
 * Runs instantly and returns a plain list of steps. No JavaFX, no animation —
 * that is the controller's job.
 */
public class BfsSolver {

    public List<TraversalStep> solve(Graph graph, Node start) {
        List<TraversalStep> steps = new ArrayList<>();

        Set<Node> visited = new HashSet<>();
        // ArrayDeque used as a FIFO queue — the queue is what makes this breadth-first.
        Queue<Node> queue = new ArrayDeque<>();

        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();

            for (Edge edge : graph.getNeighbors(current)) {
                Node neighbor = edge.getTarget();

                if (visited.contains(neighbor)) {
                    // Already reached this node — record the attempt so the animation
                    // can show the edge being checked and then fade it out.
                    steps.add(new TraversalStep(edge, false, null));
                } else {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    steps.add(new TraversalStep(edge, true, neighbor));
                }
            }
        }

        return steps;
    }
}
