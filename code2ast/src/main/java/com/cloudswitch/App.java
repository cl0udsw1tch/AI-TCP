package com.cloudswitch;

import org.json.JSONObject;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.YamlPrinter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class App {

    public static void main(String[] args) throws Exception {


        String method = args[0];
        

        if (method.equals("generate_ast")) {
            if (args.length < 3) {
                System.out.println("Usage: java App generate_ast <input-file> <output-file>");
                return;
            }
            generateAST(args[1], args[2]);
        } else if (method.equals("simplify_src")) {
            if (args.length < 3) {
                System.out.println("Usage: java App simplify_src <input-file> <output-file>");
                return;
            }
            simplifySrc(args[1], args[2]);
        } else if (method.equals("print_cu")) {
            if (args.length < 2) {
                System.out.println("Usage for print_cu: java App print_cu \"<source-code>\"");
                return;
            }
            String sourceCode = args[1];
            printCu(sourceCode);
        } else {
            System.out.println("Unknown method: " + method);
        }
    }

    private static void generateAST(String inputFilePath, String outputFilePath) throws IOException {

        StaticJavaParser.getConfiguration().setAttributeComments(false).setIgnoreAnnotationsWhenAttributingComments(false);
    
        
        // Open the input file and read it line by line
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
    
            StringBuilder yamlOutput = new StringBuilder();
            String line;
            int lineCount = 0;
    
            while ((line = reader.readLine()) != null) {
                // Parse the JSON from each line
                JSONObject inputObj = new JSONObject(line);
    
                // Extract the 'complexity' and 'src' fields from the input JSON
                String complexity = inputObj.getString("complexity");
                String src = inputObj.getString("src");
                src = removeImports(src);
                // Parse the Java source code to generate the AST
                CompilationUnit cu = StaticJavaParser.parse(src);
    
                YamlPrinter printer = new YamlPrinter(true);
                String ast = printer.output(cu);
                //System.out.println(cu.toString());
    
                // Use the current line number as the unique key in the YAML
                yamlOutput.append(lineCount).append(":\n");
                yamlOutput.append("  src: |\n").append("    ").append(src.replace("\n", "\n    ")).append("\n");
                yamlOutput.append("  ast: |\n").append("    ").append(ast.replace("\n", "\n    ")).append("\n");
                yamlOutput.append("  complexity: ").append(complexity).append("\n");
    
                lineCount++;
            }
    
            // Write the YAML output to the output file
            writer.write(yamlOutput.toString());
        }
    
        System.out.println("Processing complete. Output saved to: " + outputFilePath);
    }

    


    private static void simplifySrc(String inputFilePath, String outputFilePath) throws IOException {

        StaticJavaParser.getConfiguration()
        .setAttributeComments(false)
        .setIgnoreAnnotationsWhenAttributingComments(false);


        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {

            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                JSONObject inputObj = new JSONObject(line);
                String complexity = inputObj.getString("complexity");
     
                String simplified = getSimplified(inputObj.getString("src"));
               
                JSONObject outputObj = new JSONObject();

                outputObj.put("src", simplified);
                outputObj.put("complexity", complexity);

                writer.write(outputObj.toString());
                writer.newLine();

                lineCount++;
            }
        }

        System.out.println("Source simplification complete. Output saved to: " + outputFilePath);
    }

    private static void printCu(String sourceCode) {
        try {
            String src = removeWhiteSpaces(sourceCode);
            String simplified = getSimplified(src);
            
            System.out.println(simplified);
        } catch (Exception e) {
            System.err.println("Failed to parse source code: " + e.getMessage());
        }
    }


    private static String getSimplified(String src) {

        src = removeImports(src);
        Node cu;
        try {
            // Try parsing as a block statement
            cu = StaticJavaParser.parseBlock(src);
        } catch (Exception blockEx) {
            try {
                // If block parsing fails, try parsing as a full compilation unit
                cu = StaticJavaParser.parse(src);
            } catch (Exception unitEx) {
                System.out.println(src);
                throw new RuntimeException("Failed to parse input as either block or compilation unit.", unitEx);
            }
        }

        final int[] counter = {1};
        Map<String, String> identifierMap = new HashMap<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(SimpleName n, Void arg) {
                super.visit(n, arg);

                // Get the original identifier (the name of the variable, method, etc.)
                String originalName = n.getIdentifier();

                // Check if the identifier has already been encountered
                if (!identifierMap.containsKey(originalName)) {
                    // If it's the first occurrence, assign it a new VAR tag
                    identifierMap.put(originalName, "VAR_" + counter[0]);
                    counter[0]++;
                }

                // Replace the identifier with the tag from the map
                n.setIdentifier(identifierMap.get(originalName));
            }
        }, null);



        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(IntegerLiteralExpr n, Void arg) {
                super.visit(n, arg);
                // Replace integer literals with "LITERAL"
                n.replace(new IntegerLiteralExpr("INTEGER_LITERAL"));
            }

            @Override
            public void visit(StringLiteralExpr n, Void arg) {
                super.visit(n, arg);
                // Replace string literals with "LITERAL"
                n.replace(new StringLiteralExpr("STRING_LITERAL"));
            }

            @Override
            public void visit(CharLiteralExpr n, Void arg) {
                super.visit(n, arg);
                // Replace char literals with "LITERAL"
                n.replace(new CharLiteralExpr("CHARACTER_LITERAL"));
            }

            @Override
            public void visit(DoubleLiteralExpr n, Void arg) {
                super.visit(n, arg);
                // Replace double literals with "LITERAL"
                n.replace(new DoubleLiteralExpr("FLOATING_POINT_LITERAL"));
            }

            @Override
            public void visit(LongLiteralExpr n, Void arg) {
                super.visit(n, arg);
                // Replace long literals with "LITERAL"
                n.replace(new LongLiteralExpr("LONG_LITERAL"));
            }
        }, null);

        String simplified = cu.toString();
        simplified = removeWhiteSpaces(simplified);
        simplified = removeQuotes(simplified);
        return simplified;
    }
    
    // Method to remove imports from the Java source code
    private static String removeWhiteSpaces(String sourceCode) { 

        
        sourceCode = sourceCode.replaceAll("\\\\n", " ").replaceAll("\\\\t", " ").replaceAll("\\\\r", " ");
        //sourceCode = sourceCode.replaceAll("[\\n\\t\\r]+", " ");
        sourceCode = sourceCode.replaceAll("\\s{2,}", " ");
        // Remove all newlines and tabs
        return sourceCode.trim(); // Remove leading/trailing whitespace
    }

    private static String removeImports(String sourceCode) {
        sourceCode = sourceCode.replaceAll("(?m)^import\\s+.*?;\\s*", "");
        return sourceCode;
    }

    private static String removeQuotes(String sourceCode) {
        sourceCode = sourceCode.replaceAll("\'", "").replaceAll("\"", "");
        return sourceCode;
    }
}
