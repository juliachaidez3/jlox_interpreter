package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

class Lox {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadRuntimeError = false;
    static boolean hadError = false;
    private static boolean suppressErrors = false;
    private static String suppressedErrorMessage = null;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate an error in the exit code.
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);

    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;

            // First try: parse/execute as statements, but suppress parse error output.
            hadError = false;
            suppressedErrorMessage = null;
            suppressErrors = true;
            run(line);
            suppressErrors = false;

            if (hadError) {
                // Second try: parse/evaluate as a single expression (and print its value).
                hadError = false;
                runExpression(line);

                // If expression parsing also failed, show the original statement error.
                if (hadError) {
                    flushSuppressedError();
                }
            }

            // Don’t kill the session on a single error.
            hadError = false;
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        if (hadError) return;

        interpreter.interpret(statements);
    }

    private static void runExpression(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        Expr expr = parser.parseExpression();

        if (hadError) return;
        if (expr == null) return;

        interpreter.interpretExpression(expr);
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    private static void flushSuppressedError() {
        if (suppressedErrorMessage != null) {
            System.err.println(suppressedErrorMessage);
            suppressedErrorMessage = null;
        }
    }

    private static void report(int line, String where, String message) {
        hadError = true;

        String full = "[line " + line + "] Error" + where + ": " + message;

        if (suppressErrors) {
            suppressedErrorMessage = full;
            return;
        }

        System.err.println(full);
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
                "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }
}