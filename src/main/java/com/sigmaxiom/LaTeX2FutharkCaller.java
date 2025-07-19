package com.sigmaxiom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LaTeX2FutharkCaller {
    private String venvPath;
    private String scriptPath;
    
    
    public LaTeX2FutharkCaller(String venvPath, String scriptPath) {
        this.venvPath = venvPath;
        this.scriptPath = scriptPath;
    }
    
    
    public TranspilationResult transpileViaTempFile(String latexExpr, boolean generateProgram) throws IOException {
        
        Path tempFile = Files.createTempFile("latex2futhark_", ".tex");
        
        try {
            
            Files.write(tempFile, latexExpr.getBytes(StandardCharsets.UTF_8));
            
            
            List<String> command = new ArrayList<>();
            
            
            
            
            String pythonPath = venvPath + "/bin/python3";  

            command.add(pythonPath);
            command.add(scriptPath);
            command.add("--file");
            command.add(tempFile.toString());
            if (generateProgram) {
                command.add("--program");
            }
            
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("Warning: Process exited with code " + exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Process was interrupted", e);
            }
            
            
            return parseOutput(output.toString(), latexExpr);
            
        } finally {
            
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete temporary file: " + tempFile);
            }
        }
    }
    
    
    private TranspilationResult parseOutput(String output, String latexExpr) throws IOException {
        String[] lines = output.split("\n");
        
        
        StringBuilder programCode = new StringBuilder();
        List<String> variables = new ArrayList<>();
        Map<String, String> variableTypes = new HashMap<>();
        boolean inFutharkProgramBlock = false;
        
        for (String line : lines) {
            if (line.startsWith("-- Auto-generated from LaTeX:")) {
                inFutharkProgramBlock = true;
            }

            if (inFutharkProgramBlock) {
                programCode.append(line).append("\n");
            }

            if (line.startsWith("Variables found:")) {
                
                String varsStr = line.substring("Variables found:".length()).trim();
                if (varsStr.startsWith("[") && varsStr.endsWith("]")) {
                    varsStr = varsStr.substring(1, varsStr.length() - 1);
                    for (String var : varsStr.split(",")) {
                        var = var.trim().replaceAll("'", "");
                        if (!var.isEmpty()) {
                            variables.add(var);
                        }
                    }
                }
            } else if (line.trim().endsWith(": f64") || line.trim().endsWith(": f32")) {
                
                String[] parts = line.trim().split(":");
                if (parts.length == 2) {
                    variableTypes.put(parts[0].trim(), parts[1].trim());
                }
            }
        
        if (line.startsWith("ERROR:") || line.contains("TRANSPILATION FAILED:")) {
            throw new IOException("No valid output found in transpiler result");
        }
    }
        
        return new TranspilationResult(
            latexExpr,
            "",  
            programCode.toString().trim(),
            variables,
            variableTypes
        );
    }
    
    
    public class TranspilationResult {
        private final String latexExpr;
        private final String futharkCode;
        private final String completeProgram;
        private final List<String> variables;
        private final Map<String, String> variableTypes;
        
        public TranspilationResult(String latexExpr, String futharkCode, 
                                  String completeProgram, List<String> variables,
                                  Map<String, String> variableTypes) {
            this.latexExpr = latexExpr;
            this.futharkCode = futharkCode;
            this.completeProgram = completeProgram;
            this.variables = variables;
            this.variableTypes = variableTypes;
        }
        
        public String getLatexExpr() {
            return latexExpr;
        }
        
        public String getFutharkCode() {
            return futharkCode;
        }
        
        public String getCompleteProgram() {
            return completeProgram.isEmpty() ? futharkCode : completeProgram;
        }
        
        public List<String> getVariables() {
            return variables;
        }
        
        public Map<String, String> getVariableTypes() {
            return variableTypes;
        }
    }
}
