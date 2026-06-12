package com.graphviz.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs Depth-First Search from a chosen starting node.
 *
 * DFS follows one path as deep as it goes before backing up and trying another
 * branch. This implementation is iterative — it uses an explicit edge stack
 * instead of recursion so that every examined edge becomes one distinct,
 * replayable step in the animation.
 *
 * The stack holds edges, not nodes. Pushing a node's outgoing edges in reverse
 * order means its first-listed edge pops first, and when that edge discovers a
 * new node its edges are pushed on top — this produces the same visit order as
 * recursive DFS.
 *
 * Traversal follows arrow directions only (directed). Nodes with no directed
 * path from the start will not be visited.
 *
 * Runs instantly and returns a plain list of steps. No JavaFX — that is the
 * controller's job.
 */
public class DfsSolver {

    public List<TraversalStep> solve(Graph graph, Node start) {
        List<TraversalStep> steps = new ArrayList<>();

        Set<Node> visited = new HashSet<>();
        // ArrayDeque used as a LIFO stack — Java's recommended stack type.
        // (Legacy java.util.Stack is a synchronized Vector and should be avoided.)
        Deque<Edge> stack = new ArrayDeque<>();

        visited.add(start);
        pushOutgoingReversed(graph, start, stack);

        while (!stack.isEmpty()) {
            Edge edge = stack.pop();
            Node neighbor = edge.getTarget();

            if (visited.contains(neighbor)) {
                // A deeper path already reached this node before this edge was popped —
                // the same "already visited" outcome that recursive DFS produces when
                // it iterates to a visited neighbor.
                steps.add(new TraversalStep(edge, false, null));
            } else {
                visited.add(neighbor);
                steps.add(new TraversalStep(edge, true, neighbor));
                pushOutgoingReversed(graph, neighbor, stack);
            }
        }

        return steps;
    }

    /** Push all outgoing edges of a node in reverse order so the first edge pops first. */
    private void pushOutgoingReversed(Graph graph, Node node, Deque<Edge> stack) {
        List<Edge> outgoing = graph.getNeighbors(node);
        for (int i = outgoing.size() - 1; i >= 0; i--) {
            stack.push(outgoing.get(i));
        }
    }
}
