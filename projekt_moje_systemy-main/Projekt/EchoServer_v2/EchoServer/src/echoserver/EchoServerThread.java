package echoserver;

import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EchoServerThread implements Runnable {
    private Socket socket;
    private CopyOnWriteArrayList<ArrayList<String>> logins;
    private int userIndex = -1;

    public EchoServerThread(Socket clientSocket, CopyOnWriteArrayList<ArrayList<String>> logins) {
        this.socket = clientSocket;
        this.logins = logins;
    }

    @Override
    public void run() {
        try (BufferedReader brinp = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String threadName = Thread.currentThread().getName();
            String line;

            while ((line = brinp.readLine()) != null) {
                System.out.println(threadName + "| Line read: " + line);

                switch (line) {
                    case "login":
                        funLogin(brinp, out, threadName);
                        break;
                    case "register":
                        funRejestracja(brinp, out, threadName);
                        break;
                    case "list":
                        funPokazUzytk(out);
                        break;
                    case "deposit":
                        funDepozyt(brinp, out);
                        break;
                    case "withdraw":
                        funWyplata(brinp, out);
                        break;
                    case "transfer":
                        funTransfer(brinp, out);
                        break;
                    case "balance":
                        funBalans(out);
                        break;
                    default:
                        out.writeBytes("Nieznana komenda.\r\n");
                        System.out.println(threadName + "| Nieznana komenda: " + line);
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Blad, " + e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Blad zamykania socket'u: " + e);
            }
        }
    }

    private void funLogin(BufferedReader brinp, DataOutputStream out, String threadName) throws IOException {
        boolean loginPass = false;
        out.writeBytes("Login i haslo?\r\n");
        while (!loginPass) {
            String credentialsLine = brinp.readLine();
            if (credentialsLine == null) return;
            String[] credentials = credentialsLine.split(" ");
            for (int i = 0; i < logins.size(); i++) {
                if (logins.get(i).get(0).equals(credentials[0]) && logins.get(i).get(1).equals(credentials[1])) {
                    userIndex = i;
                    loginPass = true;
                    out.writeBytes("Poprawne dane, zalogowano pomyslnie. Twoje saldo: " + logins.get(i).get(3) + "\r\n");
                    break;
                }
            }
            if (!loginPass) {
                out.writeBytes("Niepoprawne dane. Sprobuj ponownie.\r\n");
            }
        }
    }

    private void funRejestracja(BufferedReader brinp, DataOutputStream out, String threadName) throws IOException {
        out.writeBytes("Login, haslo, nazwa uzytkownika?\r\n");
        String line = brinp.readLine();
        if (line == null) return;
        String[] parts = line.split(" ");
        for (ArrayList<String> userData : logins) {
            if (userData.get(0).equals(parts[0])) {
                out.writeBytes("Login obecnie w użyciu, wybierz inny.\r\n");
                return;
            }
        }
        ArrayList<String> newUser = new ArrayList<>();
        newUser.add(parts[0]);
        newUser.add(parts[1]);
        newUser.add(parts[2]);
        newUser.add("0.00");
        logins.add(newUser);
        zapiszDoBazy();
        out.writeBytes("Użytkownik zarejestrowany pomyslnie.\r\n");
    }

    private void funPokazUzytk(DataOutputStream out) throws IOException {
        StringBuilder userList = new StringBuilder();
        for (ArrayList<String> userData : logins) {
            userList.append(userData.get(2)).append("\r\n");
        }
        out.writeBytes(userList.toString());
        out.flush();
    }

    private void funDepozyt(BufferedReader brinp, DataOutputStream out) throws IOException {
        if (userIndex == -1) {
            out.write("Prosze najpierw sie zalogowac.\r\n".getBytes("UTF-8"));
            return;
        }
        out.write("Podaj kwote depozytu:\r\n".getBytes("UTF-8"));
        String amountStr = brinp.readLine();
        try {
            double amount = Double.parseDouble(amountStr);
            double currentBalance = Double.parseDouble(logins.get(userIndex).get(3));
            currentBalance += amount;
            logins.get(userIndex).set(3, String.format("%.2f", currentBalance));
            zapiszDoBazy();
            out.write(("Nowe saldo: " + String.format("%.2f", currentBalance) + "\r\n").getBytes("UTF-8"));
        } catch (NumberFormatException e) {
            out.write("Niepoprawny format liczby.\r\n".getBytes("UTF-8"));
        }
    }

    private void funTransfer(BufferedReader brinp, DataOutputStream out) throws IOException {
        if (userIndex == -1) {
            out.write("Prosze sie najpierw zalogowac.\r\n".getBytes("UTF-8"));
            return;
        }
        out.write("Podaj login odbiorcy oraz kwote transferu: \r\n".getBytes("UTF-8"));
        String line = brinp.readLine();
        if (line == null) return;

        String[] transferDetails = line.split(" ");
        if (transferDetails.length < 2) {
            out.write("Invalid transfer details.\r\n".getBytes("UTF-8"));
            return;
        }

        String recipientLogin = transferDetails[0];
        double amount;
        try {
            amount = Double.parseDouble(transferDetails[1]);
        } catch (NumberFormatException e) {
            out.write("Zly format liczby.\r\n".getBytes("UTF-8"));
            return;
        }

        int recipientIndex = -1;
        for (int i = 0; i < logins.size(); i++) {
            if (logins.get(i).get(0).equals(recipientLogin)) {
                recipientIndex = i;
                break;
            }
        }

        if (recipientIndex != -1 && amount <= Double.parseDouble(logins.get(userIndex).get(3))) {
            double senderBalance = Double.parseDouble(logins.get(userIndex).get(3)) - amount;
            double recipientBalance = Double.parseDouble(logins.get(recipientIndex).get(3)) + amount;
            logins.get(userIndex).set(3, String.format("%.2f", senderBalance));
            logins.get(recipientIndex).set(3, String.format("%.2f", recipientBalance));
            zapiszDoBazy();
            out.write(("Transfer pomyslny, nowe saldo: " + String.format("%.2f", senderBalance) + "\r\n").getBytes("UTF-8"));
        } else {
            out.write("Transfer failed. Insufficient funds or recipient not found.\r\n".getBytes("UTF-8"));
        }
    }

    private void funWyplata(BufferedReader brinp, DataOutputStream out) throws IOException {
        if (userIndex == -1) {
            out.write("Prosze sie najpierw zalogowac.\r\n".getBytes("UTF-8"));
            return;
        }
        out.write("Podaj kwote wyplaty:\r\n".getBytes("UTF-8"));
        String amountStr = brinp.readLine();
        try {
            double amount = Double.parseDouble(amountStr);
            double currentBalance = Double.parseDouble(logins.get(userIndex).get(3));
            if (amount <= currentBalance) {
                currentBalance -= amount;
                logins.get(userIndex).set(3, String.format("%.2f", currentBalance));
                zapiszDoBazy();
                out.write(("Nowe saldo: " + String.format("%.2f", currentBalance) + "\r\n").getBytes("UTF-8"));
            } else {
                out.write("Niewystarczajace srodki.\r\n".getBytes("UTF-8"));
            }
        } catch (NumberFormatException e) {
            out.write("Zly format liczby.\r\n".getBytes("UTF-8"));
        }
    }

    private void funBalans(DataOutputStream out) throws IOException {
        if (userIndex == -1) {
            out.write("Prosze najpierw sie zalogowac.\r\n".getBytes("UTF-8"));
            return;
        }
        double currentBalance = Double.parseDouble(logins.get(userIndex).get(3));
        out.write(("Twoje obecne saldo to: " + String.format("%.2f", currentBalance) + "\r\n").getBytes("UTF-8"));
    }

    private void zapiszDoBazy() {
        Path path = Paths.get("baza.txt");
        List<String> lines = new ArrayList<>();
        for (ArrayList<String> userDetails : logins) {
            String line = String.join(",", userDetails);
            lines.add(line);
        }
        try {
            Files.write(path, lines);
        } catch (IOException e) {
            System.out.println("Blad zapisy danych do bazy: " + e);
        }
    }
}
