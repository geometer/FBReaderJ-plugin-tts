package org.geometerplus.fbreader.plugin.tts;

import java.util.HashMap;
import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.Window;

import org.geometerplus.android.fbreader.api.*;

public class SpeakActivity extends Activity implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener {
	private Api myApi;

	private static final int CHECK_TTS_INSTALLED = 0;
	private static final String PARAGRAPHUTTERANCE = "PARAGRAPHUTTERANCE";

	static final int CURRENTORFORWARD = 0;
	static final int SEARCHFORWARD = 1;
	static final int SEARCHBACKWARD = 2;

	private TextToSpeech myTTS;

	private int myParagraphIndex = -1;
	private int myParagraphsNumber;

	private boolean myIsActive = false;

	private PhoneStateListener mPhoneListener = new PhoneStateListener() {
		public void onCallStateChanged(int state, String incomingNumber) {
			if (state == TelephonyManager.CALL_STATE_RINGING) {
				stopTalking();
				finish();
			}
		}
	};

	private View.OnClickListener forwardListener = new View.OnClickListener() {
		public void onClick(View v) {
			stopTalking();
			nextParagraphString(false, SEARCHFORWARD);
		}
	};

	private View.OnClickListener backListener = new View.OnClickListener() {
		public void onClick(View v) {
			stopTalking();
			nextParagraphString(false, SEARCHBACKWARD);
		}
	};

	private View.OnClickListener pauseListener = new View.OnClickListener() {
		public void onClick(View v) {
			stopTalking();
			myIsActive = false;
		}
	};

	private View.OnClickListener stopListener = new View.OnClickListener() {
		public void onClick(View v) {
			stopTalking();
			finish();
		}
	};

	private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		myApi = new ApiServiceConnection(this);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.control_panel);

		findViewById(R.id.button_previous_paragraph).setOnClickListener(backListener);
		findViewById(R.id.button_next_paragraph).setOnClickListener(forwardListener);
		findViewById(R.id.button_close).setOnClickListener(stopListener);
		findViewById(R.id.button_pause).setOnClickListener(pauseListener);
		findViewById(R.id.button_play).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setActive(true);
				if (myIsActive) {
					nextParagraphString(true, CURRENTORFORWARD);
				}
			}
		});

		setActive(false);

		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);

		Intent checkIntent = new Intent();
		checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
		startActivityForResult(checkIntent, CHECK_TTS_INSTALLED);
	}

	@Override
	protected void onResume() {
		myApi.connect();
		super.onResume();
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CHECK_TTS_INSTALLED) {
			if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
				// success, create the TTS instance
				myTTS = new TextToSpeech(this, this);
			} else {
				// missing data, install it
				Intent installIntent = new Intent();
				installIntent.setAction(
					TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
				startActivity(installIntent);
			}
		}
	}

	private String getParagraphText(int paragraphIndex) {
		try {
			return myApi.getParagraphText(paragraphIndex);
		} catch (ApiException e) {
			e.printStackTrace();
			return "";
		}
	}

	private void setActive(boolean active) {
		if (active && myParagraphIndex == -1) {
			try {
				myParagraphIndex = myApi.getPageStart().ParagraphIndex;
				myParagraphsNumber = myApi.getParagraphsNumber();
			} catch (ApiException e) {
				e.printStackTrace();
			}
			active = myParagraphIndex != -1;
		}

		myIsActive = active;

		runOnUiThread(new Runnable() {
			public void run() {
				findViewById(R.id.button_play).setVisibility(myIsActive ? View.GONE : View.VISIBLE);
				findViewById(R.id.button_pause).setVisibility(myIsActive ? View.VISIBLE : View.GONE);
			}
		});
	}

	private void speakString(String s) {
		setActive(true);

		HashMap<String, String> callbackMap = new HashMap<String, String>();
		callbackMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, PARAGRAPHUTTERANCE);

		myTTS.speak(s, TextToSpeech.QUEUE_FLUSH, callbackMap);
	}

	private String lookForValidParagraphString(int direction) {
		String s = "";
		while (s.equals("") && myParagraphIndex != -1) {
			switch (direction) {
				case SEARCHFORWARD:
					++myParagraphIndex;
					break;
				case SEARCHBACKWARD:
					--myParagraphIndex;
					break;
				case CURRENTORFORWARD:
					direction = SEARCHFORWARD;
					break;
			}
			s = getParagraphText(myParagraphIndex);
		}
		return s;
	}

	private void nextParagraphString(boolean speak, int direction) {
		if (myParagraphIndex >= myParagraphsNumber - 1) {
			stopTalking();
			return;
		}

		String s = lookForValidParagraphString(direction);

		try {
			if (!myApi.isPageEndOfText()) {
				myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
			}
		} catch (ApiException e) {
			e.printStackTrace();
		}
		if (speak) {
			speakString(s);
		}
	}

	@Override
	protected void onDestroy() {
		setActive(false);
		if (myTTS != null) {
			myTTS.shutdown();
		}
		super.onDestroy();
	}

	private void stopTalking() {
		setActive(false);
		if (myTTS != null) {
			if (myTTS.isSpeaking()) {
				myTTS.stop();
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		stopTalking();
		myApi.disconnect();
		super.onStop();
	}

	@Override
	public void onBackPressed() {
		stopTalking();
		super.onBackPressed();
	}

	@Override
	public void onInit(int status) {
		myTTS.setOnUtteranceCompletedListener(this);
		try {
			Locale locale = new Locale(myApi.getBookLanguage());
			if (myTTS.isLanguageAvailable(locale) < 0) {
				locale = Locale.getDefault();
			}
			myTTS.setLanguage(locale);
		} catch (ApiException e) {
			e.printStackTrace();
		}
		setActive(true);
		nextParagraphString(true, CURRENTORFORWARD);
	}

	@Override
	public void onUtteranceCompleted(String uttId) {
		if (myIsActive && uttId.equals(PARAGRAPHUTTERANCE)) {
			nextParagraphString(true, SEARCHFORWARD);
		} else {
			setActive(false);
		}
	}
}
