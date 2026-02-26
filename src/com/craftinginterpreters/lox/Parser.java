package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // program → declaration* EOF ;
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    // declaration → varDecl | statement ;
    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    // varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    // statement → exprStmt | printStmt | block ;
    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }

    // printStmt → "print" expression ";" ;
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    // exprStmt → expression ";" ;
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // block → "{" declaration* "}" ;
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // expression → assignment ;
    private Expr expression() {
        return assignment();
    }

    // assignment → IDENTIFIER "=" assignment | comma ;
    //
    // NOTE: since you have comma/ternary, we keep them "below" assignment.
    private Expr assignment() {
        Expr expr = comma();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            // Report error but don't throw (same behavior as the book here).
            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr comma() {
        Expr expr = ternary();

        while (match(COMMA)) {
            Token operator = previous();
            Expr right = ternary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // ternary → equality ( "?" expression ":" ternary )? ;
    private Expr ternary() {
        Expr expr = equality();

        if (match(QUESTION)) {
            Expr thenBranch = expression(); // allows comma inside the middle, like C
            consume(COLON, "Expect ':' after then branch of conditional expression.");
            Expr elseBranch = ternary();    // right-associative
            expr = new Expr.Ternary(expr, thenBranch, elseBranch);
        }

        return expr;
    }

    // equality → comparison ( ( "!=" | "==" ) comparison )* ;
    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // term → factor ( ( "-" | "+" ) factor )* ;
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // factor → unary ( ( "/" | "*" ) unary )* ;
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // unary → ( "!" | "-" ) unary | primary ;
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    // primary → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" | IDENTIFIER ;
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE))  return new Expr.Literal(true);
        if (match(NIL))   return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // Error productions: binary operator with no left operand
        if (match(COMMA,
                EQUAL_EQUAL, BANG_EQUAL,
                GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
                PLUS,
                STAR, SLASH)) {
            Token operator = previous();
            // Parse and discard RHS (we return it as the recovered expression)
            return recoverMissingLeftOperand(operator);
        }

        throw error(peek(), "Expect expression.");
    }

    private Expr recoverMissingLeftOperand(Token operator) {
        // Report but DO NOT throw; we want to keep parsing.
        Lox.error(operator, "Missing left-hand operand.");

        // Parse and discard a RHS at the correct precedence level
        // The same kind of operand that operator expects on its right
        switch (operator.type) {
            // Lowest precedence
            case COMMA:
                return ternary();

            // Equality operators take comparison operands.
            case EQUAL_EQUAL:
            case BANG_EQUAL:
                return comparison();

            // Comparison operators take term operands.
            case GREATER:
            case GREATER_EQUAL:
            case LESS:
            case LESS_EQUAL:
                return term();

            // + takes factor operands.
            case PLUS:
                return factor();

            // * and / take unary operands.
            case STAR:
            case SLASH:
                return unary();

            default:
                // Fallback: parse *something* so we don't loop forever.
                return unary();
        }
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    // Panic-mode recovery for declarations/statements.
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }
}