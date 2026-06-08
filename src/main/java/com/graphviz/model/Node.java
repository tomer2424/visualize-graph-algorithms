package com.graphviz.model;

import java.util.Objects;

/**
 * A single vertex in the graph.
 * The x and y fields store the node's position on the canvas so the view
 * knows where to draw it. They are mutable because the user can drag nodes.
 */
public class Node {

    private final String id;
    private double x;
    private double y;

    public Node(String id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public String getId() { return id; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    // Two nodes are the same if they share the same id, regardless of position.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Node{id='" + id + "', x=" + x + ", y=" + y + "}";
    }
}
