package client;

import java.net.*;
import java.io.*;

public class Client {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 6699;

        try (Socket clientSocket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
             BufferedReader brSockInp = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedReader brLocalInp = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to " + clientSocket);

            Thread listenerThread = new Thread(() -> {
                try {
                    String inputLine;
                    while ((inputLine = brSockInp.readLine()) != null) {
                        System.out.println("[Zwrot serwera]: " + inputLine);
                    }
                } catch (IOException e) {
                    System.out.println("Error reading from server: " + e);
                }
            });
            listenerThread.start();

            System.out.println("Mozesz wpisywac komendy.");
            String line;
            while ((line = brLocalInp.readLine()) != null) {
                if ("quit".equals(line)) {
                    System.out.println("Zamykanie...");
                    break;
                }
                out.writeBytes(line + "\r\n");
                out.flush();
            }

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(-1);
        }
    }
}
