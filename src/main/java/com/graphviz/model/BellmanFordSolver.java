package com.graphviz.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs Bellman-Ford to find the shortest path from one source node to every
 * other node. Works on directed graphs and allows negative edge weights.
 *
 * The idea, in plain words:
 *   1. Start with the source distance = 0 and every other distance = infinity.
 *   2. Go over every edge and "relax" it: if reaching the target through the
 *      source is cheaper than the target's current distance, lower it.
 *   3. Repeat this whole sweep (V − 1) times, where V is the number of nodes.
 *      After that many sweeps the shortest distances are settled — unless there
 *      is a negative cycle.
 *   4. Do ONE more sweep. If any edge can still be relaxed, a negative-weight
 *      cycle exists (you could loop around it forever to lower the distance).
 *
 * Like KruskalSolver, this does not animate or sleep. It runs instantly and
 * returns an ordered list of steps plus the final result. No JavaFX.
 */
public class BellmanFordSolver {

    public BellmanFordResult solve(Graph graph, Node source) {
        List<AlgorithmStep> steps = new ArrayList<>();
        List<Node> nodes = graph.getNodes();
        List<Edge> edges = graph.getEdges();

        // distance[node] = best known distance from the source so far.
        Map<Node, Double> distance = new HashMap<>();
        // predecessor[node] = the edge we arrived on to reach this node cheaply.
        // Used both for the result and to trace a negative cycle.
        Map<Node, Edge> predecessor = new HashMap<>();

        for (Node node : nodes) {
            distance.put(node, Double.POSITIVE_INFINITY);
        }
        distance.put(source, 0.0);

        int vertexCount = nodes.size();

        // --- Step 2 & 3: relax all edges (V - 1) times ---
        for (int pass = 0; pass < vertexCount - 1; pass++) {
            for (Edge edge : edges) {
                Node from = edge.getSource();
                Node to   = edge.getTarget();

                double fromDist = distance.get(from);
                double throughEdge = fromDist + edge.getWeight();

                // We can only improve the target if the source is reachable
                // (its distance is not still infinity).
                boolean improved = fromDist != Double.POSITIVE_INFINITY
                        && throughEdge < distance.get(to);

                if (improved) {
                    distance.put(to, throughEdge);
                    predecessor.put(to, edge);
                    steps.add(new BellmanFordStep(edge, true, to, throughEdge));
                } else {
                    // Record the attempt even when nothing changed, so the
                    // animation shows the edge being checked.
                    steps.add(new BellmanFordStep(edge, false, null, 0));
                }
            }
        }

        // --- Step 4: one more sweep to detect a negative cycle ---
        boolean negativeCycle = false;
        Node nodeInsideCycle = null;

        for (Edge edge : edges) {
            Node from = edge.getSource();
            Node to   = edge.getTarget();
            double fromDist = distance.get(from);

            if (fromDist != Double.POSITIVE_INFINITY
                    && fromDist + edge.getWeight() < distance.get(to)) {
                // This edge can still be relaxed — a negative cycle exists.
                negativeCycle = true;
                predecessor.put(to, edge); // keep the link so we can trace back
                nodeInsideCycle = to;
                break;
            }
        }

        List<Edge> cycleEdges = new ArrayList<>();
        if (negativeCycle) {
            cycleEdges = traceCycle(nodeInsideCycle, predecessor, vertexCount);
        }

        return new BellmanFordResult(steps, distance, negativeCycle, cycleEdges);
    }

    /**
     * Finds the exact edges of the negative cycle.
     *
     * The node we found may only LEAD INTO the cycle, not be on it yet. So we
     * first walk backwards V times along predecessor edges — that guarantees we
     * land on a node that is truly inside the cycle. From there we follow
     * predecessors around until we return to that node, collecting the edges.
     */
    private List<Edge> traceCycle(Node start, Map<Node, Edge> predecessor, int vertexCount) {
        // Walk back V steps to be sure we are inside the cycle.
        Node current = start;
        for (int i = 0; i < vertexCount; i++) {
            Edge in = predecessor.get(current);
            if (in == null) {
                break; // safety: no predecessor (should not happen in a real cycle)
            }
            current = in.getSource();
        }

        // Now follow predecessors around the cycle until we come back to 'current'.
        List<Edge> cycleEdges = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        Node walker = current;

        while (walker != null && !visited.contains(walker)) {
            visited.add(walker);
            Edge in = predecessor.get(walker);
            if (in == null) {
                break;
            }
            cycleEdges.add(in);
            walker = in.getSource();
            if (walker.equals(current)) {
                break; // completed the loop back to the start of the cycle
            }
        }

        return cycleEdges;
    }
}
