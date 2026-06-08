package com.graphviz.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Disjoint Set (also called Union-Find) data structure.
 *
 * Kruskal's algorithm uses this to answer one question quickly:
 * "are these two nodes already connected?" If they are, adding an edge
 * between them would create a cycle, so the edge is rejected.
 *
 * Two well-known speed-ups are used:
 *   - Path compression: when we look up a node's group, we flatten the path
 *     so future lookups are faster.
 *   - Union by rank: when joining two groups, the shorter tree is hung under
 *     the taller one, which keeps the trees shallow.
 *
 * No JavaFX imports — this is plain Java so it can be tested on its own.
 */
public class UnionFind {

    // For each node, which node is its parent in the group tree.
    // A node that is its own parent is the "root" (representative) of its group.
    private final Map<Node, Node> parent = new HashMap<>();

    // A rough measure of each group tree's height, used by union by rank.
    private final Map<Node, Integer> rank = new HashMap<>();

    /**
     * Starts with every node in its own separate group.
     */
    public UnionFind(List<Node> nodes) {
        for (Node node : nodes) {
            parent.put(node, node); // each node is its own root at the start
            rank.put(node, 0);
        }
    }

    /**
     * Returns the representative (root) of the group that the node belongs to.
     * Uses path compression: every node visited on the way up is re-pointed
     * directly at the root.
     */
    public Node find(Node node) {
        Node root = parent.get(node);
        if (!root.equals(node)) {
            root = find(root);     // walk up to the real root
            parent.put(node, root); // path compression
        }
        return root;
    }

    /**
     * Joins the groups that a and b belong to.
     * Does nothing if they are already in the same group.
     */
    public void union(Node a, Node b) {
        Node rootA = find(a);
        Node rootB = find(b);

        if (rootA.equals(rootB)) {
            return; // already in the same group
        }

        // Hang the shorter tree under the taller one (union by rank).
        int rankA = rank.get(rootA);
        int rankB = rank.get(rootB);

        if (rankA < rankB) {
            parent.put(rootA, rootB);
        } else if (rankA > rankB) {
            parent.put(rootB, rootA);
        } else {
            // Same height — pick one as the new root and raise its rank by one.
            parent.put(rootB, rootA);
            rank.put(rootA, rankA + 1);
        }
    }

    /**
     * Returns true if a and b are already in the same group.
     * This is the cycle check Kruskal's algorithm relies on.
     */
    public boolean connected(Node a, Node b) {
        return find(a).equals(find(b));
    }
}
