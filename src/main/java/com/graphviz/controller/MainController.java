package com.graphviz.controller;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for MainView.fxml.
 *
 * Responsibilities:
 *   1. Build graphs — random generation and manual mouse drawing.
 *   2. Run Kruskal's MST algorithm and animate it step by step.
 *   3. Drive the playback controls (Play / Pause / Step / Reset).
 *
 * Threading rule (important): the algorithm animation runs on a background
 * thread so the GUI never freezes. Every time the animation needs to redraw
 * the canvas or update a label, it does so through Platform.runLater(), which
 * hands the work to the JavaFX Application Thread — the only thread allowed to
 * touch UI controls.
 *
 * How the colors are stored: the model classes (Graph, Edge, Node) stay free
 * of any JavaFX code. The visual state of each edge lives here in the
 * controller, in the edgeStates map. This keeps the algorithm plain and testable.
 */
public class MainController {

    // --- Visual constants ---
    private static final double NODE_RADIUS        = 18.0;
    private static final double EDGE_HIT_THRESHOLD = 6.0;

    private static final Color NODE_FILL    = Color.web("#a8d8ea");
    private static final Color NODE_STROKE  = Color.web("#2c3e50");
    private static final Color WEIGHT_COLOR = Color.web("#c0392b");
    private static final Color DRAG_COLOR   = Color.web("#3498db");

    // Edge state colors
    private static final Color EDGE_NORMAL    = Color.web("#888888"); // plain gray
    private static final Color EDGE_EVALUATING = Color.web("#f1c40f"); // yellow
    private static final Color EDGE_ACCEPTED  = Color.web("#1e8449"); // green
    private static final Color EDGE_REJECTED  = Color.web("#c0392b"); // red

    private static final Font NODE_FONT   = Font.font("Arial", 13);
    private static final Font WEIGHT_FONT = Font.font("Arial", 11);

    // --- Animation timing (milliseconds) ---
    private static final long EVALUATING_PAUSE  = 500; // how long an edge stays yellow
    private static final long BETWEEN_STEPS_PAUSE = 350; // gap between steps in Play mode
    private static final long FADE_DURATION_MS  = 500; // red→gray fade length

    /** The visual state of a single edge while the animation runs. */
    private enum EdgeState { NORMAL, EVALUATING, ACCEPTED, REJECTED }

    // --- FXML fields ---
    @FXML private Canvas graphCanvas;
    @FXML private Label  statusLabel;
    @FXML private Button playButton;
    @FXML private Button pauseButton;
    @FXML private Button stepButton;
    @FXML private Button resetButton;

    // --- Model + helpers ---
    private final RandomGraphGenerator generator = new RandomGraphGenerator();
    private final KruskalSolver solver = new KruskalSolver();
    private Graph currentGraph;

    // --- Mouse drag state ---
    private Node pressedNode;
    private double pressX, pressY;

    // --- Color state for drawing (edge → its current visual state) ---
    private final Map<Edge, EdgeState> edgeStates = new HashMap<>();
    // Edges currently fading from red to gray, mapped to how far along the fade is (0..1).
    private final Map<Edge, Double> fadingEdges = new HashMap<>();

