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

    public interface Listener {
        void onPartialResult(String hypothesis);
        void onFinalResult(String result);
        void onReady();
        void onError(Exception e);
    }

    public MyVoskService(Context context, Listener listener) {
        this.listener = listener;
        LibVosk.setLogLevel(LogLevel.INFO);

        // IMPORTANT: the first arg must EXACTLY match the assets folder name
        final String assetsModelDir = "vosk-model-small-en-us-0.15";

        StorageService.unpack(
                context,
                assetsModelDir,
                "model", // cache dir name
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

    public void startListening() {
        if (!ready.get() || model == null) {
            // Not ready yet — avoid creating Recognizer with null model
            Log.w(TAG, "startListening called before model is ready");
            return;
        }
        stop(); // clean any previous session

        try {
            recognizer = new Recognizer(model, 16000.0f);
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(this);
        } catch (IOException e) {
            Log.e(TAG, "Error starting speech service", e);
            if (listener != null) main.post(() -> listener.onError(e));
        }
    }

    public void stop() {
        SpeechService ss = speechService;
        if (ss != null) {
            try { ss.stop(); } catch (Throwable ignored) {}
            try { ss.shutdown(); } catch (Throwable ignored) {}
        }
        speechService = null;
        recognizer = null;
    }

    @Override public void onPartialResult(String hypothesis) {
        try {
            JSONObject obj = (JSONObject) new JSONParser().parse(hypothesis);
            String partial = (String) obj.get("partial");
            if (partial != null && listener != null) {
                main.post(() -> listener.onPartialResult(partial));
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
                main.post(() -> listener.onFinalResult(text));
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse final error", e);
        }
        // Keep continuous listening
        startListening();
    }

    @Override public void onResult(String hypothesis) { /* unused */ }

    @Override public void onError(Exception exception) {
        Log.e(TAG, "Recognizer error", exception);
        if (listener != null) main.post(() -> listener.onError(exception));
    }

    @Override public void onTimeout() {
        Log.i(TAG, "Recognizer timed out.");
        // Optionally restart
        startListening();
    }

    @Override public void close() {
        if (closing.getAndSet(true)) return;
        stop();
        if (model != null) {
            try { model.close(); } catch (Throwable ignored) {}
            model = null;
        }
    }
}
