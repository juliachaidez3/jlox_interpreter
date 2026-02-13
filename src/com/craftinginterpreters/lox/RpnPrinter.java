package com.craftinginterpreters.lox;

class RpnPrinter implements Expr.Visitor<String> {

    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        String left = expr.left.accept(this);
        String right = expr.right.accept(this);
        // operand operand operator
        return join(left, right, expr.operator.lexeme);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        // grouping just yields the inner expression in RPN
        return expr.expression.accept(this);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        if (expr.value instanceof String) {
            // keep quotes so it's clear it's a string literal
            return "\"" + expr.value + "\"";
        }
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        // For unary, put operand first then operator (RPN)
        String right = expr.right.accept(this);
        return join(right, expr.operator.lexeme);
    }

    // Helpers

    // Join tokens with single spaces, ignoring empty pieces.
    private String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (!first) sb.append(' ');
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }

    // Test similar to AstPrinter.main
    public static void main(String[] args) {
        // Builds: (* (+ 1 2) (group 45.67))  which in RPN is: "1 2 + 45.67 *"
        Expr expression = new Expr.Binary(
                new Expr.Binary(
                        new Expr.Literal(1),
                        new Token(TokenType.PLUS, "+", null, 1),
                        new Expr.Literal(2)),
                new Token(TokenType.STAR, "*", null, 1),
                new Expr.Grouping(new Expr.Literal(45.67)));

        System.out.println(new RpnPrinter().print(expression));
        // Expected output: "1 2 + 45.67 *"
    }
}