package com.strobel.decompiler.languages.java.ast.transforms;

import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.languages.java.ast.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Detects and unflattens control flow flattening obfuscation.
 * This is one of the most important deobfuscation transforms.
 * 
 * Pattern detected:
 * - while(true) or while(condition)
 * - switch statement on state variable
 * - state transitions in each case
 * - terminal states with return/break
 */
public class ControlFlowUnflatteningTransform extends DepthFirstAstVisitor<Void, Void> implements IAstTransform {
    private static final Logger LOG = Logger.getLogger(ControlFlowUnflatteningTransform.class.getSimpleName());
    
    private final DecompilerContext context;
    private int unflattenedCount = 0;
    
    public ControlFlowUnflatteningTransform(DecompilerContext context) {
        this.context = context;
    }
    
    @Override
    public void run(AstNode compilationUnit) {
        LOG.fine("Starting control flow unflattening analysis...");
        compilationUnit.acceptVisitor(this, null);
        if (unflattenedCount > 0) {
            LOG.info("Unflattened " + unflattenedCount + " control flow patterns");
        }
    }
    
    @Override
    public Void visitWhileStatement(WhileStatement node, Void data) {
        if (isFlattenedControlFlow(node)) {
            LOG.fine("Detected flattened control flow pattern");
            try {
                unflattenControlFlow(node);
                unflattenedCount++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to unflatten control flow", e);
            }
        }
        return super.visitWhileStatement(node, data);
    }
    
    /**
     * Detects if a while loop is a flattened control flow pattern.
     */
    private boolean isFlattenedControlFlow(WhileStatement whileLoop) {
        // Check for while(true) or while with simple condition
        Expression condition = whileLoop.getCondition();
        if (!isInfiniteOrSimpleLoop(condition)) {
            return false;
        }
        
        // Check body is a single switch statement
        Statement body = whileLoop.getEmbeddedStatement();
        if (body instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) body;
            if (block.getStatements().size() == 1) {
                Statement first = block.getStatements().firstOrNullObject();
                if (first instanceof SwitchStatement) {
                    return analyzeSwitch((SwitchStatement) first);
                }
            }
        } else if (body instanceof SwitchStatement) {
            return analyzeSwitch((SwitchStatement) body);
        }
        
