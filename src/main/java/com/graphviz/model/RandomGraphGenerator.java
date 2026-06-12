package com.graphviz.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds a random Graph that can be drawn on a canvas of a given size.
 *
 * The generated graph is always connected — every node can be reached from
 * every other node. This matters for MST and Bellman-Ford, which require a
 * connected graph to produce meaningful results.
 *
 * No JavaFX imports here — the model must stay independent of the UI.
 */
public class RandomGraphGenerator {

    // How far from the canvas edge a node centre can be placed.
    // This stops the circle from being drawn outside the visible area.
    private static final double MARGIN = 50.0;

    // The minimum pixel distance allowed between two node centres.
    // Keeps circles from sitting on top of each other.
    private static final double MIN_NODE_DISTANCE = 80.0;

    // A candidate node centre must be at least this far from any existing edge
    // segment, so nodes don't appear to sit on top of edges.
    private static final double MIN_EDGE_CLEARANCE = 30.0;

    // How many times we try to find a non-overlapping position before giving up.
    private static final int MAX_PLACEMENT_TRIES = 50;

    private final Random random = new Random();

    /**
     * Generates a new random graph whose nodes fit inside the given canvas size.
     *
     * @param canvasWidth  the width of the drawing area in pixels
     * @param canvasHeight the height of the drawing area in pixels
     * @return a fully connected Graph with 5–8 nodes and weighted edges
     */
    public Graph generate(double canvasWidth, double canvasHeight) {
        Graph graph = new Graph();

        int nodeCount = 5 + random.nextInt(4); // 5, 6, 7, or 8

        // Place nodes one by one, keeping them inside bounds, not overlapping
        // each other, and not landing on top of any already-placed edge.
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            String id = String.valueOf((char) ('A' + i));
            Node node = placeNode(id, canvasWidth, canvasHeight, nodes, edges);
            nodes.add(node);
            graph.addNode(node);
        }

        // --- Build a connected spanning chain first ---
        // Shuffle the node list and link each consecutive pair. This guarantees
        // that every node is reachable, even before we add extra edges.
        List<Node> shuffled = new ArrayList<>(nodes);
        Collections.shuffle(shuffled, random);

        // Track which pairs already have an edge so we never add duplicates.
        Set<String> existingEdges = new HashSet<>();

        for (int i = 0; i < shuffled.size() - 1; i++) {
            Node a = shuffled.get(i);
            Node b = shuffled.get(i + 1);
            edges.add(addUndirectedEdge(graph, a, b, existingEdges));
        }

        // --- Add a few extra random edges for variety ---
        // Aim for roughly one extra edge per two nodes, but never more than
        // the total number of possible unique pairs.
        int extraEdges = nodeCount / 2;
        int attempts = 0;
        int added = 0;
        while (added < extraEdges && attempts < 50) {
            Node a = nodes.get(random.nextInt(nodeCount));
            Node b = nodes.get(random.nextInt(nodeCount));
            if (!a.equals(b) && !existingEdges.contains(edgeKey(a, b))) {
                edges.add(addUndirectedEdge(graph, a, b, existingEdges));
                added++;
            }
            attempts++;
        }

        return graph;
    }

    /**
     * Finds a position for a new node that is inside the canvas margin and
     * not too close to any already-placed node. Falls back to the best
     * available position if MAX_PLACEMENT_TRIES is reached without a good spot.
     */
    private Node placeNode(String id, double canvasWidth, double canvasHeight,
                            List<Node> existing, List<Edge> existingEdges) {
        double usableWidth  = canvasWidth  - 2 * MARGIN;
        double usableHeight = canvasHeight - 2 * MARGIN;

        for (int attempt = 0; attempt < MAX_PLACEMENT_TRIES; attempt++) {
            double x = MARGIN + random.nextDouble() * usableWidth;
            double y = MARGIN + random.nextDouble() * usableHeight;

            if (isFarEnough(x, y, existing, existingEdges)) {
                return new Node(id, x, y);
            }
        }

        // Give up and place at the last random position (only on very small canvases).
        double x = MARGIN + random.nextDouble() * usableWidth;
        double y = MARGIN + random.nextDouble() * usableHeight;
        return new Node(id, x, y);
    }

    /** Returns true if (x, y) is far from every node centre and every edge segment. */
    private boolean isFarEnough(double x, double y, List<Node> nodes, List<Edge> edges) {
        for (Node n : nodes) {
            double dx = n.getX() - x;
            double dy = n.getY() - y;
            if (Math.sqrt(dx * dx + dy * dy) < MIN_NODE_DISTANCE) {
                return false;
            }
        }
        for (Edge e : edges) {
            if (distanceToSegment(x, y, e.getSource().getX(), e.getSource().getY(),
                                        e.getTarget().getX(), e.getTarget().getY())
                    < MIN_EDGE_CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    /** Point-to-segment distance between (px, py) and the segment (x1,y1)→(x2,y2). */
    private double distanceToSegment(double px, double py,
                                     double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    /**
     * Adds one undirected edge (stored as a single directed edge) with a
     * random weight between 1 and 20. Returns the created Edge so the caller
     * can track it for clearance checks.
     */
    private Edge addUndirectedEdge(Graph graph, Node a, Node b,
                                   Set<String> existingEdges) {
        int weight = 1 + random.nextInt(20);
        graph.addEdge(a, b, weight);
        existingEdges.add(edgeKey(a, b));
        // Return the edge that was just added (last in the graph's list).
        List<Edge> all = graph.getEdges();
        return all.get(all.size() - 1);
    }

    /**
     * Makes a canonical key for a pair of nodes so that (A,B) and (B,A)
     * are treated as the same edge. We sort the ids alphabetically.
     */
    private String edgeKey(Node a, Node b) {
        String idA = a.getId();
        String idB = b.getId();
        return idA.compareTo(idB) <= 0 ? idA + "-" + idB : idB + "-" + idA;
    }
}
