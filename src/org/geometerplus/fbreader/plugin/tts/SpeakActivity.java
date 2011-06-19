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

import org.geometerplus.android.fbreader.api.*;

public class SpeakActivity extends Activity implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener {
	private Api myApi;

	private static final String UTTERANCE_ID = "FBReaderTTSPlugin";

	static final int CURRENTORFORWARD = 0;
	static final int SEARCHFORWARD = 1;

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

	private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}

	private void highlightParagraph() throws ApiException {
		if (0 <= myParagraphIndex && myParagraphIndex < myParagraphsNumber) {
			myApi.highlightArea(
				new TextPosition(myParagraphIndex, 0, 0),
				new TextPosition(myParagraphIndex, Integer.MAX_VALUE, 0)
			);
		} else {
			myApi.clearHighlighting();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		myApi = new ApiClientImplementation(this);

		setContentView(R.layout.control_panel);

		setListener(R.id.button_previous_paragraph, new View.OnClickListener() {
			public void onClick(View v) {
				stopTalking();
				try {
					for (int i = myParagraphIndex - 1; i >= 0; --i) {
						if (myApi.getParagraphText(i).length() > 0) {
							myParagraphIndex = i;
							break;
						}
					}
					if (myApi.getPageStart().ParagraphIndex >= myParagraphIndex) {
						myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
					}
					highlightParagraph();
					findViewById(R.id.button_next_paragraph).setEnabled(true);
					findViewById(R.id.button_play).setEnabled(true);
				} catch (ApiException e) {
					e.printStackTrace();
				}
			}
		});
		setListener(R.id.button_next_paragraph, new View.OnClickListener() {
			public void onClick(View v) {
				stopTalking();
				nextParagraphString(SEARCHFORWARD);
			}
		});
		setListener(R.id.button_close, new View.OnClickListener() {
			public void onClick(View v) {
				stopTalking();
				finish();
			}
		});
		setListener(R.id.button_pause, new View.OnClickListener() {
			public void onClick(View v) {
				stopTalking();
			}
		});
		setListener(R.id.button_play, new View.OnClickListener() {
			public void onClick(View v) {
				setActive(true);
				if (myIsActive) {
					speakString(nextParagraphString(CURRENTORFORWARD));
				}
			}
		});

		setActive(false);

		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);

		startActivityForResult(
			new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA), 0
		);
	}

	@Override
	protected void onResume() {
		myApi.connect();
		super.onResume();
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
			// success, create the TTS instance
			myTTS = new TextToSpeech(this, this);
		} else {
			// missing data, install it
			startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
		}
	}

	private void setActive(final boolean active) {
		if (active && myParagraphIndex == -1) {
			try {
				myParagraphIndex = myApi.getPageStart().ParagraphIndex;
				myParagraphsNumber = myApi.getParagraphsNumber();
			} catch (ApiException e) {
				e.printStackTrace();
			}
			if (myParagraphIndex == -1) {
				return;
			}
		}

		myIsActive = active;

		runOnUiThread(new Runnable() {
			public void run() {
				findViewById(R.id.button_play).setVisibility(active ? View.GONE : View.VISIBLE);
				findViewById(R.id.button_pause).setVisibility(active ? View.VISIBLE : View.GONE);
			}
		});
	}

	private void speakString(String text) {
		setActive(true);

		HashMap<String, String> callbackMap = new HashMap<String, String>();
		callbackMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);

		myTTS.speak(text, TextToSpeech.QUEUE_FLUSH, callbackMap);
	}

	private String nextParagraphString(int direction) {
		if (myParagraphIndex == -1) {
			return "";
		}

		try {
			if (direction == SEARCHFORWARD) {
				++myParagraphIndex;
			}
			String text = "";
			for (; myParagraphIndex < myParagraphsNumber; ++myParagraphIndex) {
				final String s = myApi.getParagraphText(myParagraphIndex);
				if (s.length() > 0) {
					text = s;
					break;
				}
			}
			if (!myApi.isPageEndOfText()) {
				myApi.setPageStart(new TextPosition(myParagraphIndex, 0, 0));
			}
			highlightParagraph();
			if (myParagraphIndex >= myParagraphsNumber) {
				runOnUiThread(new Runnable() {
					public void run() {
						findViewById(R.id.button_next_paragraph).setEnabled(false);
						findViewById(R.id.button_play).setEnabled(false);
					}
				});
			}
			return text;
		} catch (ApiException e) {
			e.printStackTrace();
			return "";
		}
	}

	@Override
	protected void onDestroy() {
		if (myTTS != null) {
			setActive(false);
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
		try {
			myApi.clearHighlighting();
		} catch (ApiException e) {
			e.printStackTrace();
		}
		myApi.disconnect();
		super.onStop();
	}

	@Override
	public void onInit(int status) {
		myTTS.setOnUtteranceCompletedListener(this);
		try {
			setTitle(myApi.getBookTitle());
		} catch (ApiException e) {
			e.printStackTrace();
			finish();
			return;
		}

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
		speakString(nextParagraphString(CURRENTORFORWARD));
	}

	@Override
	public void onUtteranceCompleted(String uttId) {
		if (myIsActive && UTTERANCE_ID.equals(uttId)) {
			speakString(nextParagraphString(SEARCHFORWARD));
			if (myParagraphIndex >= myParagraphsNumber) {
				stopTalking();
			}
		} else {
			setActive(false);
		}
	}
}
