package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class LoxInstance {
    final LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

    Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }

        LoxClass definingClass = klass.findTopDefiningClass(name.lexeme);
        if (definingClass != null) {
            LoxFunction method = klass.getMethodFromClass(definingClass, name.lexeme);
            return method.bind(this);
        }

        throw new RuntimeError(name,
                "Undefined property '" + name.lexeme + "'.");
    }

    void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}