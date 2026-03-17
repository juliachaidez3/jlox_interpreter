package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

class LoxClass implements LoxCallable {
    final String name;
    final LoxClass superclass;
    private final Map<String, LoxFunction> methods;

    LoxClass(String name, LoxClass superclass,
             Map<String, LoxFunction> methods) {
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
    }

    LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        if (superclass != null) {
            return superclass.findMethod(name);
        }

        return null;
    }

    LoxClass findTopDefiningClass(String name) {
        LoxClass top = null;

        if (superclass != null) {
            top = superclass.findTopDefiningClass(name);
        }

        if (top != null) return top;
        if (methods.containsKey(name)) return this;

        return null;
    }

    LoxClass findNextDefiningClassBelow(LoxClass current, String name) {
        if (superclass == null) return null;

        if (superclass == current) {
            if (methods.containsKey(name)) return this;
            return null;
        }

        LoxClass found = superclass.findNextDefiningClassBelow(current, name);
        if (found != null) return found;

        if (methods.containsKey(name)) return this;
        return null;
    }

    LoxFunction getMethodFromClass(LoxClass definingClass, String name) {
        LoxFunction method = definingClass.methods.get(name);
        if (method == null) return null;

        return new LoxFunction(method.declaration, method.closure,
                name.equals("init"), definingClass, name);
    }

    @Override
    public Object call(Interpreter interpreter,
                       List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);

        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }

        return instance;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public String toString() {
        return name;
    }
}