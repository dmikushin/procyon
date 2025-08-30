package com.strobel.decompiler.languages.java.ast.transforms;

import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.languages.java.ast.*;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Detects and removes opaque predicates - conditions that always evaluate to the same value
 * but are designed to be hard to analyze statically.
 * 
 * Common patterns:
 * - (x * x) >= 0  // Always true for real numbers
 * - (2 * (x/2)) == x  // Always true for even x, false for odd
 * - ((x - 1) | 1) >= 1  // Always true
 * - (7*y*y - 1) % 7 == 0  // Always false
 */
public class OpaquePredicateRemovalTransform extends DepthFirstAstVisitor<Void, Void> implements IAstTransform {
    private static final Logger LOG = Logger.getLogger(OpaquePredicateRemovalTransform.class.getSimpleName());
    
    private final DecompilerContext context;
    private int removedPredicates = 0;
    
    public OpaquePredicateRemovalTransform(DecompilerContext context) {
        this.context = context;
    }
    
    @Override
    public void run(AstNode compilationUnit) {
        LOG.fine("Starting opaque predicate analysis...");
        compilationUnit.acceptVisitor(this, null);
        if (removedPredicates > 0) {
            LOG.info("Removed " + removedPredicates + " opaque predicates");
        }
    }
    
    @Override
    public Void visitIfElseStatement(IfElseStatement node, Void data) {
        Expression condition = node.getCondition();
        OpaquePredicateType type = detectOpaquePredicate(condition);
        
        if (type != OpaquePredicateType.NONE) {
            LOG.fine("Detected opaque predicate: " + type + " in condition: " + condition);
            removeOpaquePredicate(node, type);
            removedPredicates++;
        }
        
        return super.visitIfElseStatement(node, data);
    }
    
    @Override
    public Void visitConditionalExpression(ConditionalExpression node, Void data) {
        Expression condition = node.getCondition();
        OpaquePredicateType type = detectOpaquePredicate(condition);
        
        if (type != OpaquePredicateType.NONE) {
            LOG.fine("Detected opaque predicate in ternary: " + condition);
            
            if (type == OpaquePredicateType.ALWAYS_TRUE) {
                node.replaceWith(node.getTrueExpression());
            } else if (type == OpaquePredicateType.ALWAYS_FALSE) {
                node.replaceWith(node.getFalseExpression());
            }
            removedPredicates++;
        }
        
        return super.visitConditionalExpression(node, data);
    }
    
    /**
     * Detects if an expression is an opaque predicate.
     */
    private OpaquePredicateType detectOpaquePredicate(Expression expr) {
        // Pattern: x*x >= 0 (always true for real numbers)
        if (isSquareNonNegative(expr)) {
            return OpaquePredicateType.ALWAYS_TRUE;
        }
        
        // Pattern: (x | 1) >= 1 (always true)
        if (isBitwiseOrAlwaysPositive(expr)) {
            return OpaquePredicateType.ALWAYS_TRUE;
        }
        
        // Pattern: (7*y*y - 1) % 7 == 0 (always false)
        if (isModuloAlwaysFalse(expr)) {
            return OpaquePredicateType.ALWAYS_FALSE;
        }
        
        // Pattern: x != x (always false)
        if (isSelfInequality(expr)) {
            return OpaquePredicateType.ALWAYS_FALSE;
        }
        
        // Pattern: x == x (always true)
        if (isSelfEquality(expr)) {
            return OpaquePredicateType.ALWAYS_TRUE;
        }
        
        // More complex patterns requiring deeper analysis
        if (isComplexOpaquePredicate(expr)) {
            return analyzeComplexPredicate(expr);
        }
        
        return OpaquePredicateType.NONE;
    }
    