    // --- Algorithm playback state ---
    private List<MstStep> steps;          // the recorded Kruskal steps to play back
    private int currentStep;              // index of the next step to run
    private volatile boolean playing;     // true while auto-playing
    private volatile boolean stepRequested; // one-shot flag set by the Step button
    private volatile boolean runActive;   // true while the playback thread is alive
    private Thread animationThread;
    private int mstTotalWeight;           // running total of accepted edge weights

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
        statusLabel.setText("Click empty space = new node · Drag node→node = edge · Right-click edge = edit weight.");
    }

    // -------------------------------------------------------------------------
    // Graph building buttons
    // -------------------------------------------------------------------------

    @FXML
    private void onCreateRandomGraph() {
        stopAnyRun(); // a new graph cancels any prepared/running animation
        currentGraph = generator.generate(graphCanvas.getWidth(), graphCanvas.getHeight());
        clearColors();
        disablePlaybackButtons();
        drawGraph();
        statusLabel.setText("Random graph created: "
                + currentGraph.getNodes().size() + " nodes, "
                + currentGraph.getEdges().size() + " edges. Click 'Run Kruskal MST' to begin.");
    }

    // -------------------------------------------------------------------------
    // Kruskal MST: prepare and playback controls
    // -------------------------------------------------------------------------

    /**
     * Prepares an MST run: solves Kruskal instantly, stores the step list,
     * resets colors, and enables the playback buttons. Does not start playing yet.
     */
    @FXML
    private void onRunKruskal() {
        if (currentGraph.getNodes().isEmpty() || currentGraph.getEdges().isEmpty()) {
            statusLabel.setText("Please build a graph with at least one edge first.");
            return;
        }

        stopAnyRun();

        // Solve instantly on the JavaFX thread — this is just plain computation.
        steps = solver.solve(currentGraph);
        currentStep = 0;
        mstTotalWeight = 0;
        clearColors();
        drawGraph();

        // Enable playback; start the background thread that waits for Play/Step.
        playButton.setDisable(false);
        pauseButton.setDisable(false);
        stepButton.setDisable(false);
        resetButton.setDisable(false);

        startAnimationThread();
        statusLabel.setText("Ready. Press Play to run automatically, or Step to advance one edge at a time.");
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
        playing = false;      // stepping is manual, so make sure auto-play is off
        stepRequested = true; // the background loop will run exactly one step
    }

    /**
     * Resets the animation WITHOUT deleting the graph.
     * Stops the loop, clears all colors, and rewinds to the first step so the
     * same graph can be replayed from the beginning.
     */
    @FXML
    private void onReset() {
        stopAnyRun();
        currentStep = 0;
        mstTotalWeight = 0;
        clearColors();
        drawGraph();

        // If we still have a solved step list, re-arm the thread so Play works again.
        if (steps != null && !steps.isEmpty()) {
            startAnimationThread();
            statusLabel.setText("Reset. The graph is unchanged — press Play or Step to run again.");
        } else {
            disablePlaybackButtons();
            statusLabel.setText("Reset.");
        }
    }

    // -------------------------------------------------------------------------
    // The playback engine (runs on a background thread)
    // -------------------------------------------------------------------------

    /**
     * Starts the single background thread that walks through the step list.
     * The thread waits until Play is on or a Step is requested, then runs one
     * step. All drawing is pushed to the JavaFX thread via Platform.runLater().
     */
    private void startAnimationThread() {
        runActive = true;
        playing = false;
        stepRequested = false;

        animationThread = new Thread(() -> {
            try {
                while (runActive && currentStep < steps.size()) {
                    // Wait here until the user presses Play (auto) or Step (one-shot).
                    while (runActive && !playing && !stepRequested) {
                        Thread.sleep(20);
                    }
                    if (!runActive) {
                        return; // a reset or new graph stopped us
                    }

                    boolean wasSingleStep = stepRequested;
                    stepRequested = false;

                    runOneStep();

                    if (wasSingleStep) {
                        // After a single Step, fall back to waiting again.
                        playing = false;
                    } else {
                        // In Play mode, pause briefly before the next step.
                        Thread.sleep(BETWEEN_STEPS_PAUSE);
                    }
                }

                // Reached the end of the list — show the final summary.
                if (runActive && currentStep >= steps.size()) {
                    final int total = mstTotalWeight;
                    Platform.runLater(() -> {
                        playing = false;
                        statusLabel.setText("MST complete — total weight = " + total
                                + ". Press Reset to run again on the same graph.");
                        playButton.setDisable(true);
                        stepButton.setDisable(true);
                    });
                }
            } catch (InterruptedException e) {
                // Thread was interrupted by a reset — just stop cleanly.
                Thread.currentThread().interrupt();
            }
        });
        animationThread.setDaemon(true);
        animationThread.start();
    }

    /**
     * Runs exactly one Kruskal step: highlight the edge yellow, pause, then
     * color it green (accepted) or red-then-fade (rejected).
     * Called from the background thread.
     */
    private void runOneStep() throws InterruptedException {
        MstStep step = steps.get(currentStep);
        Edge edge = step.getEdge();

        // 1) Highlight the edge being checked in yellow.
        edgeStates.put(edge, EdgeState.EVALUATING);
        final int stepNumber = currentStep + 1;
        Platform.runLater(() -> {
            drawGraph();
            statusLabel.setText("Step " + stepNumber + "/" + steps.size()
                    + ": checking edge " + edge.getSource().getId() + "–" + edge.getTarget().getId()
                    + " (weight " + (int) edge.getWeight() + ")");
        });
        Thread.sleep(EVALUATING_PAUSE);

        // 2) Apply the recorded decision.
        if (step.getDecision() == MstStep.Decision.ACCEPTED) {
            edgeStates.put(edge, EdgeState.ACCEPTED);
            mstTotalWeight += (int) edge.getWeight();
            Platform.runLater(this::drawGraph);
        } else {
            // Rejected: show red, then smoothly fade back to gray.
            edgeStates.put(edge, EdgeState.REJECTED);
            Platform.runLater(this::drawGraph);
            startFade(edge);
        }

        currentStep++;
    }

    /**
     * Smoothly fades a rejected edge from red back to normal gray.
     * The fade runs on the JavaFX thread using an AnimationTimer, which calls
     * us once per frame with the current time so we can compute progress.
     */
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
                        // Fade done — edge is back to normal.
                        fadingEdges.remove(edge);
                        edgeStates.put(edge, EdgeState.NORMAL);
                        stop();
                    }
                    drawGraph();
                }
            }.start();
        });
    }

    /** Stops the playback thread and clears its flags. The graph stays intact. */
    private void stopAnyRun() {
        runActive = false;
        playing = false;
        stepRequested = false;
        if (animationThread != null) {
            animationThread.interrupt();
            animationThread = null;
        }
        fadingEdges.clear();
    }

    /** Resets every edge back to NORMAL color state. */
    private void clearColors() {
        edgeStates.clear();
        fadingEdges.clear();
    }

    private void disablePlaybackButtons() {
        playButton.setDisable(true);
        pauseButton.setDisable(true);
        stepButton.setDisable(true);
        resetButton.setDisable(true);
    }

    // -------------------------------------------------------------------------
    // Mouse handlers (manual graph drawing)
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
        pressedNode = findNodeAt(pressX, pressY);
    }

    private void onCanvasDragged(MouseEvent e) {
        if (pressedNode == null) {
            return;
        }
        drawGraph();
        drawDragPreview(pressedNode.getX(), pressedNode.getY(), e.getX(), e.getY());
    }

    private void onCanvasReleased(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) {
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

    /**
     * Editing the graph would make any prepared MST run out of date, so we
     * cancel the run and clear colors whenever the user changes the graph.
     */
    private void cancelRunBecauseGraphChanged() {
        if (runActive || steps != null) {
            stopAnyRun();
            steps = null;
            clearColors();
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
        if (currentGraph.hasEdgeBetween(source, target)) {
            statusLabel.setText("Edge already exists between '" + source.getId()
                    + "' and '" + target.getId() + "'.");
            drawGraph();
            return;
        }
        cancelRunBecauseGraphChanged();
        currentGraph.addEdge(source, target, 1);
        drawGraph();
        statusLabel.setText("Edge '" + source.getId() + "'→'" + target.getId()
                + "' added with weight 1. Right-click the edge to change its weight.");
    }

    private void editEdgeWeight(Edge edge) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf((int) edge.getWeight()));
        dialog.setTitle("Edit Edge Weight");
        dialog.setHeaderText("Edge: " + edge.getSource().getId() + " → " + edge.getTarget().getId());
        dialog.setContentText("New weight (positive integer):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            try {
                int newWeight = Integer.parseInt(input.trim());
                if (newWeight > 0) {
                    cancelRunBecauseGraphChanged();
                    edge.setWeight(newWeight);
                    drawGraph();
                    statusLabel.setText("Weight updated to " + newWeight + ".");
                } else {
                    statusLabel.setText("Weight must be a positive number — not changed.");
                }
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

    /**
     * Paints the whole graph, using each edge's current color state.
     * Edges are drawn first so node circles sit on top of the lines.
     */
    private void drawGraph() {
        GraphicsContext gc = graphCanvas.getGraphicsContext2D();

        gc.clearRect(0, 0, graphCanvas.getWidth(), graphCanvas.getHeight());
        gc.setFill(Color.web("#fafafa"));
        gc.fillRect(0, 0, graphCanvas.getWidth(), graphCanvas.getHeight());

        // --- Step 1: edges (colored by their current state) ---
        gc.setFont(WEIGHT_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        for (Edge edge : currentGraph.getEdges()) {
            double x1 = edge.getSource().getX();
            double y1 = edge.getSource().getY();
            double x2 = edge.getTarget().getX();
            double y2 = edge.getTarget().getY();

            gc.setStroke(colorForEdge(edge));
            // Accepted (MST) edges are drawn thicker so the tree stands out.
            gc.setLineWidth(edgeStates.get(edge) == EdgeState.ACCEPTED ? 3.5 : 1.5);
            gc.strokeLine(x1, y1, x2, y2);

            double midX = (x1 + x2) / 2.0;
            double midY = (y1 + y2) / 2.0 - 8.0;
            gc.setFill(WEIGHT_COLOR);
            gc.fillText(String.valueOf((int) edge.getWeight()), midX, midY);
        }

        // --- Step 2: nodes on top ---
        gc.setFont(NODE_FONT);
        gc.setLineWidth(2.0);
        for (Node node : currentGraph.getNodes()) {
            double cx = node.getX();
            double cy = node.getY();
            double r  = NODE_RADIUS;

            gc.setFill(NODE_FILL);
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            gc.setStroke(NODE_STROKE);
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
            gc.setFill(NODE_STROKE);
            gc.fillText(node.getId(), cx, cy);
        }
    }

    /**
     * Picks the color to draw an edge with, based on its state.
     * A rejected edge that is mid-fade is blended between red and gray.
     */
    private Color colorForEdge(Edge edge) {
        // If this edge is fading, blend red→gray by how far the fade has gone.
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
