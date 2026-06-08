package com.graphviz.model;

/**
 * A connection between two nodes in the graph, with an optional numeric weight.
 *
 * The edge is stored as directed (source → target) to support both MST (where
 * direction does not matter) and Bellman-Ford (where it does). For undirected
 * use, the Graph class adds edges in both directions as needed.
 */
public class Edge {

    private final Node source;
    private final Node target;
    private double weight;

    public Edge(Node source, Node target, double weight) {
        this.source = source;
        this.target = target;
        this.weight = weight;
    }

    public Node getSource() { return source; }
    public Node getTarget() { return target; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    @Override
    public String toString() {
        return "Edge{" + source.getId() + " -> " + target.getId()
                + ", weight=" + weight + "}";
    }
}
