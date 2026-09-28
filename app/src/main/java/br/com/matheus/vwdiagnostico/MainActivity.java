package br.com.matheus.vwdiagnostico;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import br.com.matheus.vwdiagnostico.bluetooth.BluetoothSppClient;
import br.com.matheus.vwdiagnostico.elm327.Elm327Client;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements BluetoothSppClient.Listener {

    private static final int REQUEST_BLUETOOTH_CONNECT = 1001;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSppClient bluetoothClient;
    private Elm327Client elm327Client;

    private Spinner deviceSpinner;
    private TextView connectionStatus;
    private TextView logView;
    private EditText rawCommandEdit;
    private Button connectButton;

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        buildUi();

        if (bluetoothAdapter == null) {
            setStatus("Bluetooth não disponível neste aparelho.");
            connectButton.setEnabled(false);
            return;
        }

        ensureBluetoothPermissionAndLoadDevices();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = text("VwDiagnostico", 24, Color.WHITE);
        root.addView(title, params());

        TextView subtitle = text(
                "Primeira etapa: Bluetooth Classic + ELM327 + log RAW",
                14, Color.LTGRAY);
        LinearLayout.LayoutParams subtitleParams = params();
        subtitleParams.bottomMargin = dp(12);
        root.addView(subtitle, subtitleParams);

        connectionStatus = text("Desconectado", 16, Color.YELLOW);
        root.addView(connectionStatus, params());

        deviceSpinner = new Spinner(this);
        root.addView(deviceSpinner, params());

        connectButton = button("Conectar");
        connectButton.setOnClickListener(v -> connectSelectedDevice());
        root.addView(connectButton, params());

        Button refreshButton = button("Atualizar dispositivos pareados");
        refreshButton.setOnClickListener(v -> ensureBluetoothPermissionAndLoadDevices());
        root.addView(refreshButton, params());

        LinearLayout testButtons = new LinearLayout(this);
        testButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button atiButton = button("Teste ATI");
        atiButton.setOnClickListener(v -> sendAti());

        Button initButton = button("Inicializar ELM327");
        initButton.setOnClickListener(v -> initializeElm());

        testButtons.addView(atiButton, weightParams());
        testButtons.addView(initButton, weightParams());
        root.addView(testButtons, params());

        TextView rawTitle = text("Comando somente leitura", 15, Color.WHITE);
        LinearLayout.LayoutParams rawTitleParams = params();
        rawTitleParams.topMargin = dp(10);
        root.addView(rawTitle, rawTitleParams);

        LinearLayout rawRow = new LinearLayout(this);
        rawRow.setOrientation(LinearLayout.HORIZONTAL);

        rawCommandEdit = new EditText(this);
        rawCommandEdit.setSingleLine(true);
        rawCommandEdit.setHint("Ex.: ATI ou 010C");
        rawCommandEdit.setTextColor(Color.WHITE);
        rawCommandEdit.setHintTextColor(Color.GRAY);
        rawRow.addView(rawCommandEdit, weightParams());

        Button sendRawButton = button("Enviar");
        sendRawButton.setOnClickListener(v -> sendRaw());
        rawRow.addView(sendRawButton, fixedWidthParams(110));

        root.addView(rawRow, params());

        TextView logTitle = text("Comunicação RAW", 15, Color.WHITE);
        LinearLayout.LayoutParams logTitleParams = params();
        logTitleParams.topMargin = dp(10);
        root.addView(logTitle, logTitleParams);

        logView = text("", 13, Color.GREEN);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        scrollView.addView(logView);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(4);
        root.addView(scrollView, scrollParams);

        Button clearButton = button("Limpar log");
        clearButton.setOnClickListener(v -> logView.setText(""));
        root.addView(clearButton, params());

        setContentView(root);
    }

    private void ensureBluetoothPermissionAndLoadDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_CONNECT);
            return;
        }

        loadPairedDevices();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadPairedDevices();
            } else {
                toast("A permissão de Bluetooth é necessária para conectar ao Vgate.");
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private void loadPairedDevices() {
        if (bluetoothAdapter == null) {
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            setStatus("Bluetooth está desligado.");
            try {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception ignored) {
            }
            return;
        }

        devices.clear();

        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        List<String> labels = new ArrayList<>();

        if (bondedDevices != null) {
            for (BluetoothDevice device : bondedDevices) {
                devices.add(device);
                String name = device.getName();
                if (name == null || name.trim().isEmpty()) {
                    name = "Dispositivo Bluetooth";
                }
                labels.add(name + "\n" + device.getAddress());
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels.isEmpty()
                        ? List.of("Nenhum dispositivo pareado")
                        : labels);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);

        setStatus(
                labels.isEmpty()
                        ? "Nenhum dispositivo Bluetooth pareado."
                        : labels.size() + " dispositivo(s) pareado(s)."
        );
    }

    @SuppressWarnings("MissingPermission")
    private void connectSelectedDevice() {
        if (devices.isEmpty()) {
            toast("Pareie o Vgate iCar Pro BT3.0 nas configurações do Android primeiro.");
            return;
        }

        BluetoothDevice device = devices.get(deviceSpinner.getSelectedItemPosition());

        if (bluetoothClient != null) {
            bluetoothClient.close();
        }

        bluetoothClient = new BluetoothSppClient(this);
        elm327Client = new Elm327Client(bluetoothClient);

        appendLog("INFO", "Conectando em " + safeDeviceName(device) + "...");
        setStatus("Conectando...");
        connectButton.setEnabled(false);

        bluetoothClient.connect(device);
    }

    private void sendAti() {
        if (!isConnected()) {
            toast("Conecte o adaptador primeiro.");
            return;
        }

        appendLog("TX", "ATI");
        elm327Client.identify();
    }

    private void initializeElm() {
        if (!isConnected()) {
            toast("Conecte o adaptador primeiro.");
            return;
        }

        appendLog("INFO", "Enviando sequência de inicialização ELM327.");
        appendLog("TX", "ATZ");
        appendLog("TX", "ATE0");
        appendLog("TX", "ATL0");
        appendLog("TX", "ATS0");
        appendLog("TX", "ATSP0");
        appendLog("TX", "ATI");

        elm327Client.initialize();
    }

    private void sendRaw() {
        if (!isConnected()) {
            toast("Conecte o adaptador primeiro.");
            return;
        }

        String command = rawCommandEdit.getText().toString();

        try {
            appendLog("TX", command.trim().toUpperCase(Locale.ROOT));
            elm327Client.sendReadOnly(command);
            rawCommandEdit.setText("");
        } catch (IllegalArgumentException e) {
            toast(e.getMessage());
        }
    }

    private boolean isConnected() {
        return bluetoothClient != null && bluetoothClient.isConnected();
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            setStatus("Conectado");
            connectButton.setEnabled(true);
            appendLog("INFO", "Bluetooth Classic/SPP conectado.");
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            setStatus("Desconectado");
            connectButton.setEnabled(true);
            appendLog("INFO", "Bluetooth desconectado.");
        });
    }

    @Override
    public void onRx(String text) {
        runOnUiThread(() -> appendLog("RX", text));
    }

    @Override
    public void onError(String message, Throwable error) {
        runOnUiThread(() -> {
            setStatus("Erro");
            connectButton.setEnabled(true);
            appendLog(
                    "ERR",
                    error == null || error.getMessage() == null
                            ? message
                            : message + " " + error.getMessage());
        });
    }

    @Override
    protected void onDestroy() {
        if (bluetoothClient != null) {
            bluetoothClient.close();
        }
        super.onDestroy();
    }

    private void setStatus(String status) {
        connectionStatus.setText("Status: " + status);
    }

    private void appendLog(String type, String message) {
        String timestamp = timeFormat.format(new Date());
        logView.append("[" + timestamp + "] [" + type + "] " + message + "\n");
        logView.post(() -> {
            View parent = (View) logView.getParent();
            if (parent != null) {
                parent.requestFocus();
            }
        });
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? device.getAddress() : name;
        } catch (SecurityException e) {
            return "Bluetooth";
        }
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
    }

    private LinearLayout.LayoutParams fixedWidthParams(int widthDp) {
        return new LinearLayout.LayoutParams(
                dp(widthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }
}
