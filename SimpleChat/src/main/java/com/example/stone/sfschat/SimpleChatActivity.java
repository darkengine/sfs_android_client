package com.example.stone.sfschat;

import java.util.Date;

import sfs2x.client.SmartFox;
import sfs2x.client.core.BaseEvent;
import sfs2x.client.core.IEventListener;
import sfs2x.client.core.SFSEvent;
import sfs2x.client.entities.Room;
import sfs2x.client.entities.User;
import sfs2x.client.requests.JoinRoomRequest;
import sfs2x.client.requests.LoginRequest;
import sfs2x.client.requests.PublicMessageRequest;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.smartfoxserver.v2.exceptions.SFSException;

public class SimpleChatActivity extends Activity implements IEventListener {

	private final String TAG = this.getClass().getSimpleName();
	private final static String TAB_TAG_CHAT = "tChat";
	private final static String TAB_TAG_USERS = "tUsers";

	private final static boolean DEBUG_SFS = true;

	private final static boolean VERBOSE_MODE = true;

	private final static String DEFAULT_SERVER_ADDRESS = "10.0.2.2";
	private final static String DEFAULT_SERVER_PORT = "9933";

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

	EditText inputServerAddress, inputServerPort, inputUserNick, inputChatMessage;
	View buttonConnect, buttonLogin, buttonChatSend, layoutConnector, layoutLogin, layoutChat;
	TextView labelStatus, labelTagUsers;
	CheckBox checkUseBlueBox;
	ListView listUsers, listMessages;
	ArrayAdapter<String> adapterUsers;
	MessagesAdapter adapterMessages;
	TabHost mTabHost;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
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
		sfsClient.addEventListener(SFSEvent.USER_ENTER_ROOM, this);
		sfsClient.addEventListener(SFSEvent.USER_EXIT_ROOM, this);
		sfsClient.addEventListener(SFSEvent.PUBLIC_MESSAGE, this);

