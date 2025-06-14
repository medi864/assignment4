package home.code; 

import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.Random;

public class UDPServer {
    private static final int DATA_PORT_MIN = 50000;
    private static final int DATA_PORT_MAX = 51000;
    private static final Random random = new Random();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java home.code.UDPServer <serverPort>");
            System.exit(1);
        }

        int serverPort = Integer.parseInt(args[0]);

        try (DatagramSocket welcomeSocket = new DatagramSocket(serverPort)) {
            System.out.println("Server started, port: " + serverPort);

            while (true) {
                // Receive client request
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                welcomeSocket.receive(receivePacket);

                String request = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                System.out.println("Received request: " + request);

                // Handle request in a new thread
                new Thread(() -> handleClientRequest(welcomeSocket, receivePacket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClientRequest(DatagramSocket welcomeSocket, DatagramPacket receivePacket) {
        String request = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
        String[] parts = request.split(" ");

        if (parts.length < 2 || !parts[0].equals("DOWNLOAD")) {
            System.out.println("Invalid request: " + request);
            sendResponse(welcomeSocket, receivePacket, "ERR INVALID_REQUEST");
            return;
        }

        String filename = parts[1];
        // Note the path! Server files are in home/server_files directory
        File file = new File("home/server_files/" + filename); 

        if (!file.exists()) {
            sendResponse(welcomeSocket, receivePacket, "ERR " + filename + " NOT_FOUND");
            return;
        }

        try {
            InetAddress clientAddress = receivePacket.getAddress();
            int clientPort = receivePacket.getPort();
            int dataPort = allocateDataPort();

            // Send OK response (with file size and data port)
            long fileSize = file.length();
            String okResponse = "OK " + filename + " SIZE " + fileSize + " PORT " + dataPort;
            sendResponse(welcomeSocket, clientAddress, clientPort, okResponse);

            System.out.println("Preparing to send file: " + filename + ", Size: " + fileSize + ", Data port: " + dataPort);

            // Start data transfer thread
            new Thread(() -> handleDataTransfer(dataPort, file, clientAddress, clientPort)).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void handleDataTransfer(int dataPort, File file, InetAddress clientAddress, int clientPort) {
        try (DatagramSocket dataSocket = new DatagramSocket(dataPort)) {
            System.out.println("Data port started: " + dataPort);

            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {

                while (true) {
                    // Receive client data request
                    byte[] receiveData = new byte[4096];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    dataSocket.receive(receivePacket);

                    String request = new String(receiveData, 0, receivePacket.getLength()).trim();
                    System.out.println("Received data request: " + request);

                    String[] parts = request.split(" ");

                    // Simplified CLOSE request handling
                    if (parts.length >= 3 && parts[0].equals("FILE") && parts[2].equals("CLOSE")) {
                        String closeResponse = "FILE " + file.getName() + " CLOSE_OK";
                        sendResponse(dataSocket, clientAddress, clientPort, closeResponse);
                        System.out.println("File transfer closed: " + file.getName());
                        break;
                    }

                    if (parts.length < 6 || !parts[0].equals("FILE")) {
                        System.out.println("Invalid data request: " + request);
                        continue;
                    }

                    String requestFilename = parts[1];
                    if (!requestFilename.equals(file.getName())) {
                        System.out.println("Filename mismatch: " + requestFilename);
                        continue;
                    }

                    if (parts[2].equals("GET")) {
                        int start = Integer.parseInt(parts[4]);
                        int end = Integer.parseInt(parts[6]);

                        if (start < 0 || end < start || end >= file.length()) {
                            System.out.println("Invalid range: " + start + "-" + end);
                            continue;
                        }

                        int length = end - start + 1;
                        byte[] data = new byte[length];

                        bis.skip(start);
                        int bytesRead = bis.read(data, 0, length);

                        if (bytesRead == length) {
                            // Base64 encode
                            String base64Data = Base64.getEncoder().encodeToString(data);
                            String response = "FILE " + file.getName() + " OK START " + start + " END " + end + " DATA " + base64Data;
                            sendResponse(dataSocket, clientAddress, clientPort, response);
                        } else {
                            System.out.println("Read failed: " + bytesRead + "/" + length);
                        }
                    } 
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


