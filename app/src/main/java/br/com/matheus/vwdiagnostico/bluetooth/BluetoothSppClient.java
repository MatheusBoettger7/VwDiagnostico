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

    /*
     * Leitura e escrita precisam de executores diferentes.
     *
     * O loop de leitura fica bloqueado em InputStream.read() enquanto a
     * conexão estiver ativa. Se a escrita usar o mesmo executor, os comandos
     * enviados depois da conexão ficam presos na fila e nunca chegam ao ELM327.
     */
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor();
    private final Listener listener;

    private volatile BluetoothSocket socket;
    private volatile OutputStream outputStream;
    private volatile boolean running;

    public BluetoothSppClient(Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        readerExecutor.execute(() -> {
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
        writerExecutor.execute(() -> {
            if (!isConnected()) {
                listener.onError("O adaptador não está conectado.", null);
                return;
            }

            String normalized = command == null ? "" : command.trim();
            if (normalized.isEmpty()) {
                return;
            }

            try {
                OutputStream output = outputStream;

                if (output == null) {
                    listener.onError("Canal de escrita Bluetooth indisponível.", null);
                    return;
                }

                output.write(
                        (normalized + "\r")
                                .getBytes(StandardCharsets.US_ASCII));
                output.flush();
            } catch (IOException e) {
                listener.onError("Falha ao enviar o comando.", e);
            }
        });
    }

    public boolean isConnected() {
        BluetoothSocket currentSocket = socket;
        return running
                && currentSocket != null
                && currentSocket.isConnected()
                && outputStream != null;
    }

    public void close() {
        /*
         * Não colocar o fechamento na fila do readerExecutor.
         * O reader pode estar bloqueado em InputStream.read(). Fechar o socket
         * diretamente interrompe o read e permite que a thread termine.
         */
        closeInternal();
    }

    private synchronized void closeInternal() {
        running = false;

        OutputStream output = outputStream;
        outputStream = null;

        try {
            if (output != null) {
                output.close();
            }
        } catch (IOException ignored) {
        }

        BluetoothSocket currentSocket = socket;
        socket = null;

        try {
            if (currentSocket != null) {
                currentSocket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
