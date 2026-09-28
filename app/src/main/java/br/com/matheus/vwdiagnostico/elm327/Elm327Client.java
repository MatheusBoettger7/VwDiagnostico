package br.com.matheus.vwdiagnostico.elm327;

import br.com.matheus.vwdiagnostico.bluetooth.BluetoothSppClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Elm327Client {

    private final BluetoothSppClient transport;
    private final ExecutorService initializer = Executors.newSingleThreadExecutor();

    public Elm327Client(BluetoothSppClient transport) {
        this.transport = transport;
    }

    public void identify() {
        transport.send("ATI");
    }

    public void initialize() {
        initializer.execute(() -> {
            sendWithDelay("ATZ", 2000);
            sendWithDelay("ATE0", 150);
            sendWithDelay("ATL0", 150);
            sendWithDelay("ATS0", 150);
            sendWithDelay("ATSP0", 150);
            sendWithDelay("ATI", 0);
        });
    }

    private void sendWithDelay(String command, long delayAfterMs) {
        transport.send(command);
        if (delayAfterMs <= 0) {
            return;
        }

        try {
            Thread.sleep(delayAfterMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void sendReadOnly(String command) {
        String normalized = command == null ? "" : command.trim().toUpperCase();

        // First milestone: keep RAW commands restricted to AT commands
        // and standard OBD Mode 01 queries. Protocol-specific write/coding
        // commands will only be introduced in a later, explicit layer.
        boolean allowed = normalized.startsWith("AT")
                || normalized.matches("01[0-9A-F]{2}");

        if (!allowed) {
            throw new IllegalArgumentException(
                    "Nesta versão, use comandos AT ou consultas OBD Mode 01 (01xx).");
        }

        transport.send(normalized);
    }
}