        return false;
    }
    
    /**
     * Analyzes if a switch statement is part of control flow flattening.
     */
    private boolean analyzeSwitch(SwitchStatement switchStmt) {
        // Check if switching on a state variable
        Expression selector = switchStmt.getExpression();
        if (!(selector instanceof IdentifierExpression)) {
            return false;
        }
        
        String stateVar = ((IdentifierExpression) selector).getIdentifier();
        
        // Analyze switch sections for state transitions
        int stateTransitions = 0;
        int terminalStates = 0;
        
        for (SwitchSection section : switchStmt.getSwitchSections()) {
            StateAnalysis analysis = analyzeSection(section, stateVar);
            if (analysis.hasStateTransition) {
                stateTransitions++;
            }
            if (analysis.isTerminal) {
                terminalStates++;
            }
        }
        
        // Heuristic: at least 3 state transitions and at least 1 terminal state
        return stateTransitions >= 3 && terminalStates >= 1;
    }
    
    /**
     * Analyzes a switch section for state transitions.
     */
    private StateAnalysis analyzeSection(SwitchSection section, String stateVar) {
        StateAnalysis result = new StateAnalysis();
        
        for (Statement stmt : section.getStatements()) {
            // Check for state variable assignment
            if (stmt instanceof ExpressionStatement) {
                Expression expr = ((ExpressionStatement) stmt).getExpression();
                if (expr instanceof AssignmentExpression) {
                    AssignmentExpression assign = (AssignmentExpression) expr;
                    if (assign.getLeft() instanceof IdentifierExpression) {
                        String varName = ((IdentifierExpression) assign.getLeft()).getIdentifier();
                        if (varName.equals(stateVar)) {
                            result.hasStateTransition = true;
                            result.nextState = extractStateValue(assign.getRight());
                        }
                    }
                }
            }
            
            // Check for terminal statements
            if (stmt instanceof ReturnStatement || 
                stmt instanceof BreakStatement ||
                stmt instanceof GotoStatement) {
                result.isTerminal = true;
            }
        }
        
        return result;
    }
    
    /**
     * Unflattens the control flow by reconstructing the original structure.
     */
    private void unflattenControlFlow(WhileStatement whileLoop) {
        Statement body = whileLoop.getEmbeddedStatement();
        SwitchStatement switchStmt = extractSwitch(body);
        if (switchStmt == null) {
            return;
        }
        
        // Build state transition graph
        StateGraph graph = buildStateGraph(switchStmt);
        
        // Find entry point (usually state 0 or 1)
        int entryState = findEntryState(graph);
        
        // Reconstruct control flow
        BlockStatement reconstructed = new BlockStatement();
        Set<Integer> visited = new HashSet<>();
        reconstructControlFlow(graph, entryState, reconstructed, visited);
        
        // Replace the while loop with reconstructed code
        whileLoop.replaceWith(reconstructed);
        
        LOG.fine("Successfully unflattened control flow with " + graph.size() + " states");
    }
    
    /**
     * Builds a state transition graph from the switch statement.
     */
    private StateGraph buildStateGraph(SwitchStatement switchStmt) {
        StateGraph graph = new StateGraph();
        String stateVar = ((IdentifierExpression) switchStmt.getExpression()).getIdentifier();
        
        for (SwitchSection section : switchStmt.getSwitchSections()) {
            // Get state number from case label
            Integer state = null;
            for (CaseLabel label : section.getCaseLabels()) {
                Expression expr = label.getExpression();
                if (expr instanceof PrimitiveExpression) {
                    Object value = ((PrimitiveExpression) expr).getValue();
                    if (value instanceof Number) {
                        state = ((Number) value).intValue();
                        break;
                    }
                }
            }
            
            if (state != null) {
                StateNode node = new StateNode(state);
                
                // Extract code and transitions
                for (Statement stmt : section.getStatements()) {
                    if (isStateTransition(stmt, stateVar)) {
                        node.nextState = extractNextState(stmt, stateVar);
                    } else if (!(stmt instanceof BreakStatement)) {
                        node.code.add(stmt.clone());
                    }
                }
                
                graph.addNode(node);
            }
        }
        
        return graph;
    }
    
    /**
     * Reconstructs the original control flow from the state graph.
     */
    private void reconstructControlFlow(StateGraph graph, int currentState, 
                                       BlockStatement output, Set<Integer> visited) {
        if (visited.contains(currentState)) {
            // Cycle detected - might need special handling
            return;
        }
        visited.add(currentState);
        
        StateNode node = graph.getNode(currentState);
        if (node == null) {
            return;
        }
        
        // Add the code from this state
        for (Statement stmt : node.code) {
            output.add(stmt.clone());
        }
        
        // Handle state transition
        if (node.nextState != null) {
            // Check if this is a conditional transition
            if (node.hasConditionalTransition()) {
                // This needs more complex analysis
                reconstructConditionalFlow(graph, node, output, visited);
            } else {
                // Simple transition to next state
                reconstructControlFlow(graph, node.nextState, output, visited);
            }
        }
    }
    
    /**
     * Reconstructs conditional control flow (if-else patterns).
     */
    private void reconstructConditionalFlow(StateGraph graph, StateNode node,
                                           BlockStatement output, Set<Integer> visited) {
        // For now, fallback to sequential reconstruction
        // AI integration can be added later
        if (node.nextState != null) {
            reconstructControlFlow(graph, node.nextState, output, visited);
        }
    }
    
    // Helper methods
    
    private boolean isInfiniteOrSimpleLoop(Expression condition) {
        if (condition instanceof PrimitiveExpression) {
            Object value = ((PrimitiveExpression) condition).getValue();
            return Boolean.TRUE.equals(value);
        }
        return condition instanceof IdentifierExpression;
    }
    
    private SwitchStatement extractSwitch(Statement body) {
        if (body instanceof SwitchStatement) {
            return (SwitchStatement) body;
        }
        if (body instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) body;
            if (block.getStatements().size() == 1) {
                Statement first = block.getStatements().firstOrNullObject();
                if (first instanceof SwitchStatement) {
                    return (SwitchStatement) first;
                }
            }
        }
        return null;
    }
    
    private Integer extractStateValue(Expression expr) {
        if (expr instanceof PrimitiveExpression) {
            Object value = ((PrimitiveExpression) expr).getValue();
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }
    
    private boolean isStateTransition(Statement stmt, String stateVar) {
        if (stmt instanceof ExpressionStatement) {
            Expression expr = ((ExpressionStatement) stmt).getExpression();
            if (expr instanceof AssignmentExpression) {
                AssignmentExpression assign = (AssignmentExpression) expr;
                if (assign.getLeft() instanceof IdentifierExpression) {
                    return ((IdentifierExpression) assign.getLeft()).getIdentifier().equals(stateVar);
                }
            }
        }
        return false;
    }
    
    private Integer extractNextState(Statement stmt, String stateVar) {
        if (stmt instanceof ExpressionStatement) {
            Expression expr = ((ExpressionStatement) stmt).getExpression();
            if (expr instanceof AssignmentExpression) {
                return extractStateValue(((AssignmentExpression) expr).getRight());
            }
        }
        return null;
    }
    
    private int findEntryState(StateGraph graph) {
        // Usually 0 or 1, or the smallest state number
        if (graph.hasNode(0)) return 0;
        if (graph.hasNode(1)) return 1;
        return graph.getMinState();
    }
    
    
    // Inner classes
    
    private static class StateAnalysis {
        boolean hasStateTransition = false;
        boolean isTerminal = false;
        Integer nextState = null;
    }
    
    private static class StateNode {
        final int state;
        final List<Statement> code = new ArrayList<>();
        Integer nextState = null;
        List<ConditionalTransition> conditionalTransitions = new ArrayList<>();
        
        StateNode(int state) {
            this.state = state;
        }
        
        boolean hasConditionalTransition() {
            return !conditionalTransitions.isEmpty();
        }
    }
    
    private static class ConditionalTransition {
        Expression condition;
        Integer targetState;
        
        ConditionalTransition(Expression condition, Integer targetState) {
            this.condition = condition;
            this.targetState = targetState;
        }
    }
    
    private static class StateGraph {
        private final Map<Integer, StateNode> nodes = new HashMap<>();
        
        void addNode(StateNode node) {
            nodes.put(node.state, node);
        }
        
        StateNode getNode(int state) {
            return nodes.get(state);
        }
        
        boolean hasNode(int state) {
            return nodes.containsKey(state);
        }
        
        int size() {
            return nodes.size();
        }
        
        int getMinState() {
            return nodes.keySet().stream().min(Integer::compareTo).orElse(0);
        }
    }
}