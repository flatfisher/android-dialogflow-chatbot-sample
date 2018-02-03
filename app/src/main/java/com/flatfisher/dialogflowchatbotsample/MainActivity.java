package com.flatfisher.dialogflowchatbotsample;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.ChatView;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.api.AIServiceException;
import ai.api.RequestExtras;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.android.GsonFactory;
import ai.api.model.AIContext;
import ai.api.model.AIError;
import ai.api.model.AIEvent;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Metadata;
import ai.api.model.Result;
import ai.api.model.Status;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = MainActivity.class.getName();
    private Gson gson = GsonFactory.getGson();
    private AIDataService aiDataService;
    private ChatView chatView;
    private User myAccount;
    private User droidKaigiBot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initChatView();

        //Language, Dialogflow Client access token 
        final LanguageConfig config = new LanguageConfig("ja", "xxx");
        initService(config);
    }

    @Override
    public void onClick(View v) {
        //new message
        final Message message = new Message.Builder()
                .setUser(myAccount)
                .setRightMessage(true)
                .setMessageText(chatView.getInputText())
                .hideIcon(true)
                .build();
        //Set to chat view
        chatView.send(message);
        sendRequest(chatView.getInputText());
        //Reset edit text
        chatView.setInputText("");
    }

    /*
    * AIRequest should have query OR event
    */
    private void sendRequest(String text) {
        Log.d(TAG, text);
        final String queryString = String.valueOf(text);
        final String eventString = null;
        final String contextString = null;

        if (TextUtils.isEmpty(queryString) && TextUtils.isEmpty(eventString)) {
            onError(new AIError(getString(R.string.non_empty_query)));
            return;
        }

        new AiTask().execute(queryString, eventString, contextString);
    }

    public class AiTask extends AsyncTask<String, Void, AIResponse> {
        private AIError aiError;

        @Override
        protected AIResponse doInBackground(final String... params) {
            final AIRequest request = new AIRequest();
            String query = params[0];
            String event = params[1];
            String context = params[2];

            if (!TextUtils.isEmpty(query)){
                request.setQuery(query);
            }

            if (!TextUtils.isEmpty(event)){
                request.setEvent(new AIEvent(event));
            }

            RequestExtras requestExtras = null;
            if (!TextUtils.isEmpty(context)) {
                final List<AIContext> contexts = Collections.singletonList(new AIContext(context));
                requestExtras = new RequestExtras(contexts, null);
            }

            try {
                return aiDataService.request(request, requestExtras);
            } catch (final AIServiceException e) {
                aiError = new AIError(e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(final AIResponse response) {
            if (response != null) {
                onResult(response);
            } else {
                onError(aiError);
            }
        }
    }


    private void onResult(final AIResponse response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Variables
                gson.toJson(response);
                final Status status = response.getStatus();
                final Result result = response.getResult();
                final String speech = result.getFulfillment().getSpeech();
                final Metadata metadata = result.getMetadata();
                final HashMap<String, JsonElement> params = result.getParameters();

                // Logging
                Log.d(TAG, "onResult");
                Log.i(TAG, "Received success response");
                Log.i(TAG, "Status code: " + status.getCode());
                Log.i(TAG, "Status type: " + status.getErrorType());
                Log.i(TAG, "Resolved query: " + result.getResolvedQuery());
                Log.i(TAG, "Action: " + result.getAction());
                Log.i(TAG, "Speech: " + speech);

                if (metadata != null) {
                    Log.i(TAG, "Intent id: " + metadata.getIntentId());
                    Log.i(TAG, "Intent name: " + metadata.getIntentName());
                }

                if (params != null && !params.isEmpty()) {
                    Log.i(TAG, "Parameters: ");
                    for (final Map.Entry<String, JsonElement> entry : params.entrySet()) {
                        Log.i(TAG, String.format("%s: %s",
                                entry.getKey(), entry.getValue().toString()));
                    }
                }

                //Update view to bot says
                final Message receivedMessage = new Message.Builder()
                        .setUser(droidKaigiBot)
                        .setRightMessage(false)
                        .setMessageText(speech)
                        .build();
                chatView.receive(receivedMessage);
            }
        });
    }

    private void onError(final AIError error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG,error.toString());
            }
        });
    }

    private void initChatView() {
        int myId = 0;
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_action_user);
        String myName = "Fish";
        myAccount = new User(myId, myName, icon);

        int botId = 1;
        String botName = "DroidKaigi";
        droidKaigiBot = new User(botId, botName, icon);

        chatView = findViewById(R.id.chat_view);
        chatView.setRightBubbleColor(ContextCompat.getColor(this, R.color.green500));
        chatView.setLeftBubbleColor(Color.WHITE);
        chatView.setBackgroundColor(ContextCompat.getColor(this, R.color.blueGray500));
        chatView.setSendButtonColor(ContextCompat.getColor(this, R.color.lightBlue500));
        chatView.setSendIcon(R.drawable.ic_action_send);
        chatView.setRightMessageTextColor(Color.WHITE);
        chatView.setLeftMessageTextColor(Color.BLACK);
        chatView.setUsernameTextColor(Color.WHITE);
        chatView.setSendTimeTextColor(Color.WHITE);
        chatView.setDateSeparatorColor(Color.WHITE);
        chatView.setInputTextHint("new message...");
        chatView.setMessageMarginTop(5);
        chatView.setMessageMarginBottom(5);
        chatView.setOnClickSendButtonListener(this);
    }

    private void initService(final LanguageConfig languageConfig) {
        final AIConfiguration.SupportedLanguages lang =
                AIConfiguration.SupportedLanguages.fromLanguageTag(languageConfig.getLanguageCode());
        final AIConfiguration config = new AIConfiguration(languageConfig.getAccessToken(),
                lang,
                AIConfiguration.RecognitionEngine.System);
        aiDataService = new AIDataService(this, config);
    }
}