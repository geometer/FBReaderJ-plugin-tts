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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Toast;

import org.geometerplus.android.fbreader.api.*;

public class SpeakActivity extends Activity implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener, ApiClientImplementation.ConnectionListener {
	private ApiClientImplementation myApi;

	private static final String UTTERANCE_ID = "FBReaderTTSPlugin";

	private TextToSpeech myTTS;

	private int myParagraphIndex = -1;
	private int myParagraphsNumber;

	private boolean myIsActive = false;

	private void setListener(int id, View.OnClickListener listener) {
		findViewById(id).setOnClickListener(listener);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

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
				speakString(gotoNextParagraph());
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

		myApi = new ApiClientImplementation(this, this);
		try {
			startActivityForResult(
				new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA), 0
			);
		} catch (ActivityNotFoundException e) {
			showMessage(getText(R.string.no_tts_installed));
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
			myTTS = new TextToSpeech(this, this);
		} else {
			startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
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
	protected void onDestroy() {
		if (myTTS != null) {
			myTTS.shutdown();
		}
		super.onDestroy();
	}

	private volatile int myInitializationStatus;
	private static int API_INITIALIZED = 1;
	private static int TTS_INITIALIZED = 2;
	private static int FULLY_INITIALIZED = API_INITIALIZED | TTS_INITIALIZED;

	public void onConnected() {
		if (myInitializationStatus != FULLY_INITIALIZED) {
			myInitializationStatus |= API_INITIALIZED;
			if (myInitializationStatus == FULLY_INITIALIZED) {
				doFinalInitialization();
			}
		}
	}

	public void onInit(int status) {
		if (myInitializationStatus != FULLY_INITIALIZED) {
			myInitializationStatus |= TTS_INITIALIZED;
			if (myInitializationStatus == FULLY_INITIALIZED) {
				doFinalInitialization();
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

	private void doFinalInitialization() {
		myTTS.setOnUtteranceCompletedListener(this);

		try {
			setTitle(myApi.getBookTitle());

			Locale locale = null;
			final String language = myApi.getBookLanguage();
			if ("other".equals(language)) {
				locale = Locale.getDefault();
				if (myTTS.isLanguageAvailable(locale) < 0) {
					locale = Locale.ENGLISH;
				}
				showMessage(
					getText(R.string.language_is_not_set).toString()
						.replace("%0", locale.getDisplayLanguage())
				);
			} else {
				final String languageCode = myApi.getBookLanguage();
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
					showMessage(
						getText(R.string.no_data_for_language).toString()
							.replace("%0", originalLocale != null
								? originalLocale.getDisplayLanguage() : languageCode)
							.replace("%1", locale.getDisplayLanguage())
					);
				}
			}
			myTTS.setLanguage(locale);

			myParagraphIndex = myApi.getPageStart().ParagraphIndex;
			myParagraphsNumber = myApi.getParagraphsNumber();
			setActionsEnabled(true);
			setActive(true);
			speakString(gotoNextParagraph());
		} catch (ApiException e) {
			setActionsEnabled(false);
			showMessage(getText(R.string.initialization_error));
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

	private void showMessage(final CharSequence text) {
		runOnUiThread(new Runnable() {
			public void run() {
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
