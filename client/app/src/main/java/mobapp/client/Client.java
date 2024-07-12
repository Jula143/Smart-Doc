package mobapp.client;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;


import android.annotation.SuppressLint;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

@SuppressLint("SetTextI18n")
public class Client extends AppCompatActivity {

    Thread Thread1 = null;
    EditText etIP, etPort;
    EditText etDocument;
    Button btnSend, btnGet, btnSave, btnConnect;
    TextView tvConection;
    String SERVER_IP;
    int SERVER_PORT;

    String currentServerFile= "";
    CountDownLatch latch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.client);

        etIP = findViewById(R.id.etIP);
        etPort = findViewById(R.id.etPort);
        etIP.setText("192.168.1.11");
        etPort.setText("8081");
        etDocument = findViewById(R.id.etDocument);
        tvConection = findViewById(R.id.tvConection);
        btnSend = findViewById(R.id.btnSend);
        btnGet = findViewById(R.id.btnGet);
        btnSave = findViewById(R.id.btnSave);
        btnConnect = findViewById(R.id.btnConnect);

        etDocument.setText(readFromFile());
    }

    public void connect(View v){
        SERVER_IP = etIP.getText().toString().trim();
        SERVER_PORT = Integer.parseInt(etPort.getText().toString().trim());
        Thread1 = new Thread(new Thread1());
        Thread1.start();
    }

    public void get(View v){
        latch = new CountDownLatch(1);
        new Thread(new Thread3("<GET>")).start();

        new Thread(() -> {
            try {
                latch.await();  // Wait for the latch to be released
                runOnUiThread(() -> {
                    String message = etDocument.getText().toString().trim();
                    if(!message.equals(currentServerFile))
                        handleConflict(currentServerFile, message, false);
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void send(View v){
        String message = etDocument.getText().toString().trim();
        if(!message.equals(currentServerFile))
            handleConflict(message, currentServerFile, true);
    }
    public void save(View v){
        saveToFile(etDocument.getText().toString());
        tvConection.setText("Saved\n");
    }

    private void saveToFile(String content) {
        File file = new File(getExternalFilesDir(null), "localCopy.txt");
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write((content + "\n").getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String readFromFile() {
        File file = new File(getExternalFilesDir(null), "localCopy.txt");
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

    private void handleConflict(final String newContent, String currentContent, boolean sendToServer) {
        runOnUiThread(() -> {
            Spannable diffContent = createDiff(currentContent, newContent);

            AlertDialog.Builder builder = new AlertDialog.Builder(Client.this);
            builder.setTitle("Conflict Detected");
            builder.setMessage("A conflict was detected in the document content. Review the changes below:");

            builder.setMessage(diffContent);

            builder.setPositiveButton("Keep Server Version", (dialog, which) -> {
                if(sendToServer){}
                else //getFromServer
                    etDocument.setText(newContent);
            });

            builder.setNegativeButton("Keep Local Version", (dialog, which) -> {
                if(sendToServer) {
                    new Thread(new Thread3(newContent + "\n" + "</END>" + "\n")).start();
                    tvConection.setText("Sent\n");
                }
                else {} //getFromServer
            });

            builder.show();
        });
    }

    private Spannable createDiff(String oldContent, String newContent) {
        String[] oldLines = oldContent.split("\n");
        String[] newLines = newContent.split("\n");

        // Compute LCS
        int[][] lcs = computeLCS(oldLines, newLines);

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        // Find added, removed, and modified lines
        int i = oldLines.length, j = newLines.length;
        while (i > 0 && j > 0) {
            if (oldLines[i - 1].equals(newLines[j - 1])) {
                // Line is unchanged
                spannableStringBuilder.insert(0, newLines[j - 1] + "\n");
                i--;
                j--;
            } else if (lcs[i - 1][j] >= lcs[i][j - 1]) {
                // Line is removed
                SpannableString removedLine = new SpannableString("- " + oldLines[i - 1] + "\n");
                removedLine.setSpan(new ForegroundColorSpan(Color.RED), 0, removedLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannableStringBuilder.insert(0, removedLine);
                i--;
            } else {
                // Line is added or modified
                SpannableString addedLine = new SpannableString("+ " + newLines[j - 1] + "\n");
                addedLine.setSpan(new ForegroundColorSpan(Color.GREEN), 0, addedLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannableStringBuilder.insert(0, addedLine);
                j--;
            }
        }

        // Remaining lines in old content
        while (i > 0) {
            SpannableString removedLine = new SpannableString("- " + oldLines[i - 1] + "\n");
            removedLine.setSpan(new ForegroundColorSpan(Color.RED), 0, removedLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableStringBuilder.insert(0, removedLine);
            i--;
        }

        // Remaining lines in new content
        while (j > 0) {
            SpannableString addedLine = new SpannableString("+ " + newLines[j - 1] + "\n");
            addedLine.setSpan(new ForegroundColorSpan(Color.GREEN), 0, addedLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableStringBuilder.insert(0, addedLine);
            j--;
        }

        return spannableStringBuilder;
    }

    private int[][] computeLCS(String[] oldLines, String[] newLines) {
        int m = oldLines.length;
        int n = newLines.length;
        int[][] lcs = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }
        return lcs;
    }

    private PrintWriter output;
    private BufferedReader input;
    class Thread1 implements Runnable {

        @Override
        public void run() {
            Socket socket;
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                output = new PrintWriter(socket.getOutputStream());
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                runOnUiThread(() -> {
                    tvConection.setText("Connected\n");
                });
                new Thread(new Thread2()).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class Thread2 implements Runnable {

        StringBuilder fullMessage = new StringBuilder();
        @Override
        public void run() {
            while (true) {
                try {
                    final String message = input.readLine();
                    if (message != null) {
                        if (message.equals("<END>")) {
                            currentServerFile = fullMessage.toString().trim();
                            runOnUiThread(() -> {
                                latch.countDown(); // Release the latch when done
                            });
                            fullMessage.setLength(0);  // Clear the StringBuilder
                        } else {
                            fullMessage.append(message).append("\n");
                        }
                    } else {
                        Thread1 = new Thread(new Thread1());
                        Thread1.start();
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class Thread3 implements Runnable {
        private String message;

        Thread3(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            output.write(message + "\n");
            output.flush();
        }
    }
}