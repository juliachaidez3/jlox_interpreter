package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {

    final Stmt.Function declaration;
    final Environment closure;
    final boolean isInitializer;
    final LoxClass owner;
    final String methodName;

    LoxFunction(Stmt.Function declaration, Environment closure,
                boolean isInitializer, LoxClass owner, String methodName) {
        this.isInitializer = isInitializer;
        this.closure = closure;
        this.declaration = declaration;
        this.owner = owner;
        this.methodName = methodName;
    }

    LoxClass getOwner() {
        return owner;
    }

    String getMethodName() {
        return methodName;
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment,
                isInitializer, owner, methodName);
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {

        Environment environment = new Environment(closure);

        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(
                    declaration.params.get(i).lexeme,
                    arguments.get(i));
        }

        LoxFunction enclosingMethod = interpreter.currentMethod;
        interpreter.currentMethod = this;

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            interpreter.currentMethod = enclosingMethod;
            if (isInitializer) return closure.getAt(0, "this");
            return returnValue.value;
        }

        interpreter.currentMethod = enclosingMethod;

        if (isInitializer) return closure.getAt(0, "this");

        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}