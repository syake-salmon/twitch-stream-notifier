package com.syakeapps.tsn.endpoint.application;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpMethods;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.syakeapps.tsn.endpoint.web.MultiReadHttpRequest;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Function implements HttpFunction {
    /* SYSTEM ENVVARS KEY */
    private static final String GCP_PROJECT_ID = "GCP_PROJECT_ID";
    private static final String TWITCH_CLIENT_ID = "TWITCH_CLIENT_ID";
    private static final String SLACK_WEBHOOK_URL = "SLACK_WEBHOOK_URL";
    private static final String DISCORD_WEBHOOK_URL = "DISCORD_WEBHOOK_URL";

    /* FIREBASE */
    private static final String DOCUMENT_ID = "Er5sQcTGNVGdGW7edDew";
    /* FIELDS */
    private static final String TWITCH_TOKEN = "TWITCH_TOKEN";
    private static final String SUBSCRIPTION_SECRET_CALLBACK = "SUBSCRIPTION_SECRET_CALLBACK";
    private static final String SUBSCRIPTION_SECRET_WEBHOOK = "SUBSCRIPTION_SECRET_WEBHOOK";

    /* REQUEST HEADER */
    private static final String TWITCH_EVENTSUB_MESSAGE_TYPE = "Twitch-Eventsub-Message-Type";
    private static final String TWITCH_EVENTSUB_MESSAGE_ID = "Twitch-Eventsub-Message-Id";
    private static final String TWITCH_EVENTSUB_MESSAGE_TIMESTAMP = "Twitch-Eventsub-Message-Timestamp";
    private static final String TWITCH_EVENTSUB_MESSAGE_SIGNATURE = "Twitch-Eventsub-Message-Signature";
    private static final String CLIENT_ID = "Client-Id";
    private static final String AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE = "Content-Type";

    /* REQUEST BODY */
    private static final String EVENT = "event";
    private static final String BROADCASTER_USER_ID = "broadcaster_user_id";
    private static final String BROADCASTER_USER_LOGIN = "broadcaster_user_login";
    private static final String BROADCASTER_USER_NAME = "broadcaster_user_name";
    private static final String STARTED_AT = "started_at";
    private static final String SUBSCRIPTION = "subscription";
    private static final String CONDITION = "condition";
    private static final String CHALLENGE = "challenge";
    private static final String BROADCASTER_ID = "broadcaster_id";
    private static final String USERNAME = "username";
    private static final String CONTENT = "content";
    private static final String EMBEDS = "embeds";
    private static final String URL = "url";
    private static final String COLOR = "color";
    private static final String AUTHOR = "author";
    private static final String NAME = "name";
    private static final String FIELDS = "fields";
    private static final String VALUE = "value";
    private static final String INLINE = "inline";
    private static final String ATTACHMENTS = "attachments";
    private static final String BLOCKS = "blocks";
    private static final String TYPE = "type";
    private static final String TEXT = "text";
    private static final String EMOJI = "emoji";
    private static final String ACCESSORY = "accessory";
    private static final String ACTION_ID = "action_id";

    /* RESPONSE BODY */
    private static final String TITLE = "title";
    private static final String GAME_NAME = "game_name";
    private static final String DATA = "data";

    /* NOTIFICATION TYPE */
    private static final String NOTIFICATION = "notification";
    private static final String WEBHOOK_CALLBACK_VERIFICATION = "webhook_callback_verification";
    private static final String REVOCATION = "revocation";

    /* OTHERS */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String COLORHEX_DANGER = "#A30100";
    private static final String HMAC_SHA256 = "HMacSHA256";
    private static final Logger LOGGER = Logger.getLogger(Function.class.getName());

    @Override
    @SuppressWarnings("unchecked")
    public void service(HttpRequest request, HttpResponse response) throws Exception {

        String projectId = System.getenv(GCP_PROJECT_ID);
        String clientId = System.getenv(TWITCH_CLIENT_ID);
        MultiReadHttpRequest mRequest = new MultiReadHttpRequest(request);

        try {
            // >>>>>>>>>> HTTPメソッドの判別
            LOGGER.info("HTTPメソッドの判別を開始します.");

            String method = request.getMethod();
            if (!isSupportedMethod(method)) {
                LOGGER.warning(String.format("HTTPメソッドが不正です. HTTP_METHOD=[%s]", method));

                response.setStatusCode(HttpURLConnection.HTTP_BAD_METHOD);
                return;
            }

            LOGGER.info("HTTPメソッドの判別が完了しました.");
            // <<<<<<<<<< HTTPメソッドの判別

            // >>>>>>>>>> Firestore初期化
            LOGGER.info("Firestoreの初期化を開始します.");

            Firestore db = initializeFirestore(projectId);
            DocumentReference docRef = db.collection(projectId).document(DOCUMENT_ID);

            LOGGER.info("Firestoreの初期化が完了しました.");
            // <<<<<<<<<< Firestore初期化

            // >>>>>>>>>> リクエストタイプの判別
            LOGGER.info("リクエストタイプの判別を開始します.");

            String msgType = mRequest.getFirstHeader(TWITCH_EVENTSUB_MESSAGE_TYPE).get();

            LOGGER.info(String.format("リクエストタイプの判別が完了しました. MESSAGE_TYPE=[%s]", msgType));
            // <<<<<<<<<< リクエストタイプの判別

            if (msgType.equalsIgnoreCase(NOTIFICATION)) {
                // >>>>>>>>>> Webhook通知のハンドリング
                LOGGER.info("Webhook通知のハンドリングを開始します.");

                // >>>>>>>>>> シグネチャヘッダの妥当性チェック
                LOGGER.info("シグネチャヘッダの妥当性チェックを開始します.");

                // EventSubサブスクリプション秘密鍵を取得する（Webhook用）
                String secret = (String) docRef.get().get().get(SUBSCRIPTION_SECRET_WEBHOOK);
                if (!isValidSignature(HMAC_SHA256, mRequest, secret)) {
                    LOGGER.severe("要求シグネチャヘッダが不正です.");

                    response.setStatusCode(HttpURLConnection.HTTP_FORBIDDEN);
                    return;
                }

                LOGGER.info("シグネチャヘッダの妥当性チェックが完了しました.");
                // <<<<<<<<<< シグネチャヘッダの妥当性チェック

                // >>>>>>>>>> リクエストボディの解析
                LOGGER.info("リクエストボディの解析を開始します.");

                Map<String, Object> event = (Map<String, Object>) new ObjectMapper()
                        .readValue(mRequest.getReader(), new TypeReference<Map<String, Object>>() {
                        }).get(EVENT);

                LOGGER.info("リクエストボディの解析が完了しました.");
                // <<<<<<<<<< リクエストボディの解析

                // >>>>>>>>>> 保存済Twitchトークンの取得
                LOGGER.info("保存済Twitchトークンの取得を開始します.");

                String token = (String) docRef.get().get().get(TWITCH_TOKEN);

                LOGGER.info("保存済Twitchトークンの取得が完了しました.");
                // <<<<<<<<<< 保存済Twitchトークンの取得

                // >>>>>>>>>> 配信開始チャンネル情報の取得
                LOGGER.info("配信開始チャンネル情報の取得を開始します.");

                Map<String, Object> channelInfo = getChannelInfo(token, clientId,
                        (String) event.get(BROADCASTER_USER_ID));

                LOGGER.info("配信開始チャンネル情報の取得が完了しました.");
                // <<<<<<<<<< 配信開始チャンネル情報の取得

                // >>>>>>>>>> Discord通知処理
                LOGGER.info("Discord通知処理を開始します.");

                sendToDiscord(System.getenv(DISCORD_WEBHOOK_URL), (String) event.get(BROADCASTER_USER_NAME),
                        (String) event.get(BROADCASTER_USER_LOGIN), (String) channelInfo.get(TITLE),
                        (String) channelInfo.get(GAME_NAME), (String) event.get(STARTED_AT));

                LOGGER.info("Discord通知処理が完了しました.");
                // <<<<<<<<<< Discord通知処理

                LOGGER.info("Webhook通知のハンドリングが完了しました.");
                response.setStatusCode(HttpURLConnection.HTTP_OK);
                return;
                // <<<<<<<<<< Webhook通知のハンドリング

            } else if (msgType.equalsIgnoreCase(WEBHOOK_CALLBACK_VERIFICATION)) {
                // >>>>>>>>>> サブスクリプション要求コールバックのハンドリング
                LOGGER.info("サブスクリプション要求コールバックのハンドリングを開始します.");

                // >>>>>>>>>> シグネチャヘッダの妥当性チェック
                LOGGER.info("シグネチャヘッダの妥当性チェックを開始します.");

                // EventSubサブスクリプション秘密鍵を取得する（コールバック用）
                String secret = (String) docRef.get().get().get(SUBSCRIPTION_SECRET_CALLBACK);
                if (!isValidSignature(HMAC_SHA256, mRequest, secret)) {
                    LOGGER.severe("要求シグネチャヘッダが不正です.");

                    response.setStatusCode(HttpURLConnection.HTTP_FORBIDDEN);
                    return;
                }

                LOGGER.info("シグネチャヘッダの妥当性チェックが完了しました.");
                // <<<<<<<<<< シグネチャヘッダの妥当性チェック

                // >>>>>>>>>> サブスクリプション要求コールバックへの応答書き込み
                LOGGER.info("サブスクリプション要求コールバックへの応答書き込みを開始します.");

                String challenge = (String) new ObjectMapper()
                        .readValue(mRequest.getReader(), new TypeReference<Map<String, Object>>() {
                        }).get(CHALLENGE);

                new PrintWriter(response.getWriter()).print(challenge);

                LOGGER.info("サブスクリプション要求コールバックへの応答書き込みが完了しました.");
                // <<<<<<<<<< サブスクリプション要求コールバックへの応答書き込み

                // >>>>>>>>>> EventSubサブスクリプション秘密鍵のDB保存
                LOGGER.info("EventSubサブスクリプション秘密鍵のDB保存を開始します.");

                // EventSubサブスクリプション秘密鍵（コールバック用）をWebhook用としてDBに保存する
                docRef.update(SUBSCRIPTION_SECRET_WEBHOOK, secret).get();

                LOGGER.info("EventSubサブスクリプション秘密鍵のDB保存が完了しました.");
                // <<<<<<<<<< EventSubサブスクリプション秘密鍵のDB保存

                LOGGER.info("サブスクリプション要求コールバックのハンドリングが完了しました.");
                response.setStatusCode(HttpURLConnection.HTTP_OK);
                return;
                // <<<<<<<<<< サブスクリプション要求コールバックのハンドリング

            } else if (msgType.equalsIgnoreCase(REVOCATION)) {
                // >>>>>>>>>> サブスクリプション破棄コールバックのハンドリング
                LOGGER.info("サブスクリプション破棄コールバックのハンドリングを開始します.");

                String userId = (String) ((Map<String, Object>) ((Map<String, Object>) new ObjectMapper()
                        .readValue(mRequest.getReader(), new TypeReference<Map<String, Object>>() {
                        }).get(SUBSCRIPTION)).get(CONDITION)).get(BROADCASTER_USER_ID);
                LOGGER.info(String.format("サブスクリプションが破棄されました. USER_ID=[%s]", userId));

                LOGGER.info("サブスクリプション破棄コールバックのハンドリングが完了しました.");
                response.setStatusCode(HttpURLConnection.HTTP_OK);
                return;
                // <<<<<<<<<< サブスクリプション破棄コールバックのハンドリング
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Twitch通知ハンドリング処理中に例外が発生しました.", e);

            try {
                sendToSlack(System.getenv(SLACK_WEBHOOK_URL), COLORHEX_DANGER, "Twitch通知ハンドリング処理中に例外が発生しました.", e);
            } catch (Exception e2) {
                // エラー通知処理の例外は握りつぶす
                LOGGER.warning(String.format("Slackへのエラー通知に失敗しました. EXCEPTION_MESSAGE=[%s]", e2.getMessage()));
            }

            response.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
            return;
        }
    }

    private boolean isSupportedMethod(String method) {
        final List<String> SupportedMethods = Arrays.asList(HttpMethods.POST);

        return SupportedMethods.contains(method);
    }

    private Firestore initializeFirestore(String projectId) throws IOException {
        Firestore db;

        List<FirebaseApp> apps = FirebaseApp.getApps();
        if (apps.size() == 0) {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).setProjectId(projectId)
                    .build();
            db = FirestoreClient.getFirestore(FirebaseApp.initializeApp(options));
        } else {
            db = FirestoreClient.getFirestore(apps.get(0));
        }

        return db;
    }

    private boolean isValidSignature(String algo, HttpRequest request, String secret)
            throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        String hmacMsg = request.getFirstHeader(TWITCH_EVENTSUB_MESSAGE_ID).get()
                + request.getFirstHeader(TWITCH_EVENTSUB_MESSAGE_TIMESTAMP).get()
                + request.getReader().lines().collect(Collectors.joining());

        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), algo);
        Mac mac = Mac.getInstance(algo);
        mac.init(keySpec);
        mac.update(hmacMsg.getBytes());

        byte[] signBytes = mac.doFinal();
        String expected = "sha256=" + DatatypeConverter.printHexBinary(signBytes);

        return request.getFirstHeader(TWITCH_EVENTSUB_MESSAGE_SIGNATURE).get().compareToIgnoreCase(expected) == 0;
    }

    @SuppressWarnings({ "unchecked", "serial" })
    private Map<String, Object> getChannelInfo(String token, String clientId, String userId) throws IOException {
        Map<String, String> headers = new HashMap<>() {
            {
                put(CLIENT_ID, clientId);
                put(AUTHORIZATION, "Bearer " + token);
            }
        };

        Map<String, Object> params = new HashMap<>() {
            {
                put(BROADCASTER_ID, userId);
            }
        };

        Response response = get("https://api.twitch.tv/helix/channels", headers, params);
        return ((ArrayList<Map<String, Object>>) ((Map<String, Object>) new ObjectMapper()
                .readValue(response.body().string(), new TypeReference<Map<String, Object>>() {
                })).get(DATA)).get(0);
    }

    @SuppressWarnings("serial")
    private void sendToDiscord(String url, String userDisplayName, String userLoginName, String title, String gameName,
            String startedAtUTC) throws Exception {
        // UTC -> JST
        String startedAtJST = Instant.parse(startedAtUTC).atZone(ZoneId.of("Asia/Tokyo"))
                .format(DateTimeFormatter.ofPattern("M月dd日(E) HH:mm").withLocale(Locale.JAPANESE)).toString();

        Map<String, String> headers = new HashMap<String, String>() {
            {
                put(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            }
        };

        Map<String, Object> body = new HashMap<String, Object>() {
            {
                put(USERNAME, "Twitch Webhook");
                put(CONTENT, String.format("%s がTwitchで配信を開始しました.", userDisplayName));
                put(EMBEDS, new ArrayList<Map<String, Object>>() {
                    {
                        add(new HashMap<String, Object>() {
                            {
                                put(TITLE, StringUtils.isEmpty(title) ? "N/A" : title);
                                put(URL, String.format("https://www.twitch.tv/%s", userLoginName));
                                put(COLOR, 9521150);
                                put(AUTHOR, new HashMap<String, Object>() {
                                    {
                                        put(NAME, userDisplayName);
                                    }
                                });
                                put(FIELDS, new ArrayList<Map<String, Object>>() {
                                    {
                                        add(new HashMap<String, Object>() {
                                            {
                                                put(NAME, "Playing");
                                                put(VALUE, StringUtils.isEmpty(gameName) ? "N/A" : gameName);
                                                put(INLINE, true);
                                            }
                                        });
                                        add(new HashMap<String, Object>() {
                                            {
                                                put(NAME, "Started at (Asia/Tokyo)");
                                                put(VALUE, startedAtJST);
                                                put(INLINE, true);
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        };

        String json = new ObjectMapper().writeValueAsString(body);

        try (Response response = post(url, headers, null, RequestBody.create(json, JSON))) {
            if (!response.isSuccessful()) {
                LOGGER.severe(String.format("Discord通知に失敗しました. REQUEST_BODY=[%s]", json));

                throw new Exception(String.format("Failed to send message to Discord. STATUS_CODE=[%d], MESSAGE=[%s]",
                        response.code(), response.message()));
            }
        }
    }

    @SuppressWarnings("serial")
    private void sendToSlack(String url, String color, String message, Exception exception) throws Exception {
        Map<String, String> header = new HashMap<>() {
            {
                put(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            }
        };

        Map<String, Object> body = new HashMap<>() {
            {
                put(ATTACHMENTS, new ArrayList<Map<String, Object>>() {
                    {
                        add(new HashMap<String, Object>() {
                            {

                                put(COLOR, color);
                                put(BLOCKS, new ArrayList<Map<String, Object>>() {
                                    {
                                        add(new HashMap<String, Object>() {
                                            {
                                                put(TYPE, "section");
                                                put(TEXT, new HashMap<String, Object>() {
                                                    {
                                                        put(TYPE, "plain_text");
                                                        put(TEXT, message);
                                                        put(EMOJI, true);
                                                    }
                                                });
                                            }
                                        });
                                        add(new HashMap<String, Object>() {
                                            {
                                                put(TYPE, "section");
                                                put(FIELDS, new ArrayList<Map<String, Object>>() {
                                                    {
                                                        add(new HashMap<String, Object>() {
                                                            {
                                                                put(TYPE, "mrkdwn");
                                                                put(TEXT, "*Exception*");
                                                            }
                                                        });
                                                        add(new HashMap<String, Object>() {
                                                            {
                                                                put(TYPE, "mrkdwn");
                                                                put(TEXT, "*Message*");
                                                            }
                                                        });
                                                        add(new HashMap<String, Object>() {
                                                            {
                                                                put(TYPE, "plain_text");
                                                                put(TEXT, exception == null ? "N/A"
                                                                        : exception.getClass().getName());
                                                            }
                                                        });
                                                        add(new HashMap<String, Object>() {
                                                            {
                                                                put(TYPE, "plain_text");
                                                                put(TEXT,
                                                                        exception == null || StringUtils
                                                                                .isEmpty(exception.getMessage()) ? "N/A"
                                                                                        : exception.getMessage());
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                        });
                                        add(new HashMap<String, Object>() {
                                            {
                                                put(TYPE, "section");
                                                put(TEXT, new HashMap<String, Object>() {
                                                    {
                                                        put(TYPE, "plain_text");
                                                        put(TEXT, " ");
                                                    }
                                                });
                                                put(ACCESSORY, new HashMap<String, Object>() {
                                                    {
                                                        put(TYPE, "button");
                                                        put(TEXT, new HashMap<String, Object>() {
                                                            {
                                                                put(TYPE, "plain_text");
                                                                put(TEXT, "Open GCP");
                                                                put(EMOJI, true);
                                                            }
                                                        });
                                                        put(VALUE, "click_me_123");
                                                        put(URL, "https://console.cloud.google.com/home/dashboard");
                                                        put(ACTION_ID, "button-action");
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        };

        try (Response response = post(url, header, null,
                RequestBody.create(new ObjectMapper().writeValueAsString(body), JSON))) {

            if (!response.isSuccessful()) {
                throw new Exception(String.format("Failed to send message to Slack. STATUS_CODE=[%d], MESSAGE=[%s]",
                        response.code(), response.message()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Response get(String url, Map<String, String> headers, Map<String, Object> params) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (params != null) {
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (param.getValue() instanceof List<?>) {
                    for (String value : (List<String>) param.getValue()) {
                        urlBuilder.addQueryParameter(param.getKey(), value);
                    }
                } else {
                    urlBuilder.addQueryParameter(param.getKey(), (String) param.getValue());
                }
            }
        }

        Request.Builder reqBuilder = new Request.Builder().url(urlBuilder.build()).get();
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                reqBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        return callExternalAPI(reqBuilder.build());
    }

    @SuppressWarnings("unchecked")
    private Response post(String url, Map<String, String> headers, Map<String, Object> params, RequestBody body)
            throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (params != null) {
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (param.getValue() instanceof List<?>) {
                    for (String value : (List<String>) param.getValue()) {
                        urlBuilder.addQueryParameter(param.getKey(), value);
                    }
                } else {
                    urlBuilder.addQueryParameter(param.getKey(), (String) param.getValue());
                }
            }
        }

        Request.Builder reqBuilder = new Request.Builder().url(urlBuilder.build()).post(body);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                reqBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        return callExternalAPI(reqBuilder.build());
    }

    private Response callExternalAPI(Request request) throws IOException {
        return new OkHttpClient().newCall(request).execute();
    }
}
