package br.com.matheus.vwdiagnostico.elm327;

import br.com.matheus.vwdiagnostico.bluetooth.BluetoothSppClient;

public final class Elm327Client {

    private final BluetoothSppClient transport;

    public Elm327Client(BluetoothSppClient transport) {
        this.transport = transport;
    }

    public void identify() {
        transport.send("ATI");
    }

    public void initialize() {
        transport.send("ATZ");
        transport.send("ATE0");
        transport.send("ATL0");
        transport.send("ATS0");
        transport.send("ATSP0");
        transport.send("ATI");
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
