# Graph Algorithm Visualizer — Codebase Reference

## Overview

A JavaFX desktop app that lets you build directed weighted graphs and visually animate three classic graph algorithms step-by-step: Kruskal's MST, Prim's MST, and Bellman-Ford shortest paths.

**Stack:** Java 21 · JavaFX 21.0.4 · Maven  
**Run:** `mvn javafx:run`  
**Window:** 960×640 (minimum 820×560)

---

## Architecture

Three-layer MVC. The model layer has zero JavaFX imports, keeping algorithms pure and unit-testable. All visual state lives in the controller, not the model.

```
View    MainView.fxml + styles.css          (layout and theme)
        ↕ (FXML binding)
Ctrl    MainController.java                 (UI events, animation, rendering)
        ↕ (plain Java calls)
Model   Graph / Node / Edge                 (data structures)
        KruskalSolver / PrimSolver /        (algorithm implementations)
        BellmanFordSolver
        UnionFind                           (helper for Kruskal)
        MstStep / BellmanFordStep /         (step records for animation)
        BellmanFordResult
        RandomGraphGenerator               (connected random graphs)
```

---

## Source Files

### Entry Point

**[Main.java](src/main/java/com/graphviz/Main.java)**  
JavaFX `Application` subclass. Loads `MainView.fxml`, attaches `styles.css`, sets the scene to 960×640, and shows the stage.

---

### Model Layer

**[Node.java](src/main/java/com/graphviz/model/Node.java)**  
A graph vertex. Fields: `id: String` (A–Z, AA–ZZ), `x: double`, `y: double` (canvas position, mutable for dragging). Equality is based on ID only.

**[Edge.java](src/main/java/com/graphviz/model/Edge.java)**  
A directed weighted connection. Fields: `source: Node`, `target: Node`, `weight: double`. Negative weights are valid (used by Bellman-Ford). Bidirectional connections require two separate Edge objects.

**[Graph.java](src/main/java/com/graphviz/model/Graph.java)**  
Container for `List<Node>` and `List<Edge>`. Key methods:
- `addNode`, `removeNode`, `addEdge`, `removeEdge`, `clear`
- `getNeighbors(Node)` — edges leaving a given node
- `nextNodeId()` — auto-increments IDs (A → Z → AA → ZZ → null)
- `hasEdgeBetween(a, b)` — either direction
- `hasDirectedEdge(source, target)` — specific direction

**[UnionFind.java](src/main/java/com/graphviz/model/UnionFind.java)**  
Disjoint Set structure used by Kruskal's for cycle detection. Implements path compression on `find()` and union by rank on `union()`. Nearly O(1) amortized per operation.

**[KruskalSolver.java](src/main/java/com/graphviz/model/KruskalSolver.java)**  
`solve(Graph) → List<MstStep>`. Sorts edges by weight, processes with UnionFind. Each step is ACCEPTED (no cycle) or REJECTED (would form cycle). O(E log E + E α(V)).

**[PrimSolver.java](src/main/java/com/graphviz/model/PrimSolver.java)**  
`solve(Graph, Node start) → List<MstStep>`. Min-heap-based MST starting from a chosen node. Treats directed edges as undirected. O(E log V).

**[BellmanFordSolver.java](src/main/java/com/graphviz/model/BellmanFordSolver.java)**  
`solve(Graph, Node source) → BellmanFordResult`. Runs V−1 relaxation sweeps over all edges, then one more to detect negative cycles. `traceCycle()` walks back V steps to land inside the cycle, then follows predecessors to collect the exact cycle edges. O(V·E).

**[RandomGraphGenerator.java](src/main/java/com/graphviz/model/RandomGraphGenerator.java)**  
`generate(canvasWidth, canvasHeight) → Graph`. Produces 5–8 node connected graphs. Places nodes with minimum 80px spacing, then builds a spanning chain (guarantees connectivity), then adds ~nodeCount/2 extra edges. Edge weights are random integers 1–20.

---

### Step / Result Records

**[AlgorithmStep.java](src/main/java/com/graphviz/model/AlgorithmStep.java)**  
Interface with one method: `getEdge()`. Lets the controller handle MST and Bellman-Ford steps through the same playback loop.

**[MstStep.java](src/main/java/com/graphviz/model/MstStep.java)**  
One step in Kruskal's or Prim's. Fields: `edge`, `decision` (ACCEPTED | REJECTED).

**[BellmanFordStep.java](src/main/java/com/graphviz/model/BellmanFordStep.java)**  
One relaxation attempt. Fields: `edge`, `improved: boolean`, `updatedNode: Node`, `newDistance: double`.

**[BellmanFordResult.java](src/main/java/com/graphviz/model/BellmanFordResult.java)**  
Full Bellman-Ford output. Fields: `steps: List<AlgorithmStep>`, `finalDistances: Map<Node, Double>`, `negativeCycle: boolean`, `cycleEdges: List<Edge>`.

---

### Controller

