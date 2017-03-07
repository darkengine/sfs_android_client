package com.example.stone.sfstris;

import sfs2x.client.SmartFox;
import sfs2x.client.core.BaseEvent;
import sfs2x.client.core.IEventListener;
import sfs2x.client.core.SFSEvent;
import sfs2x.client.entities.Room;
import sfs2x.client.entities.User;
import sfs2x.client.requests.ExtensionRequest;
import sfs2x.client.requests.JoinRoomRequest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.exceptions.SFSException;

public class TrisGameActivity extends Activity implements IEventListener {

	private final String TAG = this.getClass().getSimpleName();
	private final static boolean VERBOSE_MODE = true;

	protected final static int PLAYER_1 = 1;
	protected final static int PLAYER_2 = 2;

	enum moves {
		start, stop, move, win, tie;
	}

	boolean myTurn;

	SmartFox sfsClient;
	View buttonExit, containerUIBlocker, buttonBlocker;
	TextView labelBlocker;
	ProgressBar progressBlocker;
	BoardViewsHelper board;

	private boolean gameStarted;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tris_game);
		initViews();
		initSmartFox();
	}

	private void initViews() {
		board = new BoardViewsHelper(findViewById(android.R.id.content));
		// Load the views references
		buttonExit = findViewById(R.id.button_exit);
		buttonBlocker = findViewById(R.id.button_blocker);
		labelBlocker = (TextView) findViewById(R.id.label_blocker);
		progressBlocker = (ProgressBar) findViewById(R.id.progress_blocker);

		containerUIBlocker = findViewById(R.id.container_ui_blocker);
		containerUIBlocker.setSoundEffectsEnabled(false);
		containerUIBlocker.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

			}
		});

		// Init the views
		View.OnClickListener clickExit = new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				endGame();
			}
		};
		buttonExit.setOnClickListener(clickExit);
		buttonBlocker.setOnClickListener(clickExit);

	}

	private void initSmartFox() {
		// Instantiate SmartFox client
		sfsClient = SFSController.getSFSClient();
		// Register to SmartFox events
		sfsClient.addEventListener(SFSEvent.EXTENSION_RESPONSE, this);
		sfsClient.addEventListener(SFSEvent.CONNECTION_LOST, this);
		sfsClient.addEventListener(SFSEvent.USER_EXIT_ROOM, this);

		// Tell extension I'm ready to play
		sfsClient
				.send(new ExtensionRequest("ready", new SFSObject(), sfsClient.getLastJoinedRoom()));

		if (VERBOSE_MODE)
			Log.v(TAG, "SmartFox created:" + sfsClient.isConnected() + " BlueBox enabled="
					+ sfsClient.useBlueBox());

		if (sfsClient.getLastJoinedRoom().getUserCount() == 1) {
			waitForOpponent();
		}
		gameStarted = true;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Remove listeners, rejoin the lobby and go to application's main activity
		sfsClient.removeEventListener(SFSEvent.EXTENSION_RESPONSE, this);
		sfsClient.removeEventListener(SFSEvent.CONNECTION_LOST, this);
		sfsClient.removeEventListener(SFSEvent.USER_EXIT_ROOM, this);
		Room lobby = sfsClient.getRoomByName(getString(R.string.example_lobby));
		if (sfsClient.isConnected() && !sfsClient.getMySelf().isJoinedInRoom(lobby)) {
			sfsClient.send(new JoinRoomRequest(getString(R.string.example_lobby), "", sfsClient
					.getLastJoinedRoom().getId()));
		}
	}

	@Override
	public void dispatch(BaseEvent event) throws SFSException {
		// If connection is lost switch back to main activity
		if (event.getType().equalsIgnoreCase(SFSEvent.CONNECTION_LOST)) {
			showMessage(getString(R.string.connection_lost)
					+ "\n"
					+ getString(R.string.dialog_connection_lost_message,
							event.getArguments().get("reason").toString()));
		}
		if (event.getType().equalsIgnoreCase(SFSEvent.EXTENSION_RESPONSE)) {
			String cmd = event.getArguments().get("cmd").toString();
			ISFSObject resObj = new SFSObject();
			resObj = (ISFSObject) event.getArguments().get("params");

			switch (moves.valueOf(cmd)) {
			case start:
				startGame(resObj);
				break;

			case move:
				moveReceived(resObj);
				break;

			case win:
			case tie:
				showWinner(cmd, resObj);
				break;
			}
		}
		// Handle a user leaving the room - display winners message to remaining player if game is
		// not yet over
		if (event.getType().equalsIgnoreCase(SFSEvent.USER_EXIT_ROOM)) {
			Room room = (Room) event.getArguments().get("room");
			if (room.isGame() && gameStarted) {
				User user = (User) event.getArguments().get("user");
				ISFSObject obj = new SFSObject();
				obj.putUtfString("q", user.getName());
				showWinner("earlyExit", obj);
			}
		}
	}

	/**
	 * Set who's turn it is and start the game
	 * 
	 * @param resObj
	 */
	private void startGame(ISFSObject resObj) {
		gameStarted = true;
		int firstTurn = resObj.getInt("t");
		myTurn = sfsClient.getMySelf().getPlayerId() == firstTurn ? true : false;
		board.clear();
		setTurn();
	}

	/**
	 * Show or hide the "waiting for opponent" dialog depending on who's turn it is.
	 */
	private void setTurn() {
		if (gameStarted) {
			if (myTurn) {
				endWaitForOpponent();
			} else {
				waitForOpponent();
			}
		}
	}

	/**
	 * Determine where to draw the icon just placed and send message to handler so icon is drawn in
	 * correct place
	 * 
	 * @param resObj
	 */
	private void moveReceived(ISFSObject resObj) {

		int movingPlayer = resObj.getInt("t");
		int x = resObj.getInt("x");
		int y = resObj.getInt("y");
		if (VERBOSE_MODE)
			Log.v(TAG, "moveReceived: player=" + movingPlayer + "   movement=" + x + "," + y);
		board.markCell(x, y, movingPlayer);
		myTurn = movingPlayer != sfsClient.getMySelf().getPlayerId();
		setTurn();
	}

	/**
	 * Send the move to the server's extension
	 * @param x
	 * @param y
	 */
	public void sendMove(int x, int y) {
		// Send the selected square to SFS2X
		ISFSObject sfso = new SFSObject();
		sfso.putInt("x", x);
		sfso.putInt("y", y);
		sfsClient.send(new ExtensionRequest("move", sfso, sfsClient.getLastJoinedRoom()));
	}

	/**
	 * Set the relevant message to be displayed to the user at the end of the game
	 * 
	 * @param cmd
	 *            - was the game won by a user or tied
	 * @param resObj
	 *            - if there was a winner this will contain the id of the winning player
	 */
	private void showWinner(String cmd, ISFSObject resObj) {
		final String resultMessage;
		if (cmd.equalsIgnoreCase("win")) {
			if (sfsClient.getMySelf().getPlayerId() == resObj.getInt("w")) {
				resultMessage = "You are the WINNER!";
			} else {
				resultMessage = "Sorry, you've LOST!";
			}
		} else if (cmd.equalsIgnoreCase("tie")) {
			resultMessage = "It's a TIE!";
		} else {
			resultMessage = "Game finished early - " + resObj.getUtfString("q") + " quit.";
		}
		showMessage(resultMessage);
	}

	/**
	 * Finish this activity and go back to the application's main activity
	 */
	public void endGame() {
		Intent gameIntent = new Intent();
		setResult(RESULT_OK, gameIntent);
		finish();
	}

	/*
	 * UI Blocker methods
	 */

	/**
	 * Show the waiting view
	 */

	private void waitForOpponent() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				containerUIBlocker.setVisibility(View.VISIBLE);
				progressBlocker.setVisibility(View.VISIBLE);
				buttonBlocker.setVisibility(View.GONE);
				labelBlocker.setText(R.string.message_wait_for_opponent);
			}
		});
	}

	/**
	 * Dismiss the waiting view
	 */
	private void endWaitForOpponent() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				containerUIBlocker.setVisibility(View.GONE);
			}
		});
	}

	/**
	 * Show a message to the user
	 */
	private void showMessage(final String message) {

		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				containerUIBlocker.setVisibility(View.VISIBLE);
				progressBlocker.setVisibility(View.GONE);
				buttonBlocker.setVisibility(View.VISIBLE);
				labelBlocker.setText(message);
			}
		});
	}

	private class BoardViewsHelper implements OnClickListener {

		private ImageButton[][] cells = new ImageButton[3][3];

		public BoardViewsHelper(View activityView) {

			int counterX = 0, counterY = 0;
			for (int viewId : new int[] { R.id.game_cell_1, R.id.game_cell_2, R.id.game_cell_3,
					R.id.game_cell_4, R.id.game_cell_5, R.id.game_cell_6, R.id.game_cell_7,
					R.id.game_cell_8, R.id.game_cell_9 }) {
				ImageButton cell = (ImageButton) activityView.findViewById(viewId);
				cell.setOnClickListener(BoardViewsHelper.this);
				cells[counterX][counterY] = cell;
				counterX++;
				if (counterX >= cells.length) {
					counterX = 0;
					counterY++;
				}
			}
		}

		/**
		 * Mark the specified cell with a ball of the movingPlayer
		 * 
		 * @param x
		 * @param y
		 * @param movingPlayer
		 *            PLAYER_1 or PLAYER_2
		 */
		public void markCell(int x, int y, final int movingPlayer) {

			final ImageView cell = cells[x - 1][y - 1];
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					cell.setImageResource(movingPlayer == PLAYER_1 ? R.drawable.player_1_token
							: R.drawable.player_2_token);
				}
			});
		}

		public void clear() {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					for (int i = 0; i < cells.length; i++) {
						for (int j = 0; j < cells[0].length; j++) {
							cells[i][j].setImageDrawable(null);
						}
					}
				}
			});
		}

		@Override
		public void onClick(View v) {
			int x, y;
			switch (v.getId()) {
			// Calculate the x,y values depending on the view clicked
			case R.id.game_cell_1:
				x = 1;
				y = 1;
				break;
			case R.id.game_cell_2:
				x = 2;
				y = 1;
				break;
			case R.id.game_cell_3:
				x = 3;
				y = 1;
				break;
			case R.id.game_cell_4:
				x = 1;
				y = 2;
				break;
			case R.id.game_cell_5:
				x = 2;
				y = 2;
				break;
			case R.id.game_cell_6:
				x = 3;
				y = 2;
				break;
			case R.id.game_cell_7:
				x = 1;
				y = 3;
				break;
			case R.id.game_cell_8:
				x = 2;
				y = 3;
				break;
			case R.id.game_cell_9:
			default:
				x = 3;
				y = 3;
			}
			sendMove(x, y);
		}

	}
}
