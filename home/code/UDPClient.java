package home.code; 

import java.io.*;
import java.net.*;
import java.util.Base64;

public class UDPClient {
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_TIMEOUT = 1000; 

    public static void main(String[] args) 
    {
        if (args.length != 3) 
        {
            System.err.println("Usage: java home.code.UDPClient <serverIP> <serverPort> <files.txtPath>");
            System.exit(1);
        }

        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String filesListPath = args[2];

        try (DatagramSocket socket = new DatagramSocket()) 
        {
            InetAddress serverAddress = InetAddress.getByName(serverIP);
            File filesList = new File(filesListPath);

            if (!filesList.exists()) 
            {
                System.err.println("File list not found: " + filesListPath);
                System.exit(1);
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(filesList))) 
            {
                String filename;
                while ((filename = reader.readLine()) != null) 
                {
                    filename = filename.trim();
                    if (!filename.isEmpty()) 
                    {
                        downloadFile(socket, serverAddress, serverPort, filename);
                    }
                }
            }
        } catch (IOException e) 
        {
            e.printStackTrace();
        }
    }

    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, int serverPort, String filename) 
    {
        try {
            System.out.println("Starting to download file: " + filename);

            // Send DOWNLOAD request
            String downloadRequest = "DOWNLOAD " + filename;
            String response = sendAndReceive(socket, serverAddress, serverPort, downloadRequest);

            if (response == null) 
            {
                System.err.println("No response to download request");
                return;
            }
            String[] parts = response.split(" ");
            if (parts[0].equals("ERR")) 
            {
                System.out.println("File not found on server: " + filename);
                return;
            } else if (!parts[0].equals("OK")) 
            {
                System.err.println("Invalid response: " + response);
                return;
            }

            // Parse file size and data port
            int fileSize = 0, dataPort = 0;
            for (int i = 0; i < parts.length; i++) 
            {
                if (parts[i].equals("SIZE")) fileSize = Integer.parseInt(parts[i + 1]);
                if (parts[i].equals("PORT")) dataPort = Integer.parseInt(parts[i + 1]);
            }

            System.out.println("File size: " + fileSize + " bytes, Data port: " + dataPort);

            try (FileOutputStream fos = new FileOutputStream(filename);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) 
                 {

                int blockSize = 1000;
                int downloaded = 0;

                while (downloaded < fileSize) 
                {
                    int start = downloaded;
                    int end = Math.min(downloaded + blockSize - 1, fileSize - 1);

                    // Send block request
                    String fileRequest = "FILE " + filename + " GET START " + start + " END " + end;
                    response = sendAndReceive(socket, serverAddress, dataPort, fileRequest);

                    if (response == null) 
                    {
                        System.err.println("Block timeout: " + start + "-" + end);
                        break;
                    }

                    parts = response.split(" ");
                    if (!parts[0].equals("FILE") || !parts[2].equals("OK")) 
                    {
                        System.err.println("Invalid data response: " + response);
                        break;
                    }

                    // Extract Base64 data
                    int dataIndex = 0;
                    for (int i = 0; i < parts.length; i++) 
                    {
                        if (parts[i].equals("DATA")) 
                        {
                            dataIndex = i + 1;
                            break;
                        }
                    }

                    if (dataIndex == 0) 
                    {
                        System.err.println("No data in response: " + response);
                        break;
                    }

                    StringBuilder base64Data = new StringBuilder();
                    for (int i = dataIndex; i < parts.length; i++) 
                    {
                        base64Data.append(parts[i]).append(" ");
                    }

                    byte[] data = Base64.getDecoder().decode(base64Data.toString().trim());
                    bos.write(data);
                    downloaded += data.length;

                    System.out.print("*"); // Show progress
                }
