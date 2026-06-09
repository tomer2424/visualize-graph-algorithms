package com.graphviz.model;

/**
 * A directed connection from a source node to a target node, with a weight.
 *
 * The whole app now treats the graph as directed: the edge always points from
 * source → target. Kruskal's MST simply ignores the direction, while
 * Bellman-Ford uses it (and allows negative weights). Two separate edges
 * A → B and B → A are allowed to model a two-way connection.
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
