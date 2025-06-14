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
