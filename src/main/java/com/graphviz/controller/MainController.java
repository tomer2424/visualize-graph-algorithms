package com.graphviz.controller;

import com.graphviz.model.AlgorithmStep;
import com.graphviz.model.BellmanFordResult;
import com.graphviz.model.BellmanFordSolver;
import com.graphviz.model.BellmanFordStep;
import com.graphviz.model.BfsSolver;
import com.graphviz.model.DfsSolver;
import com.graphviz.model.Edge;
import com.graphviz.model.Graph;
import com.graphviz.model.KruskalSolver;
import com.graphviz.model.MstStep;
import com.graphviz.model.Node;
import com.graphviz.model.PrimSolver;
import com.graphviz.model.RandomGraphGenerator;
import com.graphviz.model.TraversalStep;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Controller for MainView.fxml.
 *
 * Responsibilities:
 *   1. Build graphs — random generation and manual mouse drawing (directed edges).
 *   2. Run one of five algorithms — Kruskal MST, Prim MST, Bellman-Ford shortest
 *      paths, BFS traversal, or DFS traversal — and animate it step by step.
 *   3. Drive the playback controls (Play / Pause / Step / Reset).
 *
 * Threading rule (important): the animation runs on a background thread so the
 * GUI never freezes. Every canvas redraw or label update from that thread goes
 * through Platform.runLater(), which hands the work to the JavaFX Application
 * Thread — the only thread allowed to touch UI controls.
 *
 * Where the visual state lives: the model classes stay free of JavaFX. The
 * colors of edges and the distance labels on nodes are kept here in the
 * controller (edgeStates, nodeDistances). This keeps the algorithms plain and
 * testable, and works for both algorithms through the shared AlgorithmStep type.
 */
public class MainController {

    // --- Which algorithm is selected ---
    private enum Algorithm { KRUSKAL, BELLMAN_FORD, PRIM, BFS, DFS }

    // --- Visual constants ---
    private static final double NODE_RADIUS        = 18.0;
    private static final double EDGE_HIT_THRESHOLD = 6.0;
    private static final double ARROW_LENGTH       = 12.0; // length of arrowhead lines
    private static final double ARROW_WIDTH        = 6.0;  // half-width of arrowhead
    private static final double CURVE_OFFSET       = 18.0; // how far opposing edges bow apart

    // Canvas + node palette — tuned to match the modern light theme (styles.css).
    private static final Color CANVAS_BG    = Color.web("#fdfdfe");
    private static final Color NODE_FILL    = Color.web("#dbe9ff");
    private static final Color NODE_STROKE  = Color.web("#2b3a55");
    private static final Color SOURCE_FILL  = Color.web("#ffe9a8"); // highlight for BF source node
    private static final Color WEIGHT_COLOR = Color.web("#d4493d");
    private static final Color DRAG_COLOR   = Color.web("#3478f6"); // accent blue (matches CSS)
    private static final Color DIST_COLOR   = Color.web("#1a5276"); // distance label color

    // Edge state colors — same hex values as the legend dots in styles.css.
    private static final Color EDGE_NORMAL     = Color.web("#9aa3b2"); // cool gray
    private static final Color EDGE_EVALUATING = Color.web("#e0a800"); // amber
    private static final Color EDGE_ACCEPTED   = Color.web("#2e9e5b"); // green
    private static final Color EDGE_REJECTED   = Color.web("#e0483b"); // red

    private static final Font NODE_FONT   = Font.font("Segoe UI", 13);
    private static final Font WEIGHT_FONT = Font.font("Segoe UI", 11);
    private static final Font DIST_FONT   = Font.font("Segoe UI", 11);

    // --- Animation timing (milliseconds) ---
    private static final long EVALUATING_PAUSE    = 500;
    private static final long BETWEEN_STEPS_PAUSE = 350;
    private static final long FADE_DURATION_MS    = 500;

    /** The visual state of a single edge while the animation runs. */
    private enum EdgeState { NORMAL, EVALUATING, ACCEPTED, REJECTED }

    // --- FXML fields ---
    @FXML private Pane              canvasHolder;
    @FXML private Canvas            graphCanvas;
    @FXML private Label             statusLabel;
    @FXML private Button            runButton;
    @FXML private ComboBox<String>  algorithmCombo;
    @FXML private HBox              playbackBar;
    @FXML private Button playButton;
    @FXML private Button pauseButton;
    @FXML private Button stepButton;
    @FXML private Button resetButton;

    // --- Model + helpers ---
    private final RandomGraphGenerator generator = new RandomGraphGenerator();
    private final KruskalSolver kruskalSolver = new KruskalSolver();
    private final BellmanFordSolver bellmanFordSolver = new BellmanFordSolver();
    private final PrimSolver primSolver = new PrimSolver();
    private final BfsSolver bfsSolver = new BfsSolver();
    private final DfsSolver dfsSolver = new DfsSolver();
    private Graph currentGraph;

    // --- Algorithm selection + Bellman-Ford source ---
    private Algorithm selectedAlgorithm = null; // null until the user picks from the combo
    private Node sourceNode;              // chosen source for Bellman-Ford
    private boolean awaitingSourcePick;   // true while waiting for the user to click a source
    private boolean runSessionActive;     // true while the playback bar is showing (run mode)

    // --- Mouse interaction state ---
    private Node pressedNode;       // node under the mouse on press
    private double pressX, pressY;
    private boolean isDraggingNode; // true once the drag exceeds the deadzone
    private Node pendingEdgeSource; // node waiting for the user to click a target

