package br.com.matheus.vwdiagnostico.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BluetoothSppClient {

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onRx(String text);
        void onError(String message, Throwable error);
    }

    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Listener listener;

    private BluetoothSocket socket;
    private OutputStream outputStream;
    private volatile boolean running;

    public BluetoothSppClient(Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        executor.execute(() -> {
            closeInternal();

            try {
                BluetoothSocket newSocket =
                        device.createRfcommSocketToServiceRecord(SPP_UUID);

                socket = newSocket;
                socket.connect();

                outputStream = socket.getOutputStream();
                InputStream inputStream = socket.getInputStream();
                running = true;

                listener.onConnected();

                byte[] buffer = new byte[4096];
                while (running) {
                    int count = inputStream.read(buffer);
                    if (count < 0) {
                        break;
                    }

                    if (count > 0) {
                        String text = new String(
                                buffer, 0, count, StandardCharsets.UTF_8);
                        listener.onRx(text);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    listener.onError("Falha na comunicação Bluetooth.", e);
                }
            } finally {
                boolean wasRunning = running;
                closeInternal();
                if (wasRunning) {
                    listener.onDisconnected();
                }
            }
        });
    }

    public void send(String command) {
        executor.execute(() -> {
            if (!isConnected()) {
                listener.onError("O adaptador não está conectado.", null);
                return;
            }

            String normalized = command == null ? "" : command.trim();
            if (normalized.isEmpty()) {
                return;
            }

            try {
                outputStream.write((normalized + "\r").getBytes(StandardCharsets.US_ASCII));
                outputStream.flush();
            } catch (IOException e) {
                listener.onError("Falha ao enviar o comando.", e);
            }
        });
    }

    public boolean isConnected() {
        return running && socket != null && socket.isConnected() && outputStream != null;
    }

    public void close() {
        executor.execute(this::closeInternal);
    }

    private void closeInternal() {
        running = false;

        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException ignored) {
        }
        outputStream = null;

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
    }
}
