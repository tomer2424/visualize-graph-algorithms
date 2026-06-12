package com.graphviz.model;

/**
 * One step in any graph algorithm animation.
 *
 * Every algorithm (Kruskal, Prim, Bellman-Ford, BFS, DFS) produces an ordered
 * list of steps that the controller plays back. Every step is tied to one edge —
 * the edge that the algorithm is looking at during that step — which the
 * controller highlights.
 *
 * The shared playback engine only needs to know "which edge is this step about".
 * For details specific to each algorithm (was the edge accepted? did a distance
 * improve? was a node discovered?), the controller checks the concrete step type
 * (MstStep, BellmanFordStep, or TraversalStep).
 *
 * No JavaFX imports — these are plain data objects.
 */
public interface AlgorithmStep {

    /** The edge this step is looking at, so the controller can highlight it. */
    Edge getEdge();
}
