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