    /**
     * Pattern: x*x >= 0 or variations
     */
    private boolean isSquareNonNegative(Expression expr) {
        if (!(expr instanceof BinaryOperatorExpression)) {
            return false;
        }
        
        BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
        
        // Check for >= 0 or > -1
        if (binary.getOperator() == BinaryOperatorType.GREATER_THAN_OR_EQUAL) {
            if (isZero(binary.getRight()) && isSquare(binary.getLeft())) {
                return true;
            }
        } else if (binary.getOperator() == BinaryOperatorType.GREATER_THAN) {
            if (isNegativeOne(binary.getRight()) && isSquare(binary.getLeft())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if expression is a square (x * x)
     */
    private boolean isSquare(Expression expr) {
        if (!(expr instanceof BinaryOperatorExpression)) {
            return false;
        }
        
        BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
        if (binary.getOperator() != BinaryOperatorType.MULTIPLY) {
            return false;
        }
        
        // Check if both operands are the same variable
        return areEquivalent(binary.getLeft(), binary.getRight());
    }
    
    /**
     * Pattern: (x | 1) >= 1
     */
    private boolean isBitwiseOrAlwaysPositive(Expression expr) {
        if (!(expr instanceof BinaryOperatorExpression)) {
            return false;
        }
        
        BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
        
        if (binary.getOperator() == BinaryOperatorType.GREATER_THAN_OR_EQUAL) {
            if (isOne(binary.getRight())) {
                Expression left = binary.getLeft();
                if (left instanceof BinaryOperatorExpression) {
                    BinaryOperatorExpression leftBinary = (BinaryOperatorExpression) left;
                    if (leftBinary.getOperator() == BinaryOperatorType.BITWISE_OR) {
                        // Check if one operand is 1 or any odd number
                        return isOddNumber(leftBinary.getRight()) || isOddNumber(leftBinary.getLeft());
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Pattern: (7*y*y - 1) % 7 == 0
     */
    private boolean isModuloAlwaysFalse(Expression expr) {
        if (!(expr instanceof BinaryOperatorExpression)) {
            return false;
        }
        
        BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
        
        if (binary.getOperator() == BinaryOperatorType.EQUALITY && isZero(binary.getRight())) {
            Expression left = binary.getLeft();
            if (left instanceof BinaryOperatorExpression) {
                BinaryOperatorExpression modulo = (BinaryOperatorExpression) left;
                if (modulo.getOperator() == BinaryOperatorType.MODULUS) {
                    // Check for pattern (n*x*x - 1) % n where n is the same
                    return isModuloPattern(modulo);
                }
            }
        }
        
        return false;
    }
    
    /**
     * Pattern: x != x (always false)
     */
    private boolean isSelfInequality(Expression expr) {
        if (!(expr instanceof BinaryOperatorExpression)) {
            return false;
        }
        
        BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
        if (binary.getOperator() == BinaryOperatorType.INEQUALITY) {
            return areEquivalent(binary.getLeft(), binary.getRight());
        }
        
        return false;
    }
    
    /**
     * Pattern: x == x (always true)
     */
    private boolean isSelfEquality(Expression expr) {
        if (!(expr instanceof BinaryOperatorExpression)) {
            return false;
        }
        
        BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
        if (binary.getOperator() == BinaryOperatorType.EQUALITY) {
            Expression left = binary.getLeft();
            Expression right = binary.getRight();
            
            // Skip if comparing with null (x == null is not always false)
            if (left instanceof NullReferenceExpression || right instanceof NullReferenceExpression) {
                return false;
            }
            
            return areEquivalent(left, right);
        }
        
        return false;
    }
    
    /**
     * Checks for more complex opaque predicates
     */
    private boolean isComplexOpaquePredicate(Expression expr) {
        // Check for nested conditions that might be opaque
        if (expr instanceof BinaryOperatorExpression) {
            BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
            
            // Pattern: (x + 1) > x (always true unless overflow)
            if (binary.getOperator() == BinaryOperatorType.GREATER_THAN) {
                if (isIncrementPattern(binary.getLeft(), binary.getRight())) {
                    return true;
                }
            }
            
            // Pattern: (x & 0) == 0 (always true)
            if (binary.getOperator() == BinaryOperatorType.EQUALITY && isZero(binary.getRight())) {
                Expression left = binary.getLeft();
                if (left instanceof BinaryOperatorExpression) {
                    BinaryOperatorExpression leftBinary = (BinaryOperatorExpression) left;
                    if (leftBinary.getOperator() == BinaryOperatorType.BITWISE_AND) {
                        if (isZero(leftBinary.getRight()) || isZero(leftBinary.getLeft())) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    private OpaquePredicateType analyzeComplexPredicate(Expression expr) {
        // For complex predicates, we might need more sophisticated analysis
        // or AI assistance to determine if they're always true/false
        
        if (expr instanceof BinaryOperatorExpression) {
            BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
            
            // (x + 1) > x is always true (ignoring overflow)
            if (binary.getOperator() == BinaryOperatorType.GREATER_THAN) {
                if (isIncrementPattern(binary.getLeft(), binary.getRight())) {
                    return OpaquePredicateType.ALWAYS_TRUE;
                }
            }
            
            // (x & 0) == 0 is always true
            if (binary.getOperator() == BinaryOperatorType.EQUALITY && isZero(binary.getRight())) {
                Expression left = binary.getLeft();
                if (left instanceof BinaryOperatorExpression) {
                    BinaryOperatorExpression leftBinary = (BinaryOperatorExpression) left;
                    if (leftBinary.getOperator() == BinaryOperatorType.BITWISE_AND) {
                        if (isZero(leftBinary.getRight()) || isZero(leftBinary.getLeft())) {
                            return OpaquePredicateType.ALWAYS_TRUE;
                        }
                    }
                }
            }
        }
        
        return OpaquePredicateType.NONE;
    }
    
    /**
     * Removes the opaque predicate from an if statement.
     */
    private void removeOpaquePredicate(IfElseStatement ifStmt, OpaquePredicateType type) {
        if (type == OpaquePredicateType.ALWAYS_TRUE) {
            // Replace if statement with true branch
            Statement trueStmt = ifStmt.getTrueStatement();
            if (trueStmt != null) {
                ifStmt.replaceWith(trueStmt);
            } else {
                ifStmt.remove();
            }
        } else if (type == OpaquePredicateType.ALWAYS_FALSE) {
            // Replace if statement with false branch (else)
            Statement falseStmt = ifStmt.getFalseStatement();
            if (falseStmt != null) {
                ifStmt.replaceWith(falseStmt);
            } else {
                ifStmt.remove();
            }
        }
    }
    
    // Helper methods
    
    private boolean isZero(Expression expr) {
        if (expr instanceof PrimitiveExpression) {
            Object value = ((PrimitiveExpression) expr).getValue();
            return Integer.valueOf(0).equals(value) || Long.valueOf(0L).equals(value);
        }
        return false;
    }
    
    private boolean isOne(Expression expr) {
        if (expr instanceof PrimitiveExpression) {
            Object value = ((PrimitiveExpression) expr).getValue();
            return Integer.valueOf(1).equals(value) || Long.valueOf(1L).equals(value);
        }
        return false;
    }
    
    private boolean isNegativeOne(Expression expr) {
        if (expr instanceof PrimitiveExpression) {
            Object value = ((PrimitiveExpression) expr).getValue();
            return Integer.valueOf(-1).equals(value) || Long.valueOf(-1L).equals(value);
        }
        return false;
    }
    
    private boolean isOddNumber(Expression expr) {
        if (expr instanceof PrimitiveExpression) {
            Object value = ((PrimitiveExpression) expr).getValue();
            if (value instanceof Number) {
                long num = ((Number) value).longValue();
                return (num & 1) == 1;
            }
        }
        return false;
    }
    
    private boolean areEquivalent(Expression left, Expression right) {
        // Simple equivalence check - could be enhanced
        if (left instanceof IdentifierExpression && right instanceof IdentifierExpression) {
            String leftId = ((IdentifierExpression) left).getIdentifier();
            String rightId = ((IdentifierExpression) right).getIdentifier();
            return leftId.equals(rightId);
        }
        
        // Check for member references
        if (left instanceof MemberReferenceExpression && right instanceof MemberReferenceExpression) {
            MemberReferenceExpression leftMember = (MemberReferenceExpression) left;
            MemberReferenceExpression rightMember = (MemberReferenceExpression) right;
            return leftMember.getMemberName().equals(rightMember.getMemberName()) &&
                   areEquivalent(leftMember.getTarget(), rightMember.getTarget());
        }
        
        // Use toString comparison as fallback
        return left.toString().equals(right.toString());
    }
    
    private boolean isIncrementPattern(Expression left, Expression right) {
        // Check if left is (right + 1) or similar
        if (left instanceof BinaryOperatorExpression) {
            BinaryOperatorExpression binary = (BinaryOperatorExpression) left;
            if (binary.getOperator() == BinaryOperatorType.ADD) {
                if (isOne(binary.getRight()) && areEquivalent(binary.getLeft(), right)) {
                    return true;
                }
                if (isOne(binary.getLeft()) && areEquivalent(binary.getRight(), right)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isModuloPattern(BinaryOperatorExpression modulo) {
        // Check for (n*x*x - 1) % n pattern
        // This is a simplified check - could be enhanced
        Expression dividend = modulo.getLeft();
        Expression divisor = modulo.getRight();
        
        if (dividend instanceof BinaryOperatorExpression) {
            BinaryOperatorExpression sub = (BinaryOperatorExpression) dividend;
            if (sub.getOperator() == BinaryOperatorType.SUBTRACT && isOne(sub.getRight())) {
                // Check if left side contains multiplication by the same divisor
                return containsMultiplicationBy(sub.getLeft(), divisor);
            }
        }
        
        return false;
    }
    
    private boolean containsMultiplicationBy(Expression expr, Expression factor) {
        if (expr instanceof BinaryOperatorExpression) {
            BinaryOperatorExpression binary = (BinaryOperatorExpression) expr;
            if (binary.getOperator() == BinaryOperatorType.MULTIPLY) {
                return areEquivalent(binary.getLeft(), factor) || 
                       areEquivalent(binary.getRight(), factor);
            }
        }
        return false;
    }
    
    private enum OpaquePredicateType {
        NONE,
        ALWAYS_TRUE,
        ALWAYS_FALSE
    }
}