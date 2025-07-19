package com.sigmaxiom;

import org.json.JSONArray;
import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.io.InputStream;
import java.io.IOException;

interface KernelStatusListener {
    void onStatusChange(String status);
}

public class JupyterKernelClient {
    private ZContext context;
    private ZMQ.Socket shellSocket;
    private ZMQ.Socket iopubSocket;
    private String key;
    private String signatureScheme;
    private Process kernelProcess;
    private String connectionFilePath;
    private Long kernelPid;

    private KernelStatusListener statusListener; 

    
    public void setStatusListener(KernelStatusListener listener) {
        this.statusListener = listener;
    }
    
    public JupyterKernelClient(String connectionFilePath) throws Exception {
        this.connectionFilePath = connectionFilePath;
        System.out.println("Initializing JupyterKernelClient with connection file: " + connectionFilePath);
        
        
        String connectionInfo = new String(Files.readAllBytes(Paths.get(connectionFilePath)));
        JSONObject connInfo = new JSONObject(connectionInfo);
        
        System.out.println("Connection info: " + connectionInfo);
        
        
        String transport = connInfo.getString("transport");
        String ip = connInfo.getString("ip");
        int shellPort = connInfo.getInt("shell_port");
        int iopubPort = connInfo.getInt("iopub_port");
        this.key = connInfo.getString("key");
        this.signatureScheme = connInfo.getString("signature_scheme");
        
        System.out.println("Connecting to " + transport + "://" + ip + ":" + shellPort + " for shell socket");
        System.out.println("Connecting to " + transport + "://" + ip + ":" + iopubPort + " for iopub socket");
        
        
        context = new ZContext();
        
        
        shellSocket = context.createSocket(SocketType.DEALER);
        shellSocket.connect(transport + "://" + ip + ":" + shellPort);
        
        
        iopubSocket = context.createSocket(SocketType.SUB);
        iopubSocket.connect(transport + "://" + ip + ":" + iopubPort);
        iopubSocket.subscribe(ZMQ.SUBSCRIPTION_ALL); 
        
        System.out.println("JupyterKernelClient initialized successfully");
    }
    
    
    private String createSignature(String headerStr, String parentHeaderStr, String metadataStr, String contentStr) {
        try {
            if (this.signatureScheme.equals("hmac-sha256")) {
                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec keySpec = new SecretKeySpec(this.key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                mac.init(keySpec);
                
                mac.update(headerStr.getBytes(StandardCharsets.UTF_8));
                mac.update(parentHeaderStr.getBytes(StandardCharsets.UTF_8));
                mac.update(metadataStr.getBytes(StandardCharsets.UTF_8));
                mac.update(contentStr.getBytes(StandardCharsets.UTF_8));
                
                byte[] digest = mac.doFinal();
                return bytesToHex(digest);
            } else {
                System.out.println("Unsupported signature scheme: " + this.signatureScheme);
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    public Long getKernelPid() {
        return kernelPid;
    }

    
    public void setKernelPid(Long pid) {
        this.kernelPid = pid;
    }
    public boolean interruptKernel() {
        System.out.println("Attempting to interrupt kernel with PID: " + kernelPid);
        if (kernelPid != null) {
            try {
                
                ProcessBuilder pb = new ProcessBuilder("kill", "-2", kernelPid.toString());
                pb.redirectError(ProcessBuilder.Redirect.INHERIT); 
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT); 
                Process process = pb.start();
                int exitCode = process.waitFor();
                System.out.println("Interrupt kernel command exited with code: " + exitCode);
                return exitCode == 0;
            } catch (Exception e) {
                System.err.println("Error interrupting kernel: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            System.err.println("Cannot interrupt kernel: No PID available");
            return false;
        }
    }

    
    private static final ExecutorService KERNEL_EXECUTOR = 
        Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Kernel-Communication-Thread");
            t.setDaemon(true);  
            return t;
        });

    private void clearPendingMessages() {
        int maxClear = 2000; 
        try {
            for (int i = 0; i < maxClear; i++) {
                if (iopubSocket.recv(ZMQ.DONTWAIT) == null) {
                    break; 
                }
                
                while (iopubSocket.hasReceiveMore()) {
                    iopubSocket.recv(ZMQ.DONTWAIT);
                }
            }
        } catch (Exception e) {
            
        }
    }

    public CompletableFuture<String> executeCode(String code) {
        clearPendingMessages();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String msgId = UUID.randomUUID().toString();
                
                
                JSONObject header = new JSONObject();
                header.put("msg_id", msgId);
                header.put("username", "user");
                header.put("session", UUID.randomUUID().toString());
                header.put("msg_type", "execute_request");
                header.put("version", "5.0");
                String headerStr = header.toString();
                String parentHeaderStr = "{}";
                String metadataStr = "{}";
                JSONObject content = new JSONObject();
                content.put("code", code);
                content.put("silent", false);
                content.put("store_history", true);
                content.put("user_expressions", new JSONObject());
                content.put("allow_stdin", false);
                String contentStr = content.toString();
                String signature = createSignature(headerStr, parentHeaderStr, metadataStr, contentStr);

                
                shellSocket.sendMore("<IDS|MSG>".getBytes(StandardCharsets.UTF_8));
                shellSocket.sendMore(signature.getBytes(StandardCharsets.UTF_8));
                shellSocket.sendMore(headerStr.getBytes(StandardCharsets.UTF_8));
                shellSocket.sendMore(parentHeaderStr.getBytes(StandardCharsets.UTF_8));
                shellSocket.sendMore(metadataStr.getBytes(StandardCharsets.UTF_8));
                shellSocket.send(contentStr.getBytes(StandardCharsets.UTF_8));

                
                StringBuilder output = new StringBuilder();
                boolean done = false;
                long startTime = System.currentTimeMillis();
                long warningTimeout = 30000; 
                boolean warningShown = false;

                
                while (shellSocket.hasReceiveMore()) { shellSocket.recv(); } 
                List<byte[]> shellReply = new ArrayList<>();
                for(int i=0; i<6; i++) { 
                    shellReply.add(shellSocket.recv());
                }

                
                ZMQ.Poller poller = context.createPoller(1);
                poller.register(iopubSocket, ZMQ.Poller.POLLIN);

                while (!done) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    
                    
                    if (!warningShown && elapsed > warningTimeout) {
                        output.append("\n[Warning: Execution is taking longer than expected (>")
                            .append(warningTimeout/1000)
                            .append(" seconds). Still running...]\n\n");
                        warningShown = true;
                    }
                    
                    if (poller.poll(200) > 0) { 
                        String result = processIoPubMessage(iopubSocket, msgId);
                        if ("EXECUTION_COMPLETE".equals(result)) {
                            done = true; 
                        } else if (result != null && !result.isEmpty()) {
                            output.append(result).append("\n");
                        }
                    }
                }
                
                return output.toString().trim();
            } catch (Exception e) {
                e.printStackTrace();
                return "Error: " + e.getMessage();
            }
        }, KERNEL_EXECUTOR);
    }
    
    private String processIoPubMessage(ZMQ.Socket socket, String currentMsgId) {
        try {
            
            byte[] identity = socket.recv(0); 
            String delimiter = socket.recvStr(); 
            if (!"<IDS|MSG>".equals(delimiter)) return "";

            String signature = socket.recvStr(); 
            String headerStr = socket.recvStr(); 
            JSONObject header = new JSONObject(headerStr);
            String msgType = header.getString("msg_type");
            String parentHeaderStr = socket.recvStr(); 
            JSONObject parentHeader = new JSONObject(parentHeaderStr);
            
            
            if (!currentMsgId.equals(parentHeader.optString("msg_id"))) {
                while (socket.hasReceiveMore()) { socket.recv(0); } 
                return null;
            }
            
            String metadataStr = socket.recvStr(); 
            String contentStr = socket.recvStr(); 
            JSONObject content = new JSONObject(contentStr);

            
            switch (msgType) {
                case "stream":
                    return content.getString("text");
                case "execute_result":
                case "display_data":
                    JSONObject data = content.getJSONObject("data");
                    if (data.has("text/plain")) return data.getString("text/plain");
                    if (data.has("text/html")) return data.getString("text/html");
                    return data.toString(); 
                case "error":
                    JSONArray tracebackArr = content.getJSONArray("traceback");
                    StringBuilder tracebackBuilder = new StringBuilder();
                    for (int i = 0; i < tracebackArr.length(); i++) {
                        tracebackBuilder.append(tracebackArr.getString(i).replaceAll("\u001B\\[[;\\d]*m", "")).append("\n");
                    }
                    return tracebackBuilder.toString();
                case "status":
                    String state = content.getString("execution_state");
                    if (statusListener != null) {
                        statusListener.onStatusChange(state);
                    }
                    if ("idle".equals(state)) {
                        return "EXECUTION_COMPLETE";
                    }
                    return null; 
                default:
                    return null; 
            }
        } catch (Exception e) {
            System.err.println("Error processing iopub message: " + e.getMessage());
            return null; 
        }
    }
    
    
    public static Map<String, KernelSpec> discoverKernels(String envPath) {
        Map<String, KernelSpec> kernels = new HashMap<>();
        
        
        if (envPath == null || envPath.trim().isEmpty()) {
            return kernels;
        }
        
        try {
            
            String jupyterPath = new File(envPath, "bin/jupyter").getAbsolutePath();

            
            if (!new File(jupyterPath).exists()) {
                
                return kernels;
            }

            ProcessBuilder pb = new ProcessBuilder(
                jupyterPath, "kernelspec", "list", "--json"
            );
            
            
            Map<String, String> env = pb.environment();
            env.put("VIRTUAL_ENV", envPath); 
            
            env.put("PATH", new File(envPath, "bin").getAbsolutePath() + File.pathSeparator + env.get("PATH"));

            Process process = pb.start();
            
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorOutput = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line);
                }
                throw new IOException("'jupyter kernelspec' command failed with exit code " + exitCode + ": " + errorOutput);
            }

            
            if (output.length() > 0) {
                JSONObject result = new JSONObject(output.toString());
                JSONObject kernelspecs = result.getJSONObject("kernelspecs");
                
                
                for (String kernelName : kernelspecs.keySet()) {
                    JSONObject spec = kernelspecs.getJSONObject(kernelName);
                    String displayName = spec.getJSONObject("spec").getString("display_name");
                    String resourceDir = spec.getString("resource_dir");
                    
                    KernelSpec kernelSpec = new KernelSpec(kernelName, displayName, resourceDir);
                    kernels.put(kernelName, kernelSpec);
                }
            }
        } catch (Exception e) {
            System.err.println("Could not discover kernels in " + envPath + ": " + e.getMessage());
        }
        
        return kernels;
    }
    
    
    
    public static JupyterKernelClient startPythonKernelDirectly(String envPath) throws Exception {
        System.out.println("Starting Python kernel directly via shell script with environment: " + envPath);
        
        
        String scriptPath = findStartKernelScript();
        if (scriptPath == null) {
            throw new Exception("Could not find start_kernel.sh script");
        }
        
        System.out.println("Using start_kernel.sh script at: " + scriptPath);
        
        
        ProcessBuilder pb = new ProcessBuilder(scriptPath, envPath);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = pb.start();
        
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        String connectionFile = null;
        Long kernelPid = null;
        
        while ((line = reader.readLine()) != null) {
            System.out.println("SCRIPT OUTPUT: " + line);
            
            if (line.startsWith("KERNEL_CONNECTION_FILE=")) {
                connectionFile = line.substring("KERNEL_CONNECTION_FILE=".length());
            }
            if (line.startsWith("KERNEL_PID=")) {
                try {
                    kernelPid = Long.parseLong(line.substring("KERNEL_PID=".length()));
                    System.out.println("Captured kernel PID: " + kernelPid);
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse kernel PID: " + e.getMessage());
                }
            }
        }
        
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Script failed with exit code " + exitCode);
        }
        
        if (connectionFile == null) {
            throw new Exception("Script did not output a connection file path");
        }
        
        
        JupyterKernelClient newClient = new JupyterKernelClient(connectionFile);
        
        
        if (kernelPid != null) {
            newClient.kernelPid = kernelPid;
            System.out.println("Set kernel PID in client: " + kernelPid);
        } else {
            System.out.println("WARNING: Failed to capture kernel PID from script output");
        }
        
        
        newClient.waitForKernelReady();
        
        return newClient;
    }

    private static String findStartKernelScript() {
        
        
        
        try {
            
            String classPath = JupyterKernelClient.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
            File classDir = new File(classPath).getParentFile();
            
            
            String[] possiblePaths = {
                "start_kernel.sh",
                "com/example/start_kernel.sh",
                "../start_kernel.sh",
                "../../start_kernel.sh",
                "src/main/java/com/example/start_kernel.sh"
            };
            
            for (String path : possiblePaths) {
                File scriptFile = new File(classDir, path);
                if (scriptFile.exists() && scriptFile.canExecute()) {
                    return scriptFile.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding script relative to class: " + e.getMessage());
        }
        
        
        try {
            String workingDir = System.getProperty("user.dir");
            String[] possiblePaths = {
                "start_kernel.sh",
                "src/main/java/com/example/start_kernel.sh",
                "com/example/start_kernel.sh"
            };
            
            for (String path : possiblePaths) {
                File scriptFile = new File(workingDir, path);
                if (scriptFile.exists() && scriptFile.canExecute()) {
                    return scriptFile.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding script relative to working directory: " + e.getMessage());
        }
        
        
        try {
            URL resource = JupyterKernelClient.class.getResource("/com/example/start_kernel.sh");
            if (resource == null) {
                resource = JupyterKernelClient.class.getResource("/start_kernel.sh");
            }
            if (resource == null) {
                resource = JupyterKernelClient.class.getResource("start_kernel.sh");
            }
            
            if (resource != null) {
                
                File tempScript = File.createTempFile("start_kernel", ".sh");
                tempScript.deleteOnExit();
                
                try (InputStream in = resource.openStream();
                    java.io.FileOutputStream out = new java.io.FileOutputStream(tempScript)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                
                
                tempScript.setExecutable(true);
                return tempScript.getAbsolutePath();
            }
        } catch (Exception e) {
            System.err.println("Error finding script as resource: " + e.getMessage());
        }
        
        return null;
    }

    
    public static JupyterKernelClient startKernel(String kernelName, String envPath) throws Exception {
        System.out.println("Starting kernel: " + kernelName);
        
        String connectionFile = "kernel_" + UUID.randomUUID().toString() + ".json";
        
        try {
            
            String jupyterPath = new File(envPath, "bin/jupyter").getAbsolutePath();

            ProcessBuilder pb = new ProcessBuilder(
                jupyterPath, "kernel",
                "--kernel=" + kernelName,
                "--KernelManager.connection_file=" + connectionFile
            );
            
            
            Map<String, String> env = pb.environment();
            env.put("VIRTUAL_ENV", envPath);
            env.put("PATH", new File(envPath, "bin").getAbsolutePath() + File.pathSeparator + env.get("PATH"));
            env.remove("PYTHONHOME"); 
            env.remove("PYTHONPATH");
            
            System.out.println("Using PATH: " + env.get("PATH"));
            
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            
            Process process = pb.start();
            
            
            Long pid = null;
            try {
                Method pidMethod = Process.class.getMethod("pid");
                pid = (Long) pidMethod.invoke(process);
                System.out.println("Kernel started with PID: " + pid);
            } catch (Exception e) {
                try {
                    Field pidField = process.getClass().getDeclaredField("pid");
                    pidField.setAccessible(true);
                    pid = pidField.getLong(process);
                    System.out.println("Kernel started with PID (via reflection): " + pid);
                } catch (Exception ex) {
                    System.out.println("Could not get process PID: " + ex.getMessage());
                }
            }
            
            System.out.println("Waiting for connection file to be created: " + connectionFile);
            long startTime = System.currentTimeMillis();
            while (!Files.exists(Paths.get(connectionFile)) && System.currentTimeMillis() - startTime < 10000) {
                Thread.sleep(100);
            }
            
            if (!Files.exists(Paths.get(connectionFile))) {
                
                if (!process.isAlive()) {
                    throw new Exception("Kernel process exited prematurely with code " + process.exitValue() + ". Check console for errors.");
                }
                throw new Exception("Failed to create connection file within timeout");
            }
            
            JupyterKernelClient newClient = new JupyterKernelClient(connectionFile);
            if (pid != null) {
                newClient.kernelPid = pid;
                System.out.println("Set kernel PID in client: " + pid);
            }

            newClient.kernelProcess = process;
            newClient.waitForKernelReady();
            return newClient;

        } catch (Exception e) {
            System.err.println("Error starting kernel: " + e.getMessage());
            System.err.println("Please ensure Jupyter is installed and in your system's PATH.");
            e.printStackTrace();
            throw e;
        }
    }

    private void waitForKernelReady() throws Exception {
        System.out.println("Checking if kernel is ready...");
        String msgId = UUID.randomUUID().toString();

        
        JSONObject header = new JSONObject()
            .put("msg_id", msgId)
            .put("username", "user")
            .put("session", UUID.randomUUID().toString())
            .put("msg_type", "kernel_info_request")
            .put("version", "5.0");
        String headerStr = header.toString();
        String parentHeaderStr = "{}";
        String metadataStr = "{}";
        String contentStr = "{}";
        String signature = createSignature(headerStr, parentHeaderStr, metadataStr, contentStr);

        
        shellSocket.sendMore("<IDS|MSG>".getBytes(StandardCharsets.UTF_8));
        shellSocket.sendMore(signature.getBytes(StandardCharsets.UTF_8));
        shellSocket.sendMore(headerStr.getBytes(StandardCharsets.UTF_8));
        shellSocket.sendMore(parentHeaderStr.getBytes(StandardCharsets.UTF_8));
        shellSocket.sendMore(metadataStr.getBytes(StandardCharsets.UTF_8));
        shellSocket.send(contentStr.getBytes(StandardCharsets.UTF_8));

        
        ZMQ.Poller poller = context.createPoller(1);
        poller.register(shellSocket, ZMQ.Poller.POLLIN);
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5000) { 
            if (poller.poll(100) > 0 && poller.pollin(0)) {
                
                List<byte[]> reply = new ArrayList<>();
                while (true) {
                    reply.add(shellSocket.recv(0));
                    if (!shellSocket.hasReceiveMore()) break;
                }
                
                JSONObject parentHeaderReply = new JSONObject(new String(reply.get(3), StandardCharsets.UTF_8));
                if (msgId.equals(parentHeaderReply.optString("msg_id"))) {
                    System.out.println("Kernel is ready (received kernel_info_reply).");
                    return; 
                }
            }
        }
        throw new Exception("Kernel did not become ready within the timeout period.");
    }
    
    public static class KernelSpec {
        private String name;
        private String displayName;
        private String resourceDir;
        
        public KernelSpec(String name, String displayName, String resourceDir) {
            this.name = name;
            this.displayName = displayName;
            this.resourceDir = resourceDir;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getResourceDir() {
            return resourceDir;
        }
        
        @Override
        public String toString() {
            return displayName + " (" + name + ")";
        }
    }
    
    

    public void close() {
        System.out.println("Closing JupyterKernelClient...");
        long startTime = System.currentTimeMillis();

        
        try {
            if (shellSocket != null) {
                shellSocket.setLinger(0); 
                shellSocket.close();
                System.out.println("Shell socket closed.");
            }
            if (iopubSocket != null) {
                iopubSocket.setLinger(0);
                iopubSocket.close();
                System.out.println("IOPub socket closed.");
            }
            if (context != null) {
                context.close(); 
                System.out.println("ZMQ context closed.");
            }
        } catch (Exception e) {
            System.err.println("Error closing ZMQ resources: " + e.getMessage());
            
        }

        
        boolean processTerminated = false;
        if (kernelProcess != null && kernelProcess.isAlive()) {
            System.out.println("Attempting to terminate kernel process (PID likely: " + kernelPid + ")");
            try {
                
                kernelProcess.destroy();
                
                if (kernelProcess.waitFor(2, TimeUnit.SECONDS)) {
                    System.out.println("Kernel process terminated gracefully (exit code: " + kernelProcess.exitValue() + ").");
                    processTerminated = true;
                } else {
                    
                    System.out.println("Kernel process did not terminate gracefully, forcing termination...");
                    kernelProcess.destroyForcibly();
                    if (kernelProcess.waitFor(1, TimeUnit.SECONDS)) {
                        System.out.println("Kernel process terminated forcefully.");
                        processTerminated = true;
                    } else {
                        System.err.println("Failed to terminate kernel process even forcefully.");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for kernel process termination.");
                
                if (kernelProcess.isAlive()) {
                    kernelProcess.destroyForcibly();
                }
            } catch (Exception e) {
                System.err.println("Error terminating kernel process: " + e.getMessage());
            }
        } else if (kernelPid != null && !processTerminated) {
            
            System.out.println("Attempting to kill kernel process using PID: " + kernelPid);
            try {
                
                Process killTerm = new ProcessBuilder("kill", kernelPid.toString()).start();
                if (killTerm.waitFor(1, TimeUnit.SECONDS)) {
                    System.out.println("Kernel process (PID " + kernelPid + ") killed via SIGTERM.");
                    processTerminated = true;
                } else {
                    killTerm.destroy(); 
                    
                    System.out.println("Kernel process (PID " + kernelPid + ") did not respond to SIGTERM, sending SIGKILL...");
                    Process killKill = new ProcessBuilder("kill", "-9", kernelPid.toString()).start();
                    if (killKill.waitFor(1, TimeUnit.SECONDS)) {
                        System.out.println("Kernel process (PID " + kernelPid + ") killed via SIGKILL.");
                        processTerminated = true;
                    } else {
                        killKill.destroy();
                        System.err.println("Failed to kill kernel process (PID " + kernelPid + ") even with SIGKILL.");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error killing kernel process via PID: " + e.getMessage());
            }
        } else {
            System.out.println("No active kernel process object or PID to terminate.");
            processTerminated = true; 
        }


        
        if (connectionFilePath != null) {
            try {
                File connectionFile = new File(connectionFilePath);
                if (connectionFile.exists()) {
                    
                    Thread.sleep(100);
                    if (Files.deleteIfExists(Paths.get(connectionFilePath))) {
                        System.out.println("Successfully deleted connection file: " + connectionFilePath);
                    } else {
                        
                        Thread.sleep(200);
                        if (Files.deleteIfExists(Paths.get(connectionFilePath))) {
                            System.out.println("Successfully deleted connection file (on second attempt): " + connectionFilePath);
                        } else {
                            System.err.println("Failed to delete connection file: " + connectionFilePath + ". It might be locked or the kernel didn't fully exit.");
                        }
                    }
                } else {
                    System.out.println("Connection file already removed: " + connectionFilePath);
                }
            } catch (Exception e) {
                System.err.println("Error deleting connection file: " + e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("JupyterKernelClient closed in " + duration + " ms.");
    }
}