**[MainController.java](src/main/java/com/graphviz/controller/MainController.java)** (~1040 lines)  
Central orchestrator. Responsibilities: graph editing, algorithm dispatch, animation, canvas rendering, mouse interaction.

#### Visual Constants
| Constant | Value | Meaning |
|---|---|---|
| `NODE_RADIUS` | 18.0 | Node circle radius (px) |
| `EDGE_HIT_THRESHOLD` | 6.0 | Right-click tolerance for edges (px) |
| `CURVE_OFFSET` | 18.0 | Bow offset for bidirectional edges |
| `EDGE_NORMAL` | #9aa3b2 | Gray — default |
| `EDGE_EVALUATING` | #e0a800 | Amber — algorithm is checking |
| `EDGE_ACCEPTED` | #2e9e5b | Green — added to MST / distance improved |
| `EDGE_REJECTED` | #e0483b | Red — rejected (then fades to gray) |
| `NODE_FILL` | #dbe9ff | Normal node fill |
| `SOURCE_FILL` | #ffe9a8 | Source node fill (Bellman-Ford / Prim) |

#### Key State
| Field | Purpose |
|---|---|
| `currentGraph: Graph` | Live graph data |
| `selectedAlgorithm: Algorithm` | KRUSKAL, BELLMAN_FORD, or PRIM |
| `steps: List<AlgorithmStep>` | Prepared animation steps |
| `currentStep: int` | Current playback position |
| `playing / stepRequested / runActive` | Volatile flags for animation thread control |
| `edgeStates: Map<Edge, EdgeState>` | Color override per edge |
| `nodeDistances: Map<Node, Double>` | Distance labels (Bellman-Ford only) |
| `fadingEdges: Map<Edge, Double>` | Red→gray fade progress 0–1 |
| `flashingNodes: Set<Node>` | Nodes with green flash |
| `sourceNode: Node` | Chosen start for BF / Prim |
| `awaitingSourcePick: boolean` | Waiting for user to click a start node |

#### Animation Thread
The animation runs on a background thread. All canvas and control updates go through `Platform.runLater()` to stay on the JavaFX Application Thread.

Flow: `onRun()` → `prepareXxx()` → `startAnimationThread()` → loop calling `runOneStep()` → `finishRun()`.

- `runMstStep(MstStep)` — flash amber 500ms, then green (ACCEPTED) or red→fade (REJECTED)
- `runBellmanFordStep(BellmanFordStep)` — flash amber 500ms, update distance label and flash node green if improved
- `startFade(Edge)` — 500ms AnimationTimer interpolating red→gray

#### Mouse Interaction
| Action | Result |
|---|---|
| Left-click empty canvas | Create new node |
| Left-click node (1st) | Enter pending-edge mode (node turns blue) |
| Left-click node (2nd) | Create directed edge from 1st to 2nd |
| Left-click same node twice | Cancel pending edge |
| Left-drag node | Move node (clamped to canvas bounds) |
| Right-click node | Delete node and its edges |
| Right-click edge | Open weight-edit dialog |

#### Drawing
`drawGraph()` clears the canvas, draws all edges (straight or bowed for bidirectional pairs, with arrowheads and weight labels), then draws nodes on top (circles + ID text), then distance labels above nodes if Bellman-Ford is running.

---

### View Layer

**[MainView.fxml](src/main/resources/com/graphviz/MainView.fxml)**  
`BorderPane` root.
- **TOP:** Toolbar with two rows — (1) random/clear buttons, algorithm combo, Run button; (2) Play/Pause/Step/Reset controls and edge-state legend.
- **CENTER:** `Pane` containing a `Canvas` bound to pane size.
- **BOTTOM:** Hint line + status message label.

**[styles.css](src/main/resources/com/graphviz/styles.css)**  
Light theme. Key classes: `.button`, `.button-primary` (blue Run button), `.algorithm-combo`, `.status-label`, `.legend-checking / .legend-accepted / .legend-rejected`.

---

## Data Flow

```
1. User builds graph (random or manual click)
        → Graph stores Nodes + Edges

2. User selects algorithm
        → Controller sets selectedAlgorithm, may set awaitingSourcePick

3. User clicks Run
        → Controller calls Solver (pure Java, no UI)
        → Solver returns List<AlgorithmStep>

4. Animation thread replays steps
        → runOneStep() dispatches by runtime type
        → Controller updates edgeStates / nodeDistances / fadingEdges

5. drawGraph() re-renders canvas on each step
        → reads currentGraph + visual state maps

6. User controls playback
        → volatile flags (playing / stepRequested) communicate to thread
```

---

## Algorithms

| Algorithm | Solver | Complexity | Key Data Structure |
|---|---|---|---|
| Kruskal's MST | `KruskalSolver` | O(E log E + E α(V)) | UnionFind |
| Prim's MST | `PrimSolver` | O(E log V) | PriorityQueue (min-heap) |
| Bellman-Ford | `BellmanFordSolver` | O(V·E) | distance array + predecessor map |

All three solvers work on directed graphs. Prim's treats edges as undirected internally. Bellman-Ford supports negative edge weights and detects + traces negative cycles.