    // --- Color/label state for drawing ---
    private final Map<Edge, EdgeState> edgeStates = new HashMap<>();
    private final Map<Edge, Double> fadingEdges = new HashMap<>(); // red→gray fade progress (0..1)
    private final Map<Node, Double> nodeDistances = new HashMap<>(); // BF distances shown on nodes
    private final Set<Node> flashingNodes = new HashSet<>();         // nodes briefly flashed green
    private final Set<Node> visitedNodes = new HashSet<>();          // traversal: discovered nodes stay green

    // --- Playback state (shared by both algorithms) ---
    private List<AlgorithmStep> steps;
    private int currentStep;
    private volatile boolean playing;
    private volatile boolean stepRequested;
    private volatile boolean runActive;
    private Thread animationThread;
    private int mstTotalWeight;           // running total for Kruskal
    private BellmanFordResult bfResult;   // kept so we can show the negative cycle at the end

    // -------------------------------------------------------------------------
    // JavaFX lifecycle
    // -------------------------------------------------------------------------

    @FXML
    public void initialize() {
        currentGraph = new Graph();

        algorithmCombo.getItems().addAll(LABEL_NONE, LABEL_KRUSKAL, LABEL_BELLMAN_FORD, LABEL_PRIM, LABEL_BFS, LABEL_DFS);
        // Show placeholder text when nothing is selected (JavaFX doesn't support promptText on non-editable combos).
        algorithmCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Choose an algorithm" : item);
            }
        });

        // Make the canvas fill its holder and follow the window as it resizes.
        graphCanvas.widthProperty().bind(canvasHolder.widthProperty());
        graphCanvas.heightProperty().bind(canvasHolder.heightProperty());
        // Repaint whenever the size changes so the graph never goes blank.
        graphCanvas.widthProperty().addListener((obs, oldV, newV) -> drawGraph());
        graphCanvas.heightProperty().addListener((obs, oldV, newV) -> drawGraph());

        graphCanvas.setOnMousePressed(this::onCanvasPressed);
        graphCanvas.setOnMouseDragged(this::onCanvasDragged);
        graphCanvas.setOnMouseReleased(this::onCanvasReleased);

        drawGraph();
        statusLabel.setText("Click empty space = new node · Click node then another = directed edge · Right-click edge = edit weight.");
    }

    // -------------------------------------------------------------------------
    // Graph building + algorithm selection
    // -------------------------------------------------------------------------

    @FXML
    private void onCreateRandomGraph() {
        // Before the first layout pass the canvas can be 0×0; fall back to a
        // reasonable default so generated nodes still fit on screen.
        double w = graphCanvas.getWidth()  > 0 ? graphCanvas.getWidth()  : 760;
        double h = graphCanvas.getHeight() > 0 ? graphCanvas.getHeight() : 480;
        stopAnyRun();
        currentGraph = generator.generate(w, h);
        sourceNode = null;
        pendingEdgeSource = null;
        clearColorsAndDistances();
        exitRunMode();
        // Every algorithm except Kruskal needs the user to pick a start node.
        if (algorithmNeedsStartNode()) {
            awaitingSourcePick = true;
        }
        drawGraph();
        statusLabel.setText("Random graph created: "
                + currentGraph.getNodes().size() + " nodes, "
                + currentGraph.getEdges().size() + " edges. "
                + (awaitingSourcePick ? "Click a node to set the source." : "Press Run to begin."));
    }

    /** Called when the user clicks one of the algorithm toggle buttons. */
    @FXML
    private void onAlgorithmChanged() {
        String selected = algorithmCombo.getValue();
        if (selected == null) return;

        stopAnyRun();
        steps = null;
        clearColorsAndDistances();

        // "Choose an algorithm" selected — reset to no-selection state.
        if (LABEL_NONE.equals(selected)) {
            selectedAlgorithm = null; // must be set before exitRunMode() so Run stays disabled
            sourceNode = null;
            awaitingSourcePick = false;
            exitRunMode();
            drawGraph();
            statusLabel.setText("Choose an algorithm from the list to get started.");
            return;
        }

        exitRunMode();

        runButton.setDisable(false); // a real algorithm is now chosen — Run is valid
        if (LABEL_BELLMAN_FORD.equals(selected)) {
            selectedAlgorithm = Algorithm.BELLMAN_FORD;
        } else if (LABEL_PRIM.equals(selected)) {
            selectedAlgorithm = Algorithm.PRIM;
        } else if (LABEL_BFS.equals(selected)) {
            selectedAlgorithm = Algorithm.BFS;
        } else if (LABEL_DFS.equals(selected)) {
            selectedAlgorithm = Algorithm.DFS;
        } else {
            selectedAlgorithm = Algorithm.KRUSKAL;
        }

        switch (selectedAlgorithm) {
            case BELLMAN_FORD -> {
                sourceNode = null;
                awaitingSourcePick = true;
                statusLabel.setText("Bellman-Ford selected — click a node to set the source for shortest paths.");
            }
            case PRIM -> {
                sourceNode = null;
                awaitingSourcePick = true;
                statusLabel.setText("Prim (MST) selected — click a node to set the starting node.");
            }
            case BFS -> {
                sourceNode = null;
                awaitingSourcePick = true;
                statusLabel.setText("BFS selected — click a node to set the starting node for the traversal.");
            }
            case DFS -> {
                sourceNode = null;
                awaitingSourcePick = true;
                statusLabel.setText("DFS selected — click a node to set the starting node for the traversal.");
            }
            default -> {
                awaitingSourcePick = false;
                sourceNode = null;
                statusLabel.setText("Kruskal (MST) selected — press Run to find the minimum spanning tree.");
            }
        }
        drawGraph();
    }

    // Labels shown in the algorithm drop-down, matching Algorithm enum order.
    private static final String LABEL_NONE          = "Choose an algorithm";
    private static final String LABEL_KRUSKAL      = "Kruskal (MST)";
    private static final String LABEL_BELLMAN_FORD = "Bellman-Ford (Shortest Path)";
    private static final String LABEL_PRIM         = "Prim (MST)";
    private static final String LABEL_BFS          = "BFS (Traversal)";
    private static final String LABEL_DFS          = "DFS (Traversal)";

    // -------------------------------------------------------------------------
    // Run + playback controls
    // -------------------------------------------------------------------------

    /** Prepares a run for whichever algorithm is selected, or cancels if already running. */
    @FXML
    private void onRun() {
        if (runSessionActive) {
            cancelRun();
            return;
        }

        if (currentGraph.getNodes().isEmpty() || currentGraph.getEdges().isEmpty()) {
            statusLabel.setText("Please build a graph with at least one edge first.");
            return;
        }

        switch (selectedAlgorithm) {
            case KRUSKAL      -> prepareKruskal();
            case BELLMAN_FORD -> prepareBellmanFord();
            case PRIM         -> preparePrim();
            case BFS, DFS     -> prepareTraversal();
        }
    }

    private void prepareKruskal() {
        stopAnyRun();
        steps = new ArrayList<>(kruskalSolver.solve(currentGraph)); // List<MstStep> → List<AlgorithmStep>
        bfResult = null;
        currentStep = 0;
        mstTotalWeight = 0;
        clearColorsAndDistances();
        drawGraph();
        enterRunMode();
        startAnimationThread();
        statusLabel.setText("Kruskal ready. Press Play to run, or Step to advance one edge at a time.");
    }

    private void prepareBellmanFord() {
        if (sourceNode == null) {
            awaitingSourcePick = true;
            statusLabel.setText("Please click a node first to set the Bellman-Ford source.");
            return;
        }
        stopAnyRun();
        bfResult = bellmanFordSolver.solve(currentGraph, sourceNode);
        steps = bfResult.getSteps();
        currentStep = 0;
        clearColorsAndDistances();

        // Show the starting distances: source = 0, everyone else = infinity.
        for (Node n : currentGraph.getNodes()) {
            nodeDistances.put(n, Double.POSITIVE_INFINITY);
        }
        nodeDistances.put(sourceNode, 0.0);

        drawGraph();
        enterRunMode();
        startAnimationThread();
        statusLabel.setText("Bellman-Ford ready from source '" + sourceNode.getId()
                + "'. Press Play, or Step to advance one relaxation at a time.");
    }

    private void preparePrim() {
        if (sourceNode == null) {
            awaitingSourcePick = true;
            statusLabel.setText("Please click a node first to set the Prim starting node.");
            return;
        }
        stopAnyRun();
        steps = new ArrayList<>(primSolver.solve(currentGraph, sourceNode));
        bfResult = null;
        currentStep = 0;
        mstTotalWeight = 0;
        clearColorsAndDistances();
        drawGraph();
        enterRunMode();
        startAnimationThread();
        statusLabel.setText("Prim ready from '" + sourceNode.getId()
                + "'. Press Play, or Step to advance one edge at a time.");
    }

    private void prepareTraversal() {
        if (sourceNode == null) {
            awaitingSourcePick = true;
            statusLabel.setText("Please click a node first to set the traversal starting node.");
            return;
        }
        stopAnyRun();
        boolean isBfs = selectedAlgorithm == Algorithm.BFS;
        steps = new ArrayList<>(isBfs
                ? bfsSolver.solve(currentGraph, sourceNode)
                : dfsSolver.solve(currentGraph, sourceNode));
        bfResult = null;
        currentStep = 0;
        clearColorsAndDistances();
        // The start node is visited from step zero — it will draw amber (sourceNode fill
        // wins over visitedNodes in the draw priority), but counts toward the visited total.
        visitedNodes.add(sourceNode);
        drawGraph();
        enterRunMode();
        startAnimationThread();
        statusLabel.setText((isBfs ? "BFS" : "DFS") + " ready from '" + sourceNode.getId()
                + "'. Press Play, or Step to explore one edge at a time.");
    }

    @FXML
    private void onPlay() {
        if (steps == null) return;
        playing = true;
        statusLabel.setText("Playing...");
    }

    @FXML
    private void onPause() {
        playing = false;
        statusLabel.setText("Paused at step " + currentStep + " of " + (steps == null ? 0 : steps.size()) + ".");
    }

    @FXML
    private void onStep() {
        if (steps == null) return;
        playing = false;
        stepRequested = true;
    }

    /** Resets the animation WITHOUT deleting the graph, so it can be replayed. */
    @FXML
    private void onReset() {
        stopAnyRun();
        currentStep = 0;
        mstTotalWeight = 0;
        clearColorsAndDistances();

        // For Bellman-Ford, restore the initial distance labels after a reset.
        if (selectedAlgorithm == Algorithm.BELLMAN_FORD && sourceNode != null) {
            for (Node n : currentGraph.getNodes()) {
                nodeDistances.put(n, Double.POSITIVE_INFINITY);
            }
            nodeDistances.put(sourceNode, 0.0);
        }
        drawGraph();

        if (steps != null && !steps.isEmpty()) {
            enterRunMode();
            startAnimationThread();
            statusLabel.setText("Reset. The graph is unchanged — press Play or Step to run again.");
        } else {
            exitRunMode();
            statusLabel.setText("Reset.");
        }
    }

    // -------------------------------------------------------------------------
    // The playback engine (background thread) — shared by both algorithms
    // -------------------------------------------------------------------------

    private void startAnimationThread() {
        runActive = true;
        playing = false;
        stepRequested = false;

        animationThread = new Thread(() -> {
            try {
                while (runActive && currentStep < steps.size()) {
                    while (runActive && !playing && !stepRequested) {
                        Thread.sleep(20);
                    }
                    if (!runActive) {
                        return;
                    }

                    boolean wasSingleStep = stepRequested;
                    stepRequested = false;

                    runOneStep();

                    if (wasSingleStep) {
                        playing = false;
                    } else {
                        Thread.sleep(BETWEEN_STEPS_PAUSE);
                    }
                }

                if (runActive && currentStep >= steps.size()) {
                    Platform.runLater(this::finishRun);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        animationThread.setDaemon(true);
        animationThread.start();
    }

    /** Runs exactly one step, choosing the visual effect by the step's type. */
    private void runOneStep() throws InterruptedException {
        AlgorithmStep step = steps.get(currentStep);

        if (step instanceof MstStep mstStep) {
            runMstStep(mstStep);
        } else if (step instanceof BellmanFordStep bfStep) {
            runBellmanFordStep(bfStep);
        } else if (step instanceof TraversalStep traversalStep) {
            runTraversalStep(traversalStep);
        }

        currentStep++;
    }

    /** Kruskal step: yellow → green (accept) or red-fade (reject). */
    private void runMstStep(MstStep step) throws InterruptedException {
        Edge edge = step.getEdge();

        edgeStates.put(edge, EdgeState.EVALUATING);
        final int stepNumber = currentStep + 1;
        Platform.runLater(() -> {
            drawGraph();
            statusLabel.setText("Step " + stepNumber + "/" + steps.size()
                    + ": checking edge " + edge.getSource().getId() + "–" + edge.getTarget().getId()
                    + " (weight " + (int) edge.getWeight() + ")");
        });
        Thread.sleep(EVALUATING_PAUSE);

        if (step.getDecision() == MstStep.Decision.ACCEPTED) {
            edgeStates.put(edge, EdgeState.ACCEPTED);
            mstTotalWeight += (int) edge.getWeight();
            Platform.runLater(this::drawGraph);
        } else {
            edgeStates.put(edge, EdgeState.REJECTED);
            Platform.runLater(this::drawGraph);
            startFade(edge);
        }
    }

    /** Bellman-Ford step: yellow while relaxing; if it improved, flash edge+node green. */
    private void runBellmanFordStep(BellmanFordStep step) throws InterruptedException {
        Edge edge = step.getEdge();

        edgeStates.put(edge, EdgeState.EVALUATING);
        final int stepNumber = currentStep + 1;
        Platform.runLater(() -> {
            drawGraph();
            statusLabel.setText("Step " + stepNumber + "/" + steps.size()
                    + ": relaxing edge " + edge.getSource().getId() + "→" + edge.getTarget().getId()
                    + " (weight " + (int) edge.getWeight() + ")");
        });
        Thread.sleep(EVALUATING_PAUSE);

        if (step.isImproved()) {
            // Update the distance label and flash the edge + target node green.
            Node updated = step.getUpdatedNode();
            double newDist = step.getNewDistance();
            nodeDistances.put(updated, newDist);
            edgeStates.put(edge, EdgeState.ACCEPTED);
            flashingNodes.add(updated);
            Platform.runLater(this::drawGraph);
            Thread.sleep(EVALUATING_PAUSE);

            // Settle back: edge to normal, node stops flashing (label stays).
            flashingNodes.remove(updated);
            edgeStates.put(edge, EdgeState.NORMAL);
            Platform.runLater(this::drawGraph);
        } else {
            // No improvement — just clear the yellow highlight.
            edgeStates.put(edge, EdgeState.NORMAL);
            Platform.runLater(this::drawGraph);
        }
    }

    /**
     * BFS/DFS step: yellow while examining; if a new node is discovered the edge
     * and node turn green and stay green; if the target was already visited the
     * edge goes red and fades back to gray.
     */
    private void runTraversalStep(TraversalStep step) throws InterruptedException {
        Edge edge = step.getEdge();

        edgeStates.put(edge, EdgeState.EVALUATING);
        final int stepNumber = currentStep + 1;
        Platform.runLater(() -> {
            drawGraph();
            statusLabel.setText("Step " + stepNumber + "/" + steps.size()
                    + ": exploring edge " + edge.getSource().getId() + "→" + edge.getTarget().getId()
                    + " (weight " + (int) edge.getWeight() + ")");
        });
        Thread.sleep(EVALUATING_PAUSE);

        if (step.isDiscovered()) {
            // New node found — edge stays green and the discovered node joins the visited set.
            edgeStates.put(edge, EdgeState.ACCEPTED);
            visitedNodes.add(step.getDiscoveredNode());
            final Node found = step.getDiscoveredNode();
            Platform.runLater(() -> {
                drawGraph();
                statusLabel.setText("Step " + stepNumber + "/" + steps.size()
                        + ": discovered node '" + found.getId() + "'.");
            });
        } else {
            // Target already visited — flash red then fade back to normal.
            edgeStates.put(edge, EdgeState.REJECTED);
            final Node already = edge.getTarget();
            Platform.runLater(() -> {
                drawGraph();
                statusLabel.setText("Step " + stepNumber + "/" + steps.size()
                        + ": '" + already.getId() + "' was already visited.");
            });
            startFade(edge);
        }
    }

    /** Called on the JavaFX thread when the step list is finished. */
    private void finishRun() {
        playing = false;
        playButton.setDisable(true);
        stepButton.setDisable(true);

        switch (selectedAlgorithm) {
            case KRUSKAL -> statusLabel.setText("Kruskal complete — MST weight = " + mstTotalWeight
                    + ". Press Reset to run again on the same graph.");
            case PRIM -> {
                // Count nodes reached by accepted edges plus the start node itself.
                Set<Node> reached = new java.util.HashSet<>();
                if (sourceNode != null) reached.add(sourceNode);
                for (AlgorithmStep s : steps) {
                    if (s instanceof MstStep ms && ms.getDecision() == MstStep.Decision.ACCEPTED) {
                        reached.add(ms.getEdge().getSource());
                        reached.add(ms.getEdge().getTarget());
                    }
                }
                int total = currentGraph.getNodes().size();
                int unreachable = total - reached.size();
                String msg = "Prim complete — MST weight = " + mstTotalWeight + ".";
                if (unreachable > 0) {
                    msg += " ⚠ " + unreachable + " node(s) unreachable from '"
                            + (sourceNode != null ? sourceNode.getId() : "?")
                            + "' (graph is disconnected).";
                } else {
                    msg += " Press Reset to run again on the same graph.";
                }
                statusLabel.setText(msg);
            }
            case BFS, DFS -> {
                // Count visited nodes from the step list (start node was added before steps ran).
                int visited = (int) steps.stream()
                        .filter(s -> s instanceof TraversalStep ts && ts.isDiscovered())
                        .count() + 1; // +1 for the start node
                int total = currentGraph.getNodes().size();
                int unreachable = total - visited;
                String algoName = selectedAlgorithm == Algorithm.BFS ? "BFS" : "DFS";
                String msg = algoName + " complete — visited " + visited + " of " + total + " node(s).";
                if (unreachable > 0) {
                    msg += " ⚠ " + unreachable + " node(s) unreachable from '"
                            + (sourceNode != null ? sourceNode.getId() : "?")
                            + "' — no directed path leads to them.";
                } else {
                    msg += " Press Reset to run again on the same graph.";
                }
                statusLabel.setText(msg);
            }
            default -> {
                if (bfResult != null && bfResult.hasNegativeCycle()) {
                    for (Edge e : bfResult.getCycleEdges()) {
                        edgeStates.put(e, EdgeState.REJECTED);
                    }
                    statusLabel.setText("⚠ Negative-weight cycle detected — shortest paths are undefined. "
                            + "The cycle edges are shown in red.");
                } else {
                    statusLabel.setText("Bellman-Ford complete — shortest distances from '"
                            + (sourceNode != null ? sourceNode.getId() : "?")
                            + "' are shown on the nodes. Press Reset to run again.");
                }
            }
        }
        drawGraph();
    }

    /** Smoothly fades a rejected edge from red back to gray (used by Kruskal). */
    private void startFade(Edge edge) {
        Platform.runLater(() -> {
            fadingEdges.put(edge, 0.0);
            final long startTime = System.nanoTime();
            new AnimationTimer() {
                @Override
                public void handle(long now) {
                    double elapsedMs = (now - startTime) / 1_000_000.0;
                    double progress = Math.min(1.0, elapsedMs / FADE_DURATION_MS);
                    fadingEdges.put(edge, progress);
                    if (progress >= 1.0) {
                        fadingEdges.remove(edge);
                        edgeStates.put(edge, EdgeState.NORMAL);
                        stop();
                    }
                    drawGraph();
                }
            }.start();
        });
    }

    private void stopAnyRun() {
        runActive = false;
        playing = false;
        stepRequested = false;
        if (animationThread != null) {
            animationThread.interrupt();
            animationThread = null;
        }
        fadingEdges.clear();
        flashingNodes.clear();
    }

    private void clearColorsAndDistances() {
        edgeStates.clear();
        fadingEdges.clear();
        flashingNodes.clear();
        nodeDistances.clear();
        visitedNodes.clear();
    }

    private void enterRunMode() {
        playButton.setDisable(false);
        pauseButton.setDisable(false);
        stepButton.setDisable(false);
        resetButton.setDisable(false);
        playbackBar.setVisible(true);
        playbackBar.setManaged(true);
        runButton.setText("Cancel");
        algorithmCombo.setDisable(true);
        runSessionActive = true;
    }

    private void exitRunMode() {
        playButton.setDisable(true);
        pauseButton.setDisable(true);
        stepButton.setDisable(true);
        resetButton.setDisable(true);
        playbackBar.setVisible(false);
        playbackBar.setManaged(false);
        runButton.setText("Run");
        algorithmCombo.setDisable(false);
        runButton.setDisable(selectedAlgorithm == null);
        runSessionActive = false;
    }

    private void cancelRun() {
        stopAnyRun();
        steps = null;
        bfResult = null;
        clearColorsAndDistances();
        exitRunMode();
        drawGraph();
        statusLabel.setText("Run cancelled — the graph is unchanged.");
    }

    // -------------------------------------------------------------------------
    // Mouse handlers (manual graph drawing + source picking)
    // -------------------------------------------------------------------------

    private void onCanvasPressed(MouseEvent e) {
        pressX = e.getX();
        pressY = e.getY();
        isDraggingNode = false;

        if (e.getButton() == MouseButton.SECONDARY) {
            pendingEdgeSource = null;
            Node node = findNodeAt(pressX, pressY);
            if (node != null) {
                deleteNode(node);
                return;
            }
            Edge edge = findEdgeNear(pressX, pressY);
            if (edge != null) {
                showEdgeContextMenu(edge, e.getScreenX(), e.getScreenY());
            }
            return;
        }

        if (e.getButton() != MouseButton.PRIMARY) return;

        // Bellman-Ford source picking overrides normal click behaviour.
        if (awaitingSourcePick) {
            Node picked = findNodeAt(pressX, pressY);
            if (picked != null) {
                setSource(picked);
            } else {
                statusLabel.setText("Click directly on a node to set the source.");
            }
            return;
        }

        pressedNode = findNodeAt(pressX, pressY);
    }

    private void onCanvasDragged(MouseEvent e) {
        if (pressedNode == null || awaitingSourcePick) return;

        double dx = e.getX() - pressX;
        double dy = e.getY() - pressY;
        // Enter move mode once the drag exceeds a small deadzone.
        if (!isDraggingNode && Math.hypot(dx, dy) > NODE_RADIUS / 2) {
            isDraggingNode = true;
            // Cancel any pending edge source — the user is clearly moving, not connecting.
            pendingEdgeSource = null;
        }

        if (isDraggingNode) {
            pressedNode.setX(e.getX());
            pressedNode.setY(e.getY());
            drawGraph();
        }
    }

    private void onCanvasReleased(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || awaitingSourcePick) {
            pressedNode = null;
            isDraggingNode = false;
            return;
        }

        double releaseX = e.getX();
        double releaseY = e.getY();

        if (isDraggingNode && pressedNode != null) {
            // Clamp to canvas bounds so a node cannot be dragged off-screen.
            double clampedX = Math.max(NODE_RADIUS, Math.min(graphCanvas.getWidth()  - NODE_RADIUS, releaseX));
            double clampedY = Math.max(NODE_RADIUS, Math.min(graphCanvas.getHeight() - NODE_RADIUS, releaseY));
            pressedNode.setX(clampedX);
            pressedNode.setY(clampedY);
            cancelRunBecauseGraphChanged();
            drawGraph();
            statusLabel.setText("Node '" + pressedNode.getId() + "' moved.");
        } else if (pressedNode != null) {
            // This was a click (no meaningful drag) on a node.
            if (pendingEdgeSource != null && !pendingEdgeSource.equals(pressedNode)) {
                // Second click on a different node — connect them.
                tryAddEdge(pendingEdgeSource, pressedNode);
                pendingEdgeSource = null;
            } else if (pendingEdgeSource != null && pendingEdgeSource.equals(pressedNode)) {
                // Clicked the same node again — cancel.
                pendingEdgeSource = null;
                drawGraph();
                statusLabel.setText("Edge cancelled.");
            } else {
                // First click on a node — enter pending mode.
                pendingEdgeSource = pressedNode;
                drawGraph();
                statusLabel.setText("Node '" + pressedNode.getId() + "' selected — click another node to connect, or click here again to cancel.");
            }
        } else {
            // Click on empty space.
            if (pendingEdgeSource != null) {
                // Cancel a pending edge.
                pendingEdgeSource = null;
                drawGraph();
                statusLabel.setText("Edge cancelled.");
            } else {
                tryAddNode(releaseX, releaseY);
            }
        }

        pressedNode = null;
        isDraggingNode = false;
    }

    /** Every algorithm except Kruskal requires the user to pick a starting node. */
    private boolean algorithmNeedsStartNode() {
        return selectedAlgorithm != null && selectedAlgorithm != Algorithm.KRUSKAL;
    }

    /** Sets the source/start node for Bellman-Ford, Prim, BFS, or DFS. */
    private void setSource(Node node) {
        sourceNode = node;
        awaitingSourcePick = false;
        clearColorsAndDistances();

        // Bellman-Ford shows ∞ distance labels from the start; other algorithms just highlight.
        if (selectedAlgorithm == Algorithm.BELLMAN_FORD) {
            for (Node n : currentGraph.getNodes()) {
                nodeDistances.put(n, Double.POSITIVE_INFINITY);
            }
            nodeDistances.put(sourceNode, 0.0);
            statusLabel.setText("Source set to '" + node.getId() + "'. Press Run to compute shortest paths.");
        } else if (selectedAlgorithm == Algorithm.BFS || selectedAlgorithm == Algorithm.DFS) {
            statusLabel.setText("Start node set to '" + node.getId() + "'. Press Run to begin the traversal.");
        } else {
            statusLabel.setText("Start node set to '" + node.getId() + "'. Press Run to build the MST.");
        }
        drawGraph();
    }

    /** Editing the graph cancels any prepared run so the steps cannot go stale. */
    private void cancelRunBecauseGraphChanged() {
        pendingEdgeSource = null;
        if (runActive || steps != null) {
            stopAnyRun();
            steps = null;
            bfResult = null;
            edgeStates.clear();
            fadingEdges.clear();
            flashingNodes.clear();
            visitedNodes.clear();
            exitRunMode();
        }
    }

    @FXML
    private void onClearGraph() {
        stopAnyRun();
        currentGraph.clear();
        sourceNode = null;
        awaitingSourcePick = false;
        pendingEdgeSource = null;
        clearColorsAndDistances();
        exitRunMode();
        steps = null;
        bfResult = null;
        drawGraph();
        statusLabel.setText("Graph cleared. Click empty space to start building a new graph.");
    }

    private void deleteNode(Node node) {
        cancelRunBecauseGraphChanged();
        // Remove node distances and edge states for any edges touching this node.
        for (Edge e : currentGraph.getEdges()) {
            if (e.getSource().equals(node) || e.getTarget().equals(node)) {
                edgeStates.remove(e);
                fadingEdges.remove(e);
            }
        }
        if (node.equals(sourceNode)) {
            sourceNode = null;
            if (algorithmNeedsStartNode()) {
                awaitingSourcePick = true;
            }
        }
        nodeDistances.remove(node);
        flashingNodes.remove(node);
        visitedNodes.remove(node);
        currentGraph.removeNode(node);
        drawGraph();
        statusLabel.setText("Node '" + node.getId() + "' deleted.");
    }

    private void tryAddNode(double x, double y) {
        if (findNodeAt(x, y) != null) {
            statusLabel.setText("Cannot place a node here — too close to an existing node.");
            return;
        }
        String id = currentGraph.nextNodeId();
        if (id == null) {
            statusLabel.setText("Maximum of 52 nodes reached (A–Z and AA–ZZ).");
            return;
        }
        cancelRunBecauseGraphChanged();
        currentGraph.addNode(new Node(id, x, y));
        drawGraph();
        statusLabel.setText("Node '" + id + "' added. Total: " + currentGraph.getNodes().size() + " nodes.");
    }

    private void tryAddEdge(Node source, Node target) {
        // Directed: block only an edge already going this same direction.
        if (currentGraph.hasDirectedEdge(source, target)) {
            statusLabel.setText("A directed edge '" + source.getId() + "→" + target.getId()
                    + "' already exists.");
            drawGraph();
            return;
        }
        cancelRunBecauseGraphChanged();
        currentGraph.addEdge(source, target, 1);
        drawGraph();
        statusLabel.setText("Directed edge '" + source.getId() + "→" + target.getId()
                + "' added with weight 1. Right-click it to change the weight (negatives allowed).");
    }

    /** Context menu shown when the user right-clicks an edge — edit weight or delete. */
    private void showEdgeContextMenu(Edge edge, double screenX, double screenY) {
        MenuItem editItem = new MenuItem("Edit weight…");
        editItem.setOnAction(e -> editEdgeWeight(edge));

        MenuItem deleteItem = new MenuItem("Delete edge");
        deleteItem.setOnAction(e -> {
            cancelRunBecauseGraphChanged();
            currentGraph.removeEdge(edge);
            drawGraph();
            statusLabel.setText("Edge " + edge.getSource().getId() + " → " + edge.getTarget().getId() + " deleted.");
        });

        ContextMenu menu = new ContextMenu(editItem, deleteItem);
        menu.show(graphCanvas, screenX, screenY);
    }

    /** Edit dialog now accepts negative integers (Bellman-Ford allows them). */
    private void editEdgeWeight(Edge edge) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf((int) edge.getWeight()));
        dialog.setTitle("Edit Edge Weight");
        dialog.setHeaderText("Edge: " + edge.getSource().getId() + " → " + edge.getTarget().getId());
        dialog.setContentText("New weight (any integer, negatives allowed):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            try {
                int newWeight = Integer.parseInt(input.trim());
                cancelRunBecauseGraphChanged();
                edge.setWeight(newWeight);
                drawGraph();
                statusLabel.setText("Weight updated to " + newWeight + ".");
            } catch (NumberFormatException ex) {
                statusLabel.setText("'" + input + "' is not a valid integer — weight not changed.");
            }
        });
    }

    // -------------------------------------------------------------------------
    // Hit-testing helpers
    // -------------------------------------------------------------------------

    private Node findNodeAt(double x, double y) {
        List<Node> nodes = currentGraph.getNodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            double dx = n.getX() - x;
            double dy = n.getY() - y;
            if (Math.sqrt(dx * dx + dy * dy) <= NODE_RADIUS) {
                return n;
            }
        }
        return null;
    }

    private Edge findEdgeNear(double px, double py) {
        for (Edge edge : currentGraph.getEdges()) {
            double x1 = edge.getSource().getX();
            double y1 = edge.getSource().getY();
            double x2 = edge.getTarget().getX();
            double y2 = edge.getTarget().getY();

            double segLenSq = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
            double dist;

            if (segLenSq == 0) {
                double dx = px - x1;
                double dy = py - y1;
                dist = Math.sqrt(dx * dx + dy * dy);
            } else {
                double t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / segLenSq;
                t = Math.max(0, Math.min(1, t));
                double closestX = x1 + t * (x2 - x1);
                double closestY = y1 + t * (y2 - y1);
                double dx = px - closestX;
                double dy = py - closestY;
                dist = Math.sqrt(dx * dx + dy * dy);
            }

            if (dist <= EDGE_HIT_THRESHOLD) {
                return edge;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    private void drawGraph() {
        GraphicsContext gc = graphCanvas.getGraphicsContext2D();

        gc.clearRect(0, 0, graphCanvas.getWidth(), graphCanvas.getHeight());
        gc.setFill(CANVAS_BG);
        gc.fillRect(0, 0, graphCanvas.getWidth(), graphCanvas.getHeight());

        // --- Step 1: directed edges with arrowheads ---
        gc.setFont(WEIGHT_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        for (Edge edge : currentGraph.getEdges()) {
            drawDirectedEdge(gc, edge);
        }

        // --- Step 2: nodes on top, with id and (for BF) distance label ---
        gc.setFont(NODE_FONT);
        for (Node node : currentGraph.getNodes()) {
            double cx = node.getX();
            double cy = node.getY();
            double r  = NODE_RADIUS;

            if (flashingNodes.contains(node)) {
                gc.setFill(EDGE_ACCEPTED);
            } else if (node.equals(sourceNode)) {
                gc.setFill(SOURCE_FILL);
            } else if (visitedNodes.contains(node)) {
                gc.setFill(EDGE_ACCEPTED); // discovered by the traversal — stays green
            } else if (node.equals(pendingEdgeSource)) {
                gc.setFill(DRAG_COLOR); // blue highlight while waiting for the second click
            } else {
                gc.setFill(NODE_FILL);
            }
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);

            gc.setStroke(NODE_STROKE);
            gc.setLineWidth(2.0);
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);

            gc.setFill(NODE_STROKE);
            gc.fillText(node.getId(), cx, cy);

            // Distance label (only when Bellman-Ford distances exist).
            Double dist = nodeDistances.get(node);
            if (dist != null) {
                String text = (dist == Double.POSITIVE_INFINITY) ? "∞" : String.valueOf((int) (double) dist);
                gc.setFont(DIST_FONT);
                gc.setFill(DIST_COLOR);
                gc.fillText(text, cx, cy - r - 9); // just above the circle
                gc.setFont(NODE_FONT);
            }
        }
    }

    /**
     * Draws one directed edge with an arrowhead at the target circle's edge.
     * If the opposite edge (target → source) also exists, both are drawn as
     * slight curves bowed to opposite sides so the two arrows do not overlap.
     */
    private void drawDirectedEdge(GraphicsContext gc, Edge edge) {
        double sx = edge.getSource().getX();
        double sy = edge.getSource().getY();
        double tx = edge.getTarget().getX();
        double ty = edge.getTarget().getY();

        double dx = tx - sx;
        double dy = ty - sy;
        double len = Math.hypot(dx, dy);
        if (len == 0) {
            return; // source and target at the same spot — nothing to draw
        }
        double ux = dx / len; // unit vector along the edge
        double uy = dy / len;

        // Does the reverse edge also exist? If so, curve this one to the side.
        boolean twoWay = currentGraph.hasDirectedEdge(edge.getTarget(), edge.getSource());

        // Stop the line at the target circle's edge so the arrow tip touches it.
        double endX = tx - ux * NODE_RADIUS;
        double endY = ty - uy * NODE_RADIUS;
        // Start just outside the source circle too, for a cleaner look.
        double startX = sx + ux * NODE_RADIUS;
        double startY = sy + uy * NODE_RADIUS;

        Color color = colorForEdge(edge);
        gc.setStroke(color);
        gc.setLineWidth(edgeStates.get(edge) == EdgeState.ACCEPTED ? 3.5 : 1.8);

        double labelX, labelY;     // where to draw the weight number
        double arrowFromX, arrowFromY; // direction the arrowhead points along

        if (twoWay) {
            // Perpendicular offset for the curve's control point.
            double px = -uy * CURVE_OFFSET;
            double py = ux * CURVE_OFFSET;
            double ctrlX = (startX + endX) / 2 + px;
            double ctrlY = (startY + endY) / 2 + py;

            gc.beginPath();
            gc.moveTo(startX, startY);
            gc.quadraticCurveTo(ctrlX, ctrlY, endX, endY);
            gc.stroke();

            // The arrow should point along the curve's tangent near the end,
            // which is the direction from the control point to the end.
            arrowFromX = endX - ctrlX;
            arrowFromY = endY - ctrlY;
            labelX = ctrlX;
            labelY = ctrlY;
        } else {
            gc.strokeLine(startX, startY, endX, endY);
            arrowFromX = ux;
            arrowFromY = uy;
            labelX = (startX + endX) / 2;
            labelY = (startY + endY) / 2 - 8;
        }

        drawArrowHead(gc, endX, endY, arrowFromX, arrowFromY, color);

        // Weight number.
        gc.setFill(WEIGHT_COLOR);
        gc.fillText(String.valueOf((int) edge.getWeight()), labelX, labelY);
    }

    /**
     * Draws a small arrowhead at point (x, y), pointing in the direction of
     * the vector (dirX, dirY). Two short lines form the head.
     */
    private void drawArrowHead(GraphicsContext gc, double x, double y,
                               double dirX, double dirY, Color color) {
        double len = Math.hypot(dirX, dirY);
        if (len == 0) return;
        double ux = dirX / len;
        double uy = dirY / len;

        // Two points behind the tip, spread to each side.
        double baseX = x - ux * ARROW_LENGTH;
        double baseY = y - uy * ARROW_LENGTH;
        // Perpendicular direction.
        double perpX = -uy;
        double perpY = ux;

        double leftX = baseX + perpX * ARROW_WIDTH;
        double leftY = baseY + perpY * ARROW_WIDTH;
        double rightX = baseX - perpX * ARROW_WIDTH;
        double rightY = baseY - perpY * ARROW_WIDTH;

        gc.setFill(color);
        gc.beginPath();
        gc.moveTo(x, y);
        gc.lineTo(leftX, leftY);
        gc.lineTo(rightX, rightY);
        gc.closePath();
        gc.fill();
    }

    private Color colorForEdge(Edge edge) {
        Double fade = fadingEdges.get(edge);
        if (fade != null) {
            return EDGE_REJECTED.interpolate(EDGE_NORMAL, fade);
        }
        EdgeState state = edgeStates.getOrDefault(edge, EdgeState.NORMAL);
        return switch (state) {
            case EVALUATING -> EDGE_EVALUATING;
            case ACCEPTED   -> EDGE_ACCEPTED;
            case REJECTED   -> EDGE_REJECTED;
            case NORMAL     -> EDGE_NORMAL;
        };
    }

    private void drawDragPreview(double x1, double y1, double x2, double y2) {
        GraphicsContext gc = graphCanvas.getGraphicsContext2D();
        gc.setStroke(DRAG_COLOR);
        gc.setLineWidth(1.5);
        gc.setLineDashes(6, 4);
        gc.strokeLine(x1, y1, x2, y2);
        gc.setLineDashes(0);
    }
}
