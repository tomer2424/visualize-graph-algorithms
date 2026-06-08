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

    // How many times we try to find a non-overlapping position before giving up.
    private static final int MAX_PLACEMENT_TRIES = 30;

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

        // Place nodes one by one, keeping them inside bounds and not overlapping.
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            String id = String.valueOf((char) ('A' + i));
            Node node = placeNode(id, canvasWidth, canvasHeight, nodes);
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
            addUndirectedEdge(graph, a, b, existingEdges);
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
                addUndirectedEdge(graph, a, b, existingEdges);
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
                            List<Node> existing) {
        double usableWidth  = canvasWidth  - 2 * MARGIN;
        double usableHeight = canvasHeight - 2 * MARGIN;

        for (int attempt = 0; attempt < MAX_PLACEMENT_TRIES; attempt++) {
            double x = MARGIN + random.nextDouble() * usableWidth;
            double y = MARGIN + random.nextDouble() * usableHeight;

            if (isFarEnough(x, y, existing)) {
                return new Node(id, x, y);
            }
        }

        // Give up trying and place the node at the last random position found.
        // This can only happen on very small canvases with many nodes.
        double x = MARGIN + random.nextDouble() * usableWidth;
        double y = MARGIN + random.nextDouble() * usableHeight;
        return new Node(id, x, y);
    }

    /** Returns true if (x, y) is far enough from every already-placed node. */
    private boolean isFarEnough(double x, double y, List<Node> existing) {
        for (Node n : existing) {
            double dx = n.getX() - x;
            double dy = n.getY() - y;
            if (Math.sqrt(dx * dx + dy * dy) < MIN_NODE_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds one undirected edge (stored as a single directed edge) with a
     * random weight between 1 and 20. Also records the pair in existingEdges
     * so we never create a duplicate.
     */
    private void addUndirectedEdge(Graph graph, Node a, Node b,
                                    Set<String> existingEdges) {
        int weight = 1 + random.nextInt(20);
        graph.addEdge(a, b, weight);
        existingEdges.add(edgeKey(a, b));
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
