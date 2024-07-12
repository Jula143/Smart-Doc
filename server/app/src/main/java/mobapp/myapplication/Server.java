package mobapp.myapplication;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressLint("SetTextI18n")
public class Server extends AppCompatActivity {
    ServerSocket serverSocket;
    Thread Thread1 = null;
    TextView tvIP, tvPort;
    TextView tvDocument;
    TextView conStatus;
    public static String SERVER_IP = "";
    public static final int SERVER_PORT = 8081;
    private AtomicInteger clientCount = new AtomicInteger(0); // Counter for connected clients

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.server);

        tvIP = findViewById(R.id.tvIP);
        tvPort = findViewById(R.id.tvPort);
        tvDocument = findViewById(R.id.tvDocument);
        conStatus = findViewById(R.id.tvConnectionStatus);

        try {
            SERVER_IP = getLocalIpAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        tvDocument.setText(readFromFile());

        Thread1 = new Thread(new Thread1());
        Thread1.start();
    }

    private void saveToFile(String message) {
        File file = new File(getExternalFilesDir(null), "document.txt");
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write((message + "\n").getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String readFromFile() {
        File file = new File(getExternalFilesDir(null), "document.txt");
        StringBuilder stringBuilder = new StringBuilder();

        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stringBuilder.toString();
    }


    private String getLocalIpAddress() throws UnknownHostException {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        assert wifiManager != null;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();
        return InetAddress.getByAddress(
                        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
    }

    class Thread1 implements Runnable {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                runOnUiThread(() -> {
                    conStatus.setText("Waiting for clients...");
                    tvIP.setText("IP: " + SERVER_IP);
                    tvPort.setText("Port: " + SERVER_PORT);
                });

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        clientCount.incrementAndGet(); // Increment client count
                        runOnUiThread(() -> conStatus.setText("Client connected (" + clientCount.get() + " clients)"));
                        new Thread(new ClientHandler(socket)).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter output;
        private BufferedReader input;
        StringBuilder singleMessage = new StringBuilder();
        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                output = new PrintWriter(clientSocket.getOutputStream(), true);
                input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String message;

                while ((message = input.readLine()) != null) {
                    final String msg = message;
                    runOnUiThread(() -> {
                        if (msg.equals("</END>")) {
                            String newContent = singleMessage.toString().trim();
                            saveToFile(newContent);
                            tvDocument.setText(readFromFile());
                            singleMessage.setLength(0);

                        } else if (msg.equals("<GET>")) {
                            new Thread(new Thread2(output, readFromFile())).start();
                        } else {
                            singleMessage.append(msg).append("\n");
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                    clientCount.decrementAndGet(); // Decrement client count when a client disconnects
                    runOnUiThread(() -> conStatus.setText("Client disconnected (" + clientCount.get() + " clients)"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class Thread2 implements Runnable {
        private PrintWriter output;
        private String message;

        Thread2(PrintWriter output, String message) {
            this.output = output;
            this.message = message;
        }

        @Override
        public void run() {
            if (output != null) {
                output.write(message +"<END>"+"\n");
                output.flush();
            }
        }
    }
}
