package com.example.nova;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.vosk.LogLevel;
import org.vosk.LibVosk;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyVoskService implements AutoCloseable, RecognitionListener {

    private static final String TAG = "MyVoskService";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private volatile Model model;
    private volatile SpeechService speechService;
    private volatile Recognizer recognizer;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicBoolean listening = new AtomicBoolean(false);

    // Silence watchdog state
    private final Handler watchdog = new Handler(Looper.getMainLooper());
    private static final long SILENCE_TIMEOUT_MS = 900;
    private volatile String lastPartial = "";
    private volatile long lastPartialTimestamp = 0L;
    private volatile boolean finalDispatched = false;

    /** Single nested listener interface (do NOT duplicate). */
    public interface Listener {
        void onPartialResult(String hypothesis);
        void onFinalResult(String result);
        void onReady();
        void onError(Exception e);
    }

    public MyVoskService(Context context, Listener listener) {
        this.listener = listener;
        LibVosk.setLogLevel(LogLevel.INFO);

        final String assetsModelDir = "vosk-model-small-en-us-0.15";
        StorageService.unpack(
                context,
                assetsModelDir,
                "model",
                unpackedModel -> {
                    model = unpackedModel;
                    ready.set(true);
                    if (this.listener != null) main.post(this.listener::onReady);
                },
                exception -> {
                    if (this.listener != null) {
                        main.post(() -> this.listener.onError(exception));
                    }
                }
        );
    }

    /** Public: start listening (safe to call repeatedly). */
    public void startListening() {
        if (!ready.get() || model == null) {
            Log.w(TAG, "startListening before ready");
            return;
        }
        stopInternal();
        try {
            recognizer = new Recognizer(model, 16000.0f);
            speechService = new SpeechService(recognizer, 16000.0f);
            resetUtteranceState();
            speechService.startListening(this);
            listening.set(true);
            scheduleWatchdog();
        } catch (IOException e) {
            Log.e(TAG, "Error starting speech service", e);
            listening.set(false);
            if (listener != null) main.post(() -> listener.onError(e));
        }
    }

    /** Public: stop listening, but keep model loaded. */
    public void stopListening() {
        stopInternal();
        listening.set(false);
    }

    /** Internal stop (does not flip 'ready'). */
    private void stopInternal() {
        SpeechService ss = speechService;
        if (ss != null) {
            try { ss.stop(); } catch (Throwable ignored) {}
            try { ss.shutdown(); } catch (Throwable ignored) {}
        }
        speechService = null;
        recognizer = null;
        watchdog.removeCallbacksAndMessages(null);
    }

    public boolean isListening() { return listening.get(); }

    private void resetUtteranceState() {
        lastPartial = "";
        lastPartialTimestamp = 0L;
        finalDispatched = false;
        watchdog.removeCallbacksAndMessages(null);
    }

    @Override public void onPartialResult(String hypothesis) {
        try {
            JSONObject obj = (JSONObject) new JSONParser().parse(hypothesis);
            String partial = (String) obj.get("partial");
            if (partial == null) return;

            if (listener != null) {
                final String show = partial;
                main.post(() -> listener.onPartialResult(show));
            }

            if (!partial.trim().isEmpty()) {
                lastPartial = partial.trim();
                lastPartialTimestamp = System.currentTimeMillis();
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse partial error", e);
        }
    }

    @Override public void onFinalResult(String result) {
        try {
            JSONObject obj = (JSONObject) new JSONParser().parse(result);
            String text = (String) obj.get("text");
            if (text != null && !text.isEmpty() && listener != null) {
                finalDispatched = true;
                main.post(() -> listener.onFinalResult(text));
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse final error", e);
        }
        restartListening();
    }

    private void restartListening() {
        watchdog.removeCallbacksAndMessages(null);
        if (listening.get()) {
            main.postDelayed(this::startListening, 50);
        }
    }

    @Override public void onResult(String hypothesis) { /* unused */ }

    @Override public void onError(Exception exception) {
        Log.e(TAG, "Recognizer error", exception);
        if (listener != null) main.post(() -> listener.onError(exception));
        restartListening();
    }

    @Override public void onTimeout() {
        Log.i(TAG, "Recognizer timed out.");
        if (!finalDispatched && lastPartial != null && !lastPartial.isEmpty() && listener != null) {
            final String promote = lastPartial;
            finalDispatched = true;
            main.post(() -> listener.onFinalResult(promote));
        }
        restartListening();
    }

    @Override public void close() {
        if (closing.getAndSet(true)) return;
        listening.set(false);
        stopInternal();
        if (model != null) {
            try { model.close(); } catch (Throwable ignored) {}
            model = null;
        }
    }

    private void scheduleWatchdog() {
        watchdog.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    if (recognizer == null || speechService == null || !listening.get()) return;

                    long now = System.currentTimeMillis();
                    if (!finalDispatched
                            && lastPartial != null
                            && !lastPartial.isEmpty()
                            && lastPartialTimestamp > 0
                            && (now - lastPartialTimestamp) >= SILENCE_TIMEOUT_MS) {

                        Log.d(TAG, "Silence watchdog -> promote: " + lastPartial);
                        finalDispatched = true;
                        if (listener != null) {
                            final String promote = lastPartial;
                            main.post(() -> listener.onFinalResult(promote));
                        }
                        restartListening();
                        return;
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "watchdog loop error", t);
                }
                watchdog.postDelayed(this, 150);
            }
        }, 150);
    }
}
