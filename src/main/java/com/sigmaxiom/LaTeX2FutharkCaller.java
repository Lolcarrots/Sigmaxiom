package com.sigmaxiom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LaTeX2FutharkCaller {
    private String venvPath;
    private String scriptPath;
    
    
    public LaTeX2FutharkCaller(String venvPath, String scriptPath) {
        this.venvPath = venvPath;
        this.scriptPath = scriptPath;
    }
    
    
    public TranspilationResult transpile(String latexExpr) throws IOException {
            
            
        List<String> command = new ArrayList<>();

        String pythonPath = venvPath + "/bin/python3";  

        command.add(pythonPath);
        command.add(scriptPath);
        command.add(latexExpr);
            
            
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
    }
    
    
    private TranspilationResult parseOutput(String output, String latexExpr) throws IOException {
        String[] lines = output.split("\n");
        
        
        StringBuilder programCode = new StringBuilder();
        boolean inFutharkProgramBlock = false;
        
        for (String line : lines) {
            if (line.startsWith("-- Auto-generated Futhark code")) {
                inFutharkProgramBlock = true;
            }

            if (inFutharkProgramBlock) {
                programCode.append(line).append("\n");
            }
        
        if (line.startsWith("ERROR:") || line.contains("TRANSPILATION FAILED:")) {
            throw new IOException("No valid output found in transpiler result");
        }
    }
        
        return new TranspilationResult(
            latexExpr,
            programCode.toString().trim());
    }
    
    
    public class TranspilationResult {
        private final String latexExpr;
        private final String completeProgram;
        
        public TranspilationResult(String latexExpr, String completeProgram) {
            this.latexExpr = latexExpr;
            this.completeProgram = completeProgram;
        }
        
        public String getLatexExpr() {
            return latexExpr;
        }
        
        
        public String getCompleteProgram() {
            return completeProgram;
        }
    }
}
