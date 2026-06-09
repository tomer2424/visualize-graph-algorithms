package com.graphviz.controller;

import com.graphviz.model.AlgorithmStep;
import com.graphviz.model.BellmanFordResult;
import com.graphviz.model.BellmanFordSolver;
import com.graphviz.model.BellmanFordStep;
import com.graphviz.model.Edge;
import com.graphviz.model.Graph;
import com.graphviz.model.KruskalSolver;
import com.graphviz.model.MstStep;
import com.graphviz.model.Node;
import com.graphviz.model.RandomGraphGenerator;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
 *   2. Run one of two algorithms — Kruskal's MST or Bellman-Ford shortest paths —
 *      and animate it step by step.
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
    private enum Algorithm { KRUSKAL, BELLMAN_FORD }

    // --- Visual constants ---
    private static final double NODE_RADIUS        = 18.0;
    private static final double EDGE_HIT_THRESHOLD = 6.0;
    private static final double ARROW_LENGTH       = 12.0; // length of arrowhead lines
    private static final double ARROW_WIDTH        = 6.0;  // half-width of arrowhead
    private static final double CURVE_OFFSET       = 18.0; // how far opposing edges bow apart

    private static final Color NODE_FILL    = Color.web("#a8d8ea");
    private static final Color NODE_STROKE  = Color.web("#2c3e50");
    private static final Color SOURCE_FILL  = Color.web("#f9e79f"); // highlight for BF source node
    private static final Color WEIGHT_COLOR = Color.web("#c0392b");
    private static final Color DRAG_COLOR   = Color.web("#3498db");
    private static final Color DIST_COLOR   = Color.web("#1a5276"); // distance label color

    // Edge state colors
    private static final Color EDGE_NORMAL     = Color.web("#888888"); // plain gray
    private static final Color EDGE_EVALUATING = Color.web("#f1c40f"); // yellow
    private static final Color EDGE_ACCEPTED   = Color.web("#1e8449"); // green
    private static final Color EDGE_REJECTED   = Color.web("#c0392b"); // red

    private static final Font NODE_FONT   = Font.font("Arial", 13);
    private static final Font WEIGHT_FONT = Font.font("Arial", 11);
    private static final Font DIST_FONT   = Font.font("Arial", 11);

    // --- Animation timing (milliseconds) ---
    private static final long EVALUATING_PAUSE    = 500;
    private static final long BETWEEN_STEPS_PAUSE = 350;
    private static final long FADE_DURATION_MS    = 500;

    /** The visual state of a single edge while the animation runs. */
    private enum EdgeState { NORMAL, EVALUATING, ACCEPTED, REJECTED }

    // --- FXML fields ---
    @FXML private Canvas graphCanvas;
    @FXML private Label  statusLabel;
    @FXML private Button runButton;
    @FXML private Button playButton;
    @FXML private Button pauseButton;
    @FXML private Button stepButton;
    @FXML private Button resetButton;

    // --- Model + helpers ---
    private final RandomGraphGenerator generator = new RandomGraphGenerator();
    private final KruskalSolver kruskalSolver = new KruskalSolver();
    private final BellmanFordSolver bellmanFordSolver = new BellmanFordSolver();
    private Graph currentGraph;

    // --- Algorithm selection + Bellman-Ford source ---
    private Algorithm selectedAlgorithm = Algorithm.KRUSKAL;
    private Node sourceNode;              // chosen source for Bellman-Ford
    private boolean awaitingSourcePick;   // true while waiting for the user to click a source

    // --- Mouse drag state ---
    private Node pressedNode;
    private double pressX, pressY;

    // --- Color/label state for drawing ---
    private final Map<Edge, EdgeState> edgeStates = new HashMap<>();
    private final Map<Edge, Double> fadingEdges = new HashMap<>(); // red→gray fade progress (0..1)
    private final Map<Node, Double> nodeDistances = new HashMap<>(); // BF distances shown on nodes
    private final Set<Node> flashingNodes = new HashSet<>();         // nodes briefly flashed green

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

        graphCanvas.setOnMousePressed(this::onCanvasPressed);
        graphCanvas.setOnMouseDragged(this::onCanvasDragged);
        graphCanvas.setOnMouseReleased(this::onCanvasReleased);

        drawGraph();
        statusLabel.setText("Click empty space = new node · Drag node→node = directed edge · Right-click edge = edit weight.");
    }

    // -------------------------------------------------------------------------
    // Graph building + algorithm selection
    // -------------------------------------------------------------------------

    @FXML
    private void onCreateRandomGraph() {
        stopAnyRun();
        currentGraph = generator.generate(graphCanvas.getWidth(), graphCanvas.getHeight());
        sourceNode = null;
        clearColorsAndDistances();
        disablePlaybackButtons();
        // If Bellman-Ford is selected, the user must pick a new source on the new graph.
        if (selectedAlgorithm == Algorithm.BELLMAN_FORD) {
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
        stopAnyRun();
        steps = null;
        clearColorsAndDistances();
        disablePlaybackButtons();

        // Figure out which toggle is now on. The Kruskal toggle starts selected,
        // so we treat "not Bellman-Ford" as Kruskal.
        boolean bfSelected = bellmanFordToggleSelected();
        selectedAlgorithm = bfSelected ? Algorithm.BELLMAN_FORD : Algorithm.KRUSKAL;

        if (selectedAlgorithm == Algorithm.BELLMAN_FORD) {
            sourceNode = null;
            awaitingSourcePick = true;
            statusLabel.setText("Bellman-Ford selected — click a node to set the source for shortest paths.");
        } else {
            awaitingSourcePick = false;
            sourceNode = null;
            statusLabel.setText("Kruskal (MST) selected — press Run to find the minimum spanning tree.");
        }
        drawGraph();
    }

    // The Bellman-Ford toggle's selected state, injected by FXML.
    @FXML private javafx.scene.control.ToggleButton bellmanFordToggle;
    private boolean bellmanFordToggleSelected() {
        return bellmanFordToggle != null && bellmanFordToggle.isSelected();
    }

    // -------------------------------------------------------------------------
    // Run + playback controls
    // -------------------------------------------------------------------------

    /** Prepares a run for whichever algorithm is selected. */
    @FXML
    private void onRun() {
        if (currentGraph.getNodes().isEmpty() || currentGraph.getEdges().isEmpty()) {
            statusLabel.setText("Please build a graph with at least one edge first.");
            return;
        }

        if (selectedAlgorithm == Algorithm.KRUSKAL) {
            prepareKruskal();
        } else {
            prepareBellmanFord();
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
        enablePlaybackButtons();
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
        enablePlaybackButtons();
        startAnimationThread();
        statusLabel.setText("Bellman-Ford ready from source '" + sourceNode.getId()
                + "'. Press Play, or Step to advance one relaxation at a time.");
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
            enablePlaybackButtons();
            startAnimationThread();
            statusLabel.setText("Reset. The graph is unchanged — press Play or Step to run again.");
        } else {
            disablePlaybackButtons();
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

    /** Called on the JavaFX thread when the step list is finished. */
    private void finishRun() {
        playing = false;
        playButton.setDisable(true);
        stepButton.setDisable(true);

        if (selectedAlgorithm == Algorithm.KRUSKAL) {
            statusLabel.setText("MST complete — total weight = " + mstTotalWeight
                    + ". Press Reset to run again on the same graph.");
        } else {
            if (bfResult != null && bfResult.hasNegativeCycle()) {
                // Highlight the negative cycle edges in red.
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
    }

    private void enablePlaybackButtons() {
        playButton.setDisable(false);
        pauseButton.setDisable(false);
        stepButton.setDisable(false);
        resetButton.setDisable(false);
    }

    private void disablePlaybackButtons() {
        playButton.setDisable(true);
        pauseButton.setDisable(true);
        stepButton.setDisable(true);
        resetButton.setDisable(true);
    }

    // -------------------------------------------------------------------------
    // Mouse handlers (manual graph drawing + source picking)
    // -------------------------------------------------------------------------

    private void onCanvasPressed(MouseEvent e) {
        pressX = e.getX();
        pressY = e.getY();

        if (e.getButton() == MouseButton.SECONDARY) {
            Edge edge = findEdgeNear(pressX, pressY);
            if (edge != null) {
                editEdgeWeight(edge);
            }
            return;
        }

        // If we are waiting for the user to choose a Bellman-Ford source, the
        // next click on a node sets the source instead of starting a drag.
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
        if (pressedNode == null || awaitingSourcePick) {
            return;
        }
        drawGraph();
        drawDragPreview(pressedNode.getX(), pressedNode.getY(), e.getX(), e.getY());
    }

    private void onCanvasReleased(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || awaitingSourcePick) {
            return;
        }

        double releaseX = e.getX();
        double releaseY = e.getY();
        Node releaseNode = findNodeAt(releaseX, releaseY);

        if (pressedNode == null && releaseNode == null) {
            tryAddNode(releaseX, releaseY);
        } else if (pressedNode != null && releaseNode != null && !pressedNode.equals(releaseNode)) {
            tryAddEdge(pressedNode, releaseNode);
        } else if (pressedNode != null) {
            statusLabel.setText("Edge cancelled — release on a different node to connect two nodes.");
            drawGraph();
        }

        pressedNode = null;
    }

    /** Sets the Bellman-Ford source node and shows the starting distances. */
    private void setSource(Node node) {
        sourceNode = node;
        awaitingSourcePick = false;
        clearColorsAndDistances();
        for (Node n : currentGraph.getNodes()) {
            nodeDistances.put(n, Double.POSITIVE_INFINITY);
        }
        nodeDistances.put(sourceNode, 0.0);
        drawGraph();
        statusLabel.setText("Source set to '" + node.getId() + "'. Press Run to compute shortest paths.");
    }

    /** Editing the graph cancels any prepared run so the steps cannot go stale. */
    private void cancelRunBecauseGraphChanged() {
        if (runActive || steps != null) {
            stopAnyRun();
            steps = null;
            bfResult = null;
            edgeStates.clear();
            fadingEdges.clear();
            flashingNodes.clear();
            disablePlaybackButtons();
        }
    }

    private void tryAddNode(double x, double y) {
        if (findNodeAt(x, y) != null) {
            statusLabel.setText("Cannot place a node here — too close to an existing node.");
            return;
        }
        cancelRunBecauseGraphChanged();
        String id = currentGraph.nextNodeId();
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
        gc.setFill(Color.web("#fafafa"));
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

            // A flashing (just-improved) node, or the BF source, get a highlight fill.
            if (flashingNodes.contains(node)) {
                gc.setFill(EDGE_ACCEPTED);
            } else if (node.equals(sourceNode)) {
                gc.setFill(SOURCE_FILL);
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
