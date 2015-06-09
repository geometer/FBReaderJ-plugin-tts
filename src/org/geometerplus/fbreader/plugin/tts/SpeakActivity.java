/*
 * Copyright (C) 2009-2011 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.plugin.tts;

import java.util.HashMap;
import java.util.Locale;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Toast;
import android.widget.SeekBar;

import org.geometerplus.android.fbreader.api.*;

public class SpeakActivity extends Activity implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener, ApiClientImplementation.ConnectionListener {
	private ApiClientImplementation myApi;

	private static final String UTTERANCE_ID = "FBReaderTTSPlugin";

	private TextToSpeech myTTS;

	private SharedPreferences myPreferences;

	private int myParagraphIndex = -1;
	private int myParagraphsNumber;

	private boolean myIsActive = false;

	private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		myPreferences = getSharedPreferences("FBReaderTTS", MODE_PRIVATE);

		setContentView(R.layout.control_panel);

		setListener(R.id.button_previous_paragraph, new View.OnClickListener() {
			public void onClick(View v) {
				stopTalking();
				gotoPreviousParagraph();
			}
		});
		setListener(R.id.button_next_paragraph, new View.OnClickListener() {
			public void onClick(View v) {
				stopTalking();
				if (myParagraphIndex < myParagraphsNumber) {
					++myParagraphIndex;
					gotoNextParagraph();
				}
			}
		});
		setListener(R.id.button_close, new View.OnClickListener() {
			public void onClick(View v) {
				switchOff();
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
				speakString(gotoNextParagraph());
			}
		});
		final SeekBar speedControl = (SeekBar)findViewById(R.id.speed_control);
		speedControl.setMax(200);
		speedControl.setProgress(myPreferences.getInt("rate", 100));
		speedControl.setEnabled(false);
		speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			private SharedPreferences.Editor myEditor = myPreferences.edit();

			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (myTTS != null) {
					setSpeechRate(progress);
					myEditor.putInt("rate", progress);
				}
			}

			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				myEditor.commit();
			}
		});

		((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).listen(
			new PhoneStateListener() {
				public void onCallStateChanged(int state, String incomingNumber) {
					if (state == TelephonyManager.CALL_STATE_RINGING) {
						stopTalking();
					}
				}
			},
			PhoneStateListener.LISTEN_CALL_STATE
		);

		setActive(false);
		setActionsEnabled(false);

		String prefix = ApiClientImplementation.FBREADER_PREFIX;
		final Intent intent = getIntent();
		if (intent != null) {
			final String action = getIntent().getAction();
			if (action != null && action.endsWith(PluginApi.ACTION_RUN_POSTFIX)) {
				prefix = action.substring(0, action.length() - PluginApi.ACTION_RUN_POSTFIX.length());
			}
		}
		myApi = new ApiClientImplementation(this, this, prefix);
		try {
			startActivityForResult(
				new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA), 0
			);
		} catch (ActivityNotFoundException e) {
			showErrorMessage(getText(R.string.no_tts_installed), true);
		}

		setTitle(R.string.initializing);
	}

	private void setSpeechRate(int progress) {
		if (myTTS != null) {
			myTTS.setSpeechRate((float)Math.pow(2.0, (progress - 100.0) / 75));
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
			myTTS = new TextToSpeech(this, this);
		} else {
			try {
				startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
			} catch (ActivityNotFoundException e) {
				showErrorMessage(getText(R.string.no_tts_installed), true);
			}
		}
	}

	@Override
	protected void onResume() {
		myApi.connect();
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void switchOff() {
		stopTalking();
		try {
			myApi.clearHighlighting();
		} catch (ApiException e) {
			e.printStackTrace();
		}
		myApi.disconnect();
		if (myTTS != null) {
			myTTS.shutdown();
			myTTS = null;
		}
	}

	@Override
	protected void onDestroy() {
		switchOff();
		super.onDestroy();
	}

	private volatile int myInitializationStatus;
	private static int API_INITIALIZED = 1;
	private static int TTS_INITIALIZED = 2;
	private static int FULLY_INITIALIZED = API_INITIALIZED | TTS_INITIALIZED;

	// implements ApiClientImplementation.ConnectionListener
	public void onConnected() {
		if (myInitializationStatus != FULLY_INITIALIZED) {
			myInitializationStatus |= API_INITIALIZED;
			if (myInitializationStatus == FULLY_INITIALIZED) {
				onInitializationCompleted();
			}
		}
	}

	// implements TextToSpeech.OnInitListener
	public void onInit(int status) {
		if (myInitializationStatus != FULLY_INITIALIZED) {
			myInitializationStatus |= TTS_INITIALIZED;
			if (myInitializationStatus == FULLY_INITIALIZED) {
				onInitializationCompleted();
			}
		}
	}

	private void setActionsEnabled(final boolean enabled) {
		runOnUiThread(new Runnable() {
			public void run() {
				findViewById(R.id.button_previous_paragraph).setEnabled(enabled);
				findViewById(R.id.button_next_paragraph).setEnabled(enabled);
				findViewById(R.id.button_play).setEnabled(enabled);
			}
		});
	}

	private String getDisplayLanguage(Locale locale, String defaultValue) {
		if (locale == null) {
			return defaultValue;
		}
		String language = locale.getDisplayLanguage();
		if (language != null) {
			return language;
		}
		language = locale.getLanguage();
		return language != null ? language : defaultValue;
	}

	private void onInitializationCompleted() {
		myTTS.setOnUtteranceCompletedListener(this);

		try {
			setTitle(myApi.getBookTitle());

			Locale locale = null;
			final String languageCode = myApi.getBookLanguage();
			if (languageCode == null || "other".equals(languageCode)) {
				locale = Locale.getDefault();
				if (myTTS.isLanguageAvailable(locale) < 0) {
					locale = Locale.ENGLISH;
				}
				showErrorMessage(
					getText(R.string.language_is_not_set).toString()
						.replace("%0", getDisplayLanguage(locale, "???")),
					false
				);
			} else {
				try {
					locale = new Locale(languageCode);
				} catch (Exception e) {
				}
				if (locale == null || myTTS.isLanguageAvailable(locale) < 0) {
					final Locale originalLocale = locale;
					locale = Locale.getDefault();
					if (myTTS.isLanguageAvailable(locale) < 0) {
						locale = Locale.ENGLISH;
					}
					showErrorMessage(
						getText(R.string.no_data_for_language).toString()
							.replace("%0", getDisplayLanguage(originalLocale, languageCode))
							.replace("%1", getDisplayLanguage(locale, "???")),
						false
					);
				}
			}
			myTTS.setLanguage(locale);

			final SeekBar speedControl = (SeekBar)findViewById(R.id.speed_control);
			speedControl.setEnabled(true);
			setSpeechRate(speedControl.getProgress());

			myParagraphIndex = myApi.getPageStart().ParagraphIndex;
			myParagraphsNumber = myApi.getParagraphsNumber();
			setActionsEnabled(true);
			setActive(true);
			speakString(gotoNextParagraph());
		} catch (ApiException e) {
			setActionsEnabled(false);
			showErrorMessage(getText(R.string.initialization_error), true);
			e.printStackTrace();
		}
	}

	@Override
	public void onUtteranceCompleted(String uttId) {
		if (myIsActive && UTTERANCE_ID.equals(uttId)) {
			++myParagraphIndex;
			speakString(gotoNextParagraph());
			if (myParagraphIndex >= myParagraphsNumber) {
				stopTalking();
			}
		} else {
			setActive(false);
		}
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

	private void stopTalking() {
		setActive(false);
		if (myTTS != null && myTTS.isSpeaking()) {
			myTTS.stop();
		}
	}

	private void showErrorMessage(final CharSequence text, final boolean fatal) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (fatal) {
					setTitle(R.string.failure);
				}
				Toast.makeText(SpeakActivity.this, text, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private volatile PowerManager.WakeLock myWakeLock;

	private synchronized void setActive(final boolean active) {
		myIsActive = active;

		runOnUiThread(new Runnable() {
			public void run() {
				findViewById(R.id.button_play).setVisibility(active ? View.GONE : View.VISIBLE);
				findViewById(R.id.button_pause).setVisibility(active ? View.VISIBLE : View.GONE);
			}
		});

		if (active) {
			if (myWakeLock == null) {
				myWakeLock =
					((PowerManager)getSystemService(POWER_SERVICE))
						.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FBReader TTS plugin");
				myWakeLock.acquire();
			}
		} else {
			if (myWakeLock != null) {
				myWakeLock.release();
				myWakeLock = null;
			}
		}
	}

	private void speakString(String text) {
		HashMap<String, String> callbackMap = new HashMap<String, String>();
		callbackMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
		myTTS.speak(text, TextToSpeech.QUEUE_FLUSH, callbackMap);
	}

	private void gotoPreviousParagraph() {
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
			runOnUiThread(new Runnable() {
				public void run() {
					findViewById(R.id.button_next_paragraph).setEnabled(true);
					findViewById(R.id.button_play).setEnabled(true);
				}
			});
		} catch (ApiException e) {
			e.printStackTrace();
		}
	}

	private String gotoNextParagraph() {
		try {
			String text = "";
			for (; myParagraphIndex < myParagraphsNumber; ++myParagraphIndex) {
				final String s = myApi.getParagraphText(myParagraphIndex);
				if (s.length() > 0) {
					text = s;
					break;
				}
			}
			if (!"".equals(text) && !myApi.isPageEndOfText()) {
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
}
