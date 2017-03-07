package com.example.stone.sfsandroidclient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import sfs2x.client.SmartFox;
import sfs2x.client.core.BaseEvent;
import sfs2x.client.core.IEventListener;
import sfs2x.client.core.SFSEvent;
import sfs2x.client.requests.JoinRoomRequest;
import sfs2x.client.requests.LoginRequest;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

import com.smartfoxserver.v2.exceptions.SFSException;

public class MainActivity extends Activity implements IEventListener {

    private final String TAG = this.getClass().getSimpleName();

    private final static boolean DEBUG_SFS = true;
    private final static boolean VERBOSE_MODE = true;

    private final static String DEFAULT_SERVER_ADDRESS = "10.0.2.2";
    private final static String DEFAULT_SERVER_PORT = "9933";
    private final static SimpleDateFormat logDateFormater = new SimpleDateFormat("h:mm:ss",
        Locale.US);
    private final static int COLOR_GREEN = Color.parseColor("#99FF99");
    private final static int COLOR_BLUE = Color.parseColor("#99CCFF");
    private final static int COLOR_GRAY = Color.parseColor("#cccccc");
    private final static int COLOR_RED = Color.parseColor("#FF0000");
    private final static int COLOR_ORANGE = Color.parseColor("#f4aa0b");

    private enum Status {
        DISCONNECTED, CONNECTED, CONNECTING, CONNECTION_ERROR, CONNECTION_LOST, LOGGED, IN_A_ROOM
    }

    Status currentStatus = null;

    SmartFox sfsClient;

