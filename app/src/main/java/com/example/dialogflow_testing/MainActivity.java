package com.example.dialogflow_testing;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2beta1.DetectIntentResponse;
import com.google.cloud.dialogflow.v2beta1.QueryInput;
import com.google.cloud.dialogflow.v2beta1.SessionName;
import com.google.cloud.dialogflow.v2beta1.SessionsClient;
import com.google.cloud.dialogflow.v2beta1.SessionsSettings;
import com.google.cloud.dialogflow.v2beta1.TextInput;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int USER = 10001;
    private static final int BOT = 10002;

    private String uuid = UUID.randomUUID().toString();
    private LinearLayout chatLayout;
    private EditText queryEditText;

    // Java V2
    private SessionsClient sessionsClient;
    private SessionName session;

    private SpeechRecognizerManager mSpeechManager;
    private String resultString;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ScrollView scrollview = findViewById(R.id.chatScrollView);
        scrollview.post(() -> scrollview.fullScroll(ScrollView.FOCUS_DOWN));

        chatLayout = findViewById(R.id.chatLayout);

        ImageView sendBtn = findViewById(R.id.sendBtn);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        sendBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                audioMessage();
                return true;
            }
        });

        queryEditText = findViewById(R.id.queryEditText);
        queryEditText.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        sendMessage();
                        return true;
                    default:
                        break;
                }
            }
            return false;
        });

        // Java V2
        initV2Chatbot();
    }

    private void initV2Chatbot() {
        try {
            InputStream stream = getResources().openRawResource(R.raw.mq_agent);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream);
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();

            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
            sessionsClient = SessionsClient.create(sessionsSettings);
            session = SessionName.of(projectId, uuid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String msg = queryEditText.getText().toString();
        if (msg.trim().isEmpty()) {
            Toast.makeText(MainActivity.this, "message is empty!", Toast.LENGTH_LONG).show();
        } else {
            showTextView(msg, USER);
            queryEditText.setText("");

            // Java V2
            QueryInput queryInput = QueryInput.newBuilder().setText(TextInput.newBuilder().setText(msg).setLanguageCode("en-US")).build();
            new RequestJavaV2Task(MainActivity.this, session, sessionsClient, queryInput).execute();
        }
        if(mSpeechManager!=null) {
            resultString = "";
            mSpeechManager.destroy();
            mSpeechManager = null;
        }
    }

    private void audioMessage() {
        if (PermissionHandler.checkPermission(this, PermissionHandler.RECORD_AUDIO)) {
            Toast.makeText(this, "SPEAK NOW", Toast.LENGTH_SHORT).show();
            if (mSpeechManager == null) {
                SetSpeechListener();
            } else if (!mSpeechManager.ismIsListening()) {
                mSpeechManager.destroy();
                SetSpeechListener();
            }
        }
        else {
            PermissionHandler.askForPermission(PermissionHandler.RECORD_AUDIO, this);
        }
    }

    private void SetSpeechListener() {
        mSpeechManager = new SpeechRecognizerManager(this, new SpeechRecognizerManager.onResultsReady() {
            @Override
            public void onResults(ArrayList<String> results) {
                if (results != null && results.size() > 0) {
                    if (results.size() == 1) {
                        mSpeechManager.destroy();
                        mSpeechManager = null;
                        resultString = results.get(0);
                        //queryEditText.setText(resultString);
                    } else {
                        StringBuilder sb = new StringBuilder();
                        if (results.size() > 5) {
                            results = (ArrayList<String>) results.subList(0, 5);
                        }
                        for (String result : results) {
                            sb.append(result).append("\n");
                        }
                        //resultString = sb.toString();
                        resultString = results.get(0);

                        queryEditText.setText(resultString);

                        if(mSpeechManager!=null) {
                            mSpeechManager.destroy();
                            mSpeechManager = null;
                        }
                        if (resultString.trim().isEmpty()) {
                            Toast.makeText(MainActivity.this, "Please enter your query!", Toast.LENGTH_LONG).show();
                        } else {
                            showTextView(resultString, USER);
                            queryEditText.setText("");
                            // Java V2
                            QueryInput queryInput = QueryInput.newBuilder().setText(TextInput.newBuilder().setText(resultString).setLanguageCode("en-US")).build();
                            new RequestJavaV2Task(MainActivity.this, session, sessionsClient, queryInput).execute();
                        }
                    }
                } else {
                    resultString = "No results found";
                    //queryEditText.setText(resultString);
                }
            }
        });
    }

    public void callbackV2(DetectIntentResponse response) {
        if (response != null) {
            // process aiResponse here
            String botReply = response.getQueryResult().getFulfillmentText();
            Log.d(TAG, "V2 Bot Reply: " + botReply);
            showTextView(botReply, BOT);
        } else {
            Log.d(TAG, "Bot Reply: Null");
            showTextView("There was some communication issue. Please Try again!", BOT);
        }
    }

    private void showTextView(String message, int type) {
        FrameLayout layout;
        switch (type) {
            case USER:
                layout = getUserLayout();
                break;
            case BOT:
                layout = getBotLayout();
                break;
            default:
                layout = getBotLayout();
                break;
        }
        layout.setFocusableInTouchMode(true);
        chatLayout.addView(layout); // move focus to text view to automatically make it scroll up if softfocus
        TextView tv = layout.findViewById(R.id.chatMsg);
        tv.setText(message);
        layout.requestFocus();
        queryEditText.requestFocus(); // change focus back to edit text to continue typing
    }

    FrameLayout getUserLayout() {
        LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        return (FrameLayout) inflater.inflate(R.layout.user_msg_layout, null);
    }

    FrameLayout getBotLayout() {
        LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        return (FrameLayout) inflater.inflate(R.layout.bot_msg_layout, null);
    }
}