		if (VERBOSE_MODE)
			Log.v(TAG, "SmartFox created:" + sfsClient.isConnected() + " BlueBox enabled="
					+ sfsClient.useBlueBox());
	}

	private void initUI() {
		// Load the view references
		inputServerAddress = (EditText) findViewById(R.id.edit_server_address);
		inputServerPort = (EditText) findViewById(R.id.edit_server_port);
		buttonConnect = findViewById(R.id.button_connect);
		buttonLogin = findViewById(R.id.button_login);
		labelStatus = (TextView) findViewById(R.id.label_status);
		checkUseBlueBox = (CheckBox) findViewById(R.id.check_bluebox);
		layoutConnector = findViewById(R.id.container_connection);
		layoutLogin = findViewById(R.id.container_login);
		layoutChat = findViewById(R.id.container_chat);
		inputUserNick = (EditText) findViewById(R.id.edit_user_nick);
		listUsers = (ListView) findViewById(R.id.list_users);
		listMessages = (ListView) findViewById(R.id.list_chat);
		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		inputChatMessage = (EditText) findViewById(R.id.input_chat_message);
		buttonChatSend = findViewById(R.id.button_chat_send);

		// Init the views
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
		checkUseBlueBox.setChecked(sfsClient.useBlueBox());
		checkUseBlueBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				sfsClient.setUseBlueBox(isChecked);
				if (VERBOSE_MODE) Log.v(TAG, "Use BlueBox=" + sfsClient.useBlueBox());
			}
		});
		buttonLogin.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String userNick = inputUserNick.getText().toString();
				String zoneName = getString(R.string.example_zone);
				if (VERBOSE_MODE) Log.v(TAG, "Login as '" + userNick + "' into " + zoneName);
				LoginRequest loginRequest = new LoginRequest(userNick, "", zoneName);
				sfsClient.send(loginRequest);

			}
		});
		buttonChatSend.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String message = inputChatMessage.getText().toString();
				if (message.length() > 0) {
					// As long as message is non-blank create a new
					// PublicMessage and send to the server
					sfsClient.send(new PublicMessageRequest(message));
					inputChatMessage.setText("");
				}
			}
		});

		// The list of users
		adapterUsers = new ArrayAdapter<String>(this, R.layout.row_user);
		listUsers.setAdapter(adapterUsers);
		adapterMessages = new MessagesAdapter(this);
		listMessages.setAdapter(adapterMessages);
		// Enable auto scroll
		listMessages.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		listMessages.setStackFromBottom(true);

		// The tabs
		mTabHost.setup();
		mTabHost.addTab(newTab(TAB_TAG_CHAT, R.string.chat, R.id.tab1));
		mTabHost.addTab(newTab(TAB_TAG_USERS, R.string.users, R.id.tab2));
		showLayout(layoutConnector);
	}

	/**
	 * Create a TabSpec with the given tag, label and content
	 * 
	 * @param tag
	 * @param labelId
	 * @param tabContentId
	 * @return
	 */
	private TabSpec newTab(String tag, int labelId, int tabContentId) {
		View indicator = LayoutInflater.from(this).inflate(R.layout.tab_header,
				(ViewGroup) findViewById(android.R.id.tabs), false);
		TextView label = (TextView) indicator.findViewById(android.R.id.title);
		label.setText(labelId);
		if (TAB_TAG_USERS.equals(tag)) {
			labelTagUsers = label;
		}
		TabSpec tabSpec = mTabHost.newTabSpec(tag);
		tabSpec.setIndicator(indicator);
		tabSpec.setContent(tabContentId);
		return tabSpec;
	}

	private void updateUsersTabLabel() {
		labelTagUsers.setText(getString(R.string.users) + " (" + adapterUsers.getCount() + ")");
	}

	@Override
	public void dispatch(final BaseEvent event) throws SFSException {

		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				if (VERBOSE_MODE)
					Log.v(TAG,
							"Dispatching " + event.getType() + " (arguments="
									+ event.getArguments() + ")");
				if (event.getType().equalsIgnoreCase(SFSEvent.CONNECTION)) {
					if (event.getArguments().get("success").equals(true)) {
						setStatus(Status.CONNECTED, sfsClient.getConnectionMode());
						// Login as guest in current zone
						showLayout(layoutLogin);
						// sfsClient.send(new LoginRequest("", "",
						// getString(R.string.example_zone)));
					} else {
						setStatus(Status.CONNECTION_ERROR);
						showLayout(layoutConnector);
					}
				} else if (event.getType().equalsIgnoreCase(SFSEvent.CONNECTION_LOST)) {
					setStatus(Status.CONNECTION_LOST);
					disconnect();
					adapterMessages.clear();
					adapterUsers.clear();
					showLayout(layoutConnector);
					try {
						// Show a dialog with the reason
						new AlertDialog.Builder(SimpleChatActivity.this)
								.setTitle(R.string.connection_lost)
								.setMessage(
										getString(R.string.dialog_connection_lost_message, event
												.getArguments().get("reason").toString()))
								.setPositiveButton(android.R.string.ok, null).show();
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else if (event.getType().equalsIgnoreCase(SFSEvent.LOGIN)) {
					setStatus(Status.LOGGED, sfsClient.getCurrentZone());
					sfsClient.send(new JoinRoomRequest(getString(R.string.example_lobby)));
				} else if (event.getType().equalsIgnoreCase(SFSEvent.ROOM_JOIN)) {
					setStatus(Status.IN_A_ROOM, sfsClient.getLastJoinedRoom().getName());
					showLayout(layoutChat);
					Room room = (Room) event.getArguments().get("room");
					for (User user : room.getUserList()) {
						adapterUsers.add(user.getName());
						updateUsersTabLabel();
					}
					adapterMessages.add(new ChatMessage("Room [" + room.getName() + "] joined"));

				}// When a user enter the room the user list is updated
			else if (event.getType().equals(SFSEvent.USER_ENTER_ROOM)) {
				final User user = (User) event.getArguments().get("user");
				if (VERBOSE_MODE) Log.v(TAG, "User '" + user.getName() + "' joined the room");
				adapterUsers.add(user.getName());
				updateUsersTabLabel();
				adapterMessages.add(new ChatMessage("User '" + user.getName() + "' joined the room"));
			}
			// When a user leave the room the user list is updated
			else if (event.getType().equals(SFSEvent.USER_EXIT_ROOM)) {
				final User user = (User) event.getArguments().get("user");
				if (VERBOSE_MODE) Log.v(TAG, "User '" + user.getName() + "' left the room");
				adapterUsers.remove(user.getName());
				updateUsersTabLabel();
				adapterMessages.add(new ChatMessage("User '" + user.getName() + "' left the room"));
			}
			// When public message is received it's added to the chat
			// history
			else if (event.getType().equals(SFSEvent.PUBLIC_MESSAGE)) {
				ChatMessage message = new ChatMessage();
				User sender = (User) event.getArguments().get("sender");
				message.setUsername(sender.getName());
				message.setMessage(event.getArguments().get("message").toString());
				message.setDate(new Date());
				// If my id and the sender id are different is a incoming
				// message
				message.setIncomingMessage(sender.getId() != sfsClient.getMySelf().getId());
				adapterMessages.add(message);
			}
		}
	});

	}

	private void connect(String serverIP, String serverPort) {
		// if the user have entered port number it uses it...
		if (serverPort.length() > 0) {
			int serverPortValue = Integer.parseInt(serverPort);
			// tries to connect to the server
			// connectToServer(serverIP, serverPortValue);
			sfsClient.connect(serverIP, serverPortValue);
		}
		// ...otherwise uses the default port number
		else {
			// tries to connect to the server
			// connectToServer(serverIP);
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

	private void disconnect() {
		if (VERBOSE_MODE) Log.v(TAG, "Disconnecting");
		if (sfsClient.isConnected()) {
			if (VERBOSE_MODE) Log.v(TAG, "Disconnect: Disconnecting client");
			sfsClient.disconnect();
			if (VERBOSE_MODE) Log.v(TAG, "Disconnect: Disconnected ? " + !sfsClient.isConnected());
			// initSmartFox();
		}
	}

	private void setStatus(Status status, String... params) {
		if (status == currentStatus) {
			// If there is no status change ignore it
			return;
		}

		if (VERBOSE_MODE) Log.v(TAG, "New status= " + status);
		currentStatus = status;
		final String message;
		final int messageColor;
		final boolean connectButtonEnabled;
		switch (status) {
		case CONNECTING:
			message = getString(R.string.connecting);
			messageColor = COLOR_BLUE;
			connectButtonEnabled = false;
			break;
		case DISCONNECTED:
			message = getString(R.string.disconnected);
			messageColor = COLOR_GRAY;
			connectButtonEnabled = true;
			break;
		case CONNECTION_ERROR:
			message = getString(R.string.connection_error);
			messageColor = COLOR_RED;
			connectButtonEnabled = true;
			break;
		case CONNECTED:
			message = getString(R.string.connected) + ": " + params[0];
			messageColor = COLOR_GREEN;
			connectButtonEnabled = false;
			break;
		case CONNECTION_LOST:
			message = getString(R.string.connection_lost);
			messageColor = COLOR_ORANGE;
			connectButtonEnabled = true;
			break;
		case LOGGED:
			message = getString(R.string.logged_into) + "'" + params[0] /*
																		 * zone name
																		 */
					+ "' zone";
			messageColor = COLOR_GREEN;
			connectButtonEnabled = false;
			break;
		case IN_A_ROOM:
			message = getString(R.string.joined_to_room) + params[0] /* room name */
					+ "'";
			messageColor = COLOR_GREEN;
			connectButtonEnabled = false;
			break;
		default:
			connectButtonEnabled = true;
			messageColor = 0;
			message = null;
		}
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				setStatusMessage(message, messageColor);
				buttonConnect.setEnabled(connectButtonEnabled);
			}
		});

	}

	private void setStatusMessage(final String message, final int color) {
		labelStatus.setText(message);
		if (color != 0) {
			labelStatus.setTextColor(color);
		}
	}

	private void showLayout(final View layoutToShow) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				// Show the layout selected and hide the others
				for (View layout : new View[] { layoutChat, layoutConnector, layoutLogin }) {
					if (layoutToShow == layout) {
						layout.setVisibility(View.VISIBLE);
					} else {
						layout.setVisibility(View.GONE);
					}
				}
			}
		});
	}

}