    EditText inputServerAddress, inputServerPort;
    View buttonConnect, buttonDisconnect;
    TextView labelLog, labelStatus;
    CheckBox checkUseBlueBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connector);
        System.setProperty("java.net.preferIPv6Addresses", "false");
        initSmartFox();
        initUI();
        setStatus(Status.DISCONNECTED);
    }

    private void initSmartFox() {

        // Instantiate SmartFox client
        sfsClient = new SmartFox(DEBUG_SFS);

        // Add event listeners
        sfsClient.addEventListener(SFSEvent.CONNECTION, this);
        sfsClient.addEventListener(SFSEvent.CONNECTION_LOST, this);
        sfsClient.addEventListener(SFSEvent.LOGIN, this);
        sfsClient.addEventListener(SFSEvent.LOGIN_ERROR, this);
        sfsClient.addEventListener(SFSEvent.ROOM_JOIN, this);
        sfsClient.addEventListener(SFSEvent.HANDSHAKE, this);
        sfsClient.addEventListener(SFSEvent.SOCKET_ERROR, this);
        if (VERBOSE_MODE)
            Log.v(TAG, "SmartFox created:" + sfsClient.isConnected() + " BlueBox enabled="
                + sfsClient.useBlueBox());
    }

    private void initUI() {
        // Load the view refferences
        inputServerAddress = (EditText) findViewById(R.id.edit_server_address);
        inputServerPort = (EditText) findViewById(R.id.edit_server_port);
        buttonConnect = findViewById(R.id.button_connect);
        buttonDisconnect = findViewById(R.id.button_disconnect);
        labelLog = (TextView) findViewById(R.id.label_log);
        labelStatus = (TextView) findViewById(R.id.label_status);
        checkUseBlueBox = (CheckBox) findViewById(R.id.check_bluebox);

        // Init the views & vyttibs
        inputServerAddress.setText(DEFAULT_SERVER_ADDRESS);
        inputServerPort.setText(DEFAULT_SERVER_PORT);
        buttonConnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                connect(inputServerAddress.getText().toString(), inputServerPort.getText()
                    .toString());
                setStatus(Status.CONNECTING);
            }
        });
        buttonDisconnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                disconnect();
                if (!sfsClient.isConnected()) {
                    setStatus(Status.DISCONNECTED);
                }
            }
        });
        checkUseBlueBox.setChecked(sfsClient.useBlueBox());
        checkUseBlueBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sfsClient.setUseBlueBox(isChecked);
                if (VERBOSE_MODE) Log.v(TAG, "Use BlueBox=" + sfsClient.useBlueBox());
            }
        });
    }

    @Override
    public void dispatch(final BaseEvent event) throws SFSException {
        if (VERBOSE_MODE)
            Log.v(TAG, "Dispatching " + event.getType() + " (arguments=" + event.getArguments()
                + ")");
        if (event.getType().equalsIgnoreCase(SFSEvent.CONNECTION)) {
            if (event.getArguments().get("success").equals(true)) {
                // Login as guest in current zone
                sfsClient.send(new LoginRequest("", "", getString(R.string.example_zone)));
                setStatus(Status.CONNECTED, sfsClient.getConnectionMode());
            } else {
                setStatus(Status.CONNECTION_ERROR);
            }
        } else if (event.getType().equalsIgnoreCase(SFSEvent.CONNECTION_LOST)) {
            setStatus(Status.CONNECTION_LOST);
            disconnect();
        } else if (event.getType().equalsIgnoreCase(SFSEvent.LOGIN)) {
            setStatus(Status.LOGGED, sfsClient.getCurrentZone());
            sfsClient.send(new JoinRoomRequest(getString(R.string.example_lobby)));
        } else if (event.getType().equalsIgnoreCase(SFSEvent.ROOM_JOIN)) {
            setStatus(Status.IN_A_ROOM, sfsClient.getLastJoinedRoom().getName());
        }
    }

    private void connect(String serverIP, String serverPort) {
        // if the user have entered port number it uses it...
        if (serverPort.length() > 0) {
            int serverPortValue = Integer.parseInt(serverPort);
            if (VERBOSE_MODE) Log.v(TAG, "Connecting to " + serverIP + ":" + serverPort);
            sfsClient.connect(serverIP, serverPortValue);
        }
        // ...otherwise uses the default port number
        else {
            if (VERBOSE_MODE) Log.v(TAG, "Connecting to " + serverIP);
            sfsClient.connect(serverIP);
        }
    }

    /**
     * Frees the resources.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
        if (VERBOSE_MODE) Log.v(TAG, "Removing Listeners");
        sfsClient.removeAllEventListeners();
    }

    /**
     * Disconnect the client from the server
     */
    private void disconnect() {
        if (VERBOSE_MODE) Log.v(TAG, "Disconnecting");

        if (sfsClient.isConnected()) {
            if (VERBOSE_MODE) Log.v(TAG, "Disconnect: Disconnecting client");
            sfsClient.disconnect();
            if (VERBOSE_MODE) Log.v(TAG, "Disconnect: Disconnected ? " + !sfsClient.isConnected());
        }
    }

    /**
     * Update the current status, the status message and log the change
     *
     * @param status
     * @param params
     */
    private void setStatus(Status status, String... params) {
        if (status == currentStatus) {
            // If there is no status change ignore it
            return;
        }

        if (VERBOSE_MODE) Log.v(TAG, "New status= " + status);
        currentStatus = status;
        final String message;
        final int messageColor;
        final boolean connectButtonEnabled, disconnectButtonEnabled;
        switch (status) {
            case CONNECTING:
                message = getString(R.string.connecting);
                messageColor = COLOR_BLUE;
                connectButtonEnabled = false;
                disconnectButtonEnabled = true;
                break;
            case DISCONNECTED:
                message = getString(R.string.disconnected);
                messageColor = COLOR_GRAY;
                connectButtonEnabled = true;
                disconnectButtonEnabled = false;
                break;
            case CONNECTION_ERROR:
                message = getString(R.string.connection_error);
                messageColor = COLOR_RED;
                connectButtonEnabled = true;
                disconnectButtonEnabled = false;
                break;
            case CONNECTED:
                message = getString(R.string.connected) + ": " + params[0];
                messageColor = COLOR_GREEN;
                connectButtonEnabled = false;
                disconnectButtonEnabled = true;
                break;
            case CONNECTION_LOST:
                message = getString(R.string.connection_lost);
                messageColor = COLOR_ORANGE;
                connectButtonEnabled = true;
                disconnectButtonEnabled = false;
                break;
            case LOGGED:
                message = getString(R.string.logged_into) + "'" + params[0] /*
																		 * zone name
																		 */
                    + "' zone";
                messageColor = COLOR_GREEN;
                connectButtonEnabled = false;
                disconnectButtonEnabled = true;
                break;
            case IN_A_ROOM:
                message = getString(R.string.joined_to_room) + params[0] /* room name */
                    + "'";
                messageColor = COLOR_GREEN;
                connectButtonEnabled = false;
                disconnectButtonEnabled = true;
                break;
            default:
                connectButtonEnabled = true;
                disconnectButtonEnabled = true;
                messageColor = 0;
                message = null;
        }
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                setStatusMessage(message, messageColor);
                log(message);
                buttonConnect.setEnabled(connectButtonEnabled);
                buttonDisconnect.setEnabled(disconnectButtonEnabled);
            }
        });

    }

    private void setStatusMessage(final String message, final int color) {
        labelStatus.setText(message);
        if (color != 0) {
            labelStatus.setTextColor(color);
        }
    }

    /**
     * Write the message in the log label
     *
     * @param message
     *            message to log
     */
    private void log(final String message) {
        labelLog.setText(logDateFormater.format(new Date()) + ": " + message + "\n"
            + labelLog.getText());
    }
}

