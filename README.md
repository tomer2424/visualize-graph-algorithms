# Graph Algorithm Visualizer

A JavaFX desktop application for building directed weighted graphs and watching five classic algorithms animate step-by-step.

---

## Requirements

| Tool | Version |
|---|---|
| Java JDK | 21 or newer |
| Maven | bundled via `mvnw` wrapper — no separate install needed |

> **Windows users:** use `.\mvnw.cmd` instead of `mvn` in every command below.  
> **Mac / Linux users:** use `./mvnw` instead of `mvn`.

---

## Running the App

```bash
mvn javafx:run
```

The window opens at 960×640. You can resize it; the canvas grows with the window.

---

## Building Without Running

```bash
mvn compile
```

Compiled `.class` files land in `target/classes/`.

---

## How to Use

### 1 — Build a Graph

| Action | What Happens |
|---|---|
| Click empty canvas space | Creates a new node (labeled A, B, C, …) |
| Click a node, then click another node | Draws a directed edge (arrow) from the first to the second |
| Click the same node twice | Cancels the pending edge |
| Hold and drag a node | Moves it; canvas clamps it to stay visible |
| Right-click a node | Deletes the node and all its edges |
| Right-click an edge | Opens a dialog to edit the edge weight |
| **Create Random Graph** button | Generates a random connected graph (5–8 nodes, weights 1–20) |
| **Clear Graph** button | Removes everything from the canvas |

---

### 2 — Choose an Algorithm

Open the **Algorithm** dropdown and pick one:

| Algorithm | What it Does | Needs a Start Node? |
|---|---|---|
| Kruskal's MST | Finds the minimum spanning tree by adding the cheapest edges that don't form a cycle | No |
| Prim's MST | Finds the minimum spanning tree by growing it outward from a chosen node | Yes |
| Bellman-Ford | Finds shortest paths from a chosen source; detects negative-weight cycles | Yes |
| BFS (Traversal) | Explores the graph in waves (breadth-first) from a chosen node | Yes |
| DFS (Traversal) | Explores one path as deep as possible before backtracking (depth-first) from a chosen node | Yes |

If the algorithm needs a start node, the status bar will say **"click a node to set the starting node"** — click any node to confirm it (it turns amber).

---

### 3 — Run and Watch

Press **Run**. The algorithm runs instantly in the background and the animation begins playing automatically.

#### Playback Controls

| Button | Action |
|---|---|
| ▶ Play | Play the animation continuously |
| ⏸ Pause | Pause at the current step |
| ⏭ Step | Advance one step at a time (while paused) |
| ⟲ Reset | Rewind to the beginning and clear all colors |

#### Color Legend

| Color | Meaning |
|---|---|
| Amber / yellow | The algorithm is currently examining this edge |
| Green | Edge accepted / node discovered — stays green for the rest of the run |
| Red (fades back to gray) | Edge rejected (would form a cycle, or neighbor already visited) |

---

### 4 — Algorithm-Specific Notes

**Kruskal's MST**  
Works on the whole graph. No start node needed. Green edges form the MST; red edges were rejected to avoid cycles.

**Prim's MST**  
Grows the MST outward from your chosen start node. Treats directed edges as undirected internally.

**Bellman-Ford**  
Shows shortest-path relaxations one by one. Distance labels appear above each node. If the graph contains a negative-weight cycle, the cycle edges are highlighted in red at the end.

**BFS / DFS**  
Follows edge arrows only (directed). Nodes discovered during traversal stay green. If some nodes are unreachable from the start node, the status bar will say so when the animation finishes.

---

## Project Structure

```
src/main/java/com/graphviz/
    Main.java                        application entry point
    controller/
        MainController.java          UI events, animation, rendering
    model/
        Graph.java / Node.java / Edge.java
        KruskalSolver.java / PrimSolver.java
        BellmanFordSolver.java
        BfsSolver.java / DfsSolver.java
        UnionFind.java
        RandomGraphGenerator.java
        AlgorithmStep.java / MstStep.java
        BellmanFordStep.java / BellmanFordResult.java
        TraversalStep.java

src/main/resources/com/graphviz/
    MainView.fxml                    layout
    styles.css                       light theme
```

For a deep technical reference (architecture, file-by-file descriptions, controller state, data flow), see [CODEBASE.md](CODEBASE.md).
