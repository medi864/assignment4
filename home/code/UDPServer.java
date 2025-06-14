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
