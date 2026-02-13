package com.craftinginterpreters.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: GenerateAst <output directory>");
            System.exit(64);
        }

        String outputDir = args[0];

        defineAst(outputDir, "Expr", Arrays.asList(
                "Binary   : Expr left, Token operator, Expr right",
                "Grouping : Expr expression",
                "Literal  : Object value",
                "Unary    : Token operator, Expr right"
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types)
            throws IOException {

        Path outPath = Path.of(outputDir, baseName + ".java");
        Files.createDirectories(outPath.getParent());

        try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(outPath, StandardCharsets.UTF_8))) {

            writer.println("package com.craftinginterpreters.lox;");
            writer.println();
            writer.println("abstract class " + baseName + " {");

            writer.println("  interface Visitor<R> {");
            for (String type : types) {
                String typeName = type.split(":")[0].trim();
                writer.println("    R visit" + typeName + baseName + "(" +
                        typeName + " expr);");
            }
            writer.println("  }");
            writer.println();

            writer.println("  abstract <R> R accept(Visitor<R> visitor);");
            writer.println();

            for (String type : types) {
                String className = type.split(":")[0].trim();
                String fieldList = type.split(":")[1].trim();

                writer.println("  static class " + className +
                        " extends " + baseName + " {");

                String[] fields = fieldList.split(", ");
                for (String field : fields) {
                    writer.println("    final " + field + ";");
                }
                writer.println();

                writer.println("    " + className + "(" + fieldList + ") {");
                for (String field : fields) {
                    String name = field.split(" ")[1];
                    writer.println("      this." + name + " = " + name + ";");
                }
                writer.println("    }");
                writer.println();

                writer.println("    @Override");
                writer.println("    <R> R accept(Visitor<R> visitor) {");
                writer.println("      return visitor.visit" +
                        className + baseName + "(this);");
                writer.println("    }");

                writer.println("  }");
                writer.println();
            }

            writer.println("}");
        }
    }
}