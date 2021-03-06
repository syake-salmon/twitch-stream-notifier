package com.syakeapps.tsn.subscriber.application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.entity.ContentType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.common.flogger.FluentLogger;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.syakeapps.tsn.subscriber.bean.PubSubMessage;
import com.syakeapps.tsn.subscriber.bean.TwitchUser;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Function implements BackgroundFunction<PubSubMessage> {
    /* SYSTEM ENVVARS KEY */
    private static final String GCP_PROJECT_ID = "GCP_PROJECT_ID";
    private static final String TWITCH_CLIENT_ID = "TWITCH_CLIENT_ID";
    private static final String TWITCH_SECRET = "TWITCH_SECRET";
    private static final String TWITCH_MY_USER_ID = "TWITCH_MY_USER_ID";
    private static final String CALLBACK_URL = "CALLBACK_URL";
    private static final String SLACK_WEBHOOK_URL = "SLACK_WEBHOOK_URL";

    /* REQUEST HEADER */
    private static final String CLIENT_ID = "Client-Id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String GRANT_TYPE = "grant_type";
    private static final String SCOPE = "scope";

    /* REQUEST BODY */
    private static final String BROADCASTER_USER_ID = "broadcaster_user_id";
    private static final String CONDITION = "condition";
    private static final String URL = "url";
    private static final String COLOR = "color";
    private static final String FIELDS = "fields";
    private static final String VALUE = "value";
    private static final String ATTACHMENTS = "attachments";
    private static final String BLOCKS = "blocks";
    private static final String TYPE = "type";
    private static final String TEXT = "text";
    private static final String EMOJI = "emoji";
    private static final String ACCESSORY = "accessory";
    private static final String ACTION_ID = "action_id";
    private static final String FIRST = "first";
    private static final String FROM_ID = "from_id";
    private static final String ID = "id";
    private static final String VERSION = "version";
    private static final String TRANSPORT = "transport";
    private static final String METHOD = "method";
    private static final String CALLBACK = "callback";
    private static final String SECRET = "secret";

    /* RESPONSE BODY */
    private static final String DATA = "data";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String TO_ID = "to_id";
    private static final String TO_NAME = "to_name";
    private static final String DISPLAY_NAME = "display_name";

    /* FIREBASE */
    private static final String DOCUMENT_ID = "Er5sQcTGNVGdGW7edDew";
    /* FIELDS */
    private static final String TWITCH_TOKEN = "TWITCH_TOKEN";
    private static final String SUBSCRIPTION_SECRET_CALLBACK = "SUBSCRIPTION_SECRET_CALLBACK";

    /* OTHERS */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String COLORHEX_DANGER = "#DAA038";
    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    @Override
    public void accept(PubSubMessage payload, Context context) throws Exception {

        String projectId = System.getenv(GCP_PROJECT_ID);
        String clientId = System.getenv(TWITCH_CLIENT_ID);
        String myUserId = System.getenv(TWITCH_MY_USER_ID);
        String slackEndpoint = System.getenv(SLACK_WEBHOOK_URL);

        try {
            // >>>>>>>>>> Firestore?????????
            LOGGER.atFiner().log("Firestore??????????????????????????????.");

            Firestore db = initializeFirestore(projectId);
            DocumentReference docRef = db.collection(projectId).document(DOCUMENT_ID);

            LOGGER.atFiner().log("Firestore?????????????????????????????????.");
            // <<<<<<<<<< Firestore?????????

            // >>>>>>>>>> ?????????Twitch?????????????????????
            LOGGER.atFiner().log("?????????Twitch???????????????????????????????????????.");

            String token = (String) docRef.get().get().get(TWITCH_TOKEN);

            LOGGER.atFiner().log("?????????Twitch??????????????????????????????????????????.");
            // <<<<<<<<<< ?????????Twitch?????????????????????

            // >>>>>>>>>> ?????????Twitch??????????????????????????????
            LOGGER.atFiner().log("?????????Twitch????????????????????????????????????????????????.");

            if (!isValidToken(token)) {
                LOGGER.atWarning().log("?????????Twitch???????????????????????????.????????????????????????.");

                // ??????????????????????????????????????????
                token = generateNewToken(clientId, System.getenv(TWITCH_SECRET));
                // ??????????????????????????????DB???????????????
                docRef.update(TWITCH_TOKEN, token).get();
            }

            LOGGER.atFiner().log("?????????Twitch???????????????????????????????????????????????????.");
            // <<<<<<<<<< ?????????Twitch??????????????????????????????

            // >>>>>>>>>> ??????????????????????????????ID?????????
            LOGGER.atFiner().log("??????????????????????????????ID???????????????????????????.");

            List<String> ids = getSubscriptionIds(token, clientId);

            LOGGER.atFiner().log("??????????????????????????????ID??????????????????????????????. ID_SIZE=[%d]", ids.size());
            // <<<<<<<<<< ??????????????????????????????ID?????????

            // >>>>>>>>>> ???????????????????????????????????????
            LOGGER.atFiner().log("?????????????????????????????????????????????????????????.");

            revokeSubscriptions(token, clientId, ids);

            LOGGER.atFiner().log("????????????????????????????????????????????????????????????.");
            // <<<<<<<<<< ???????????????????????????????????????

            // >>>>>>>>>> Twitch?????????????????????????????????
            LOGGER.atFiner().log("Twitch???????????????????????????????????????????????????.");

            List<TwitchUser> followees = getFollows(token, clientId, myUserId);

            LOGGER.atFiner().log("Twitch??????????????????????????????????????????????????????. TWITCH_FOLLOWEES_SIZE=[%d], TWITCH_FOLLOWEES=[%s]",
                    followees.size(), followees.stream().map(Object::toString).collect(Collectors.joining(", ")));
            // <<<<<<<<<< Twitch?????????????????????????????????

            // >>>>>>>>>> ?????????Twitch????????????????????????
            LOGGER.atFiner().log("?????????Twitch??????????????????????????????????????????.");

            List<TwitchUser> users = getUsers(token, clientId, Arrays.asList(myUserId));

            LOGGER.atFiner().log("?????????Twitch?????????????????????????????????????????????. MY_TWITCH_ID=[%s], MY_TWITCH_USERNAME=[%s]",
                    users.get(0).getId(), users.get(0).getName());
            // <<<<<<<<<< ?????????Twitch????????????????????????

            // >>>>>>>>>> Twitch????????????????????????????????????Twitch???????????????????????????
            LOGGER.atFiner().log("Twitch????????????????????????????????????Twitch?????????????????????????????????????????????.");

            List<TwitchUser> targets = Stream.concat(followees.stream(), users.stream()).collect(Collectors.toList());

            LOGGER.atFiner().log("Twitch????????????????????????????????????Twitch????????????????????????????????????????????????. TARGET_USERS_SIZE=[%d], TARGER_USERS=[%s]",
                    targets.size(), targets.stream().map(Object::toString).collect(Collectors.joining(", ")));
            // <<<<<<<<<< Twitch????????????????????????????????????Twitch???????????????????????????

            // >>>>>>>>>> EventSub?????????????????????????????????????????????
            LOGGER.atFiner().log("EventSub???????????????????????????????????????????????????????????????.");

            String preSubscriptionSecret = RandomStringUtils.randomAlphanumeric(100);
            // ????????????????????????DB??????????????????????????????????????????
            docRef.update(SUBSCRIPTION_SECRET_CALLBACK, preSubscriptionSecret).get();

            LOGGER.atFiner().log("EventSub??????????????????????????????????????????????????????????????????.");
            // <<<<<<<<<< EventSub?????????????????????????????????????????????

            // >>>>>>>>>> EventSub????????????????????????????????????
            LOGGER.atFiner().log("EventSub??????????????????????????????????????????????????????.");

            Map<TwitchUser, Map<String, String>> results = requestSubscription(token, clientId, targets,
                    System.getenv(CALLBACK_URL), preSubscriptionSecret);

            // ????????????????????????????????????????????????
            int failCount = 0; // 0=NORMAL, 0<WARN
            for (Entry<TwitchUser, Map<String, String>> entry : results.entrySet()) {
                String status = entry.getValue().get("status");
                String name = entry.getKey().getName();
                String message = entry.getValue().get("message");

                if (status.equals("409")) {
                    LOGGER.atInfo().log("???????????????????????????????????????????????????????????????. TWITCH_USERNAME=[%s], STATUS_CODE=[%s], MESSAGE=[%s]",
                            name, status, message);
                } else if (!status.startsWith("2")) {
                    LOGGER.atWarning().log(
                            "?????????????????????????????????????????????????????????????????????. TWITCH_USERNAME=[%s], STATUS_CODE=[%s], MESSAGE=[%s]", name,
                            status, message);
                    failCount++;
                }
            }

            // ?????????????????????????????????????????????????????????????????????Slack???????????????
            try {
                if (failCount > 0) {
                    sendToSlack(slackEndpoint, COLORHEX_DANGER, "Twitch?????????????????????????????????????????????????????????????????????.", null);
                }
            } catch (Exception e1) {
                // ??????????????????????????????????????????
                LOGGER.atWarning().withCause(e1).log("Slack??????????????????????????????????????????.");
            }

            LOGGER.atFiner().log("EventSub?????????????????????????????????????????????????????????.");
            // <<<<<<<<<< EventSub????????????????????????????????????

        } catch (Exception e2) {
            LOGGER.atSevere().withCause(e2).log("EventSub??????????????????????????????????????????????????????????????????????????????.");

            // >>>>>>>>>> Slack????????????????????????
            try {
                sendToSlack(slackEndpoint, COLORHEX_DANGER, "Twitch???????????????????????????????????????????????????????????????????????????.", e2);
            } catch (Exception e3) {
                // ??????????????????????????????????????????
                LOGGER.atWarning().withCause(e3).log("Slack??????????????????????????????????????????.");
            }
            // <<<<<<<<<< Slack????????????????????????

            throw e2;
        }
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

    @SuppressWarnings("serial")
    private boolean isValidToken(String token) throws IOException {
        if (token == null || token.isEmpty()) {
            return false;
        }

        Map<String, String> headers = new HashMap<>() {
            {
                put(AUTHORIZATION, "Bearer " + token);
            }
        };
        try (Response response = get("https://id.twitch.tv/oauth2/validate", headers, null)) {
            if (response.isSuccessful()) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("serial")
    private String generateNewToken(String clientId, String secret) throws Exception {
        Map<String, Object> params = new HashMap<>() {
            {
                put("client_id", clientId); // ???????????????????????????????????????????????????
                put(CLIENT_SECRET, secret);
                put(GRANT_TYPE, "client_credentials");
                put(SCOPE, "user:read:follows");
            }
        };

        try (Response response = post("https://id.twitch.tv/oauth2/token", null, params,
                RequestBody.create("{}", JSON))) {

            if (response.isSuccessful()) {
                return (String) new ObjectMapper()
                        .readValue(response.body().string(), new TypeReference<Map<String, Object>>() {
                        }).get(ACCESS_TOKEN);
            } else {
                throw new Exception("Failed to generate token.");
            }
        }
    }

    @SuppressWarnings({ "unchecked", "serial" })
    private List<String> getSubscriptionIds(String token, String clientId) throws IOException {
        Map<String, String> headers = new HashMap<>() {
            {
                put(CLIENT_ID, clientId);
                put(AUTHORIZATION, "Bearer " + token);
            }
        };

        List<String> ids = new ArrayList<>();
        try (Response response = get("https://api.twitch.tv/helix/eventsub/subscriptions", headers, null)) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) new ObjectMapper()
                    .readValue(response.body().string(), new TypeReference<Map<String, Object>>() {
                    }).get(DATA);

            for (Map<String, Object> datum : data) {
                ids.add((String) datum.get(ID));
            }
        }

        return ids;
    }

    @SuppressWarnings("unused")
    private void revokeSubscription(String token, String clientId, String id) throws IOException {
        revokeSubscriptions(token, clientId, Arrays.asList(id));
    }

    @SuppressWarnings("serial")
    private void revokeSubscriptions(String token, String clientId, List<String> ids) throws IOException {
        Map<String, String> headers = new HashMap<>() {
            {
                put(CLIENT_ID, clientId);
                put(AUTHORIZATION, "Bearer " + token);
            }
        };

        for (String id : ids) {
            Map<String, Object> params = new HashMap<>() {
                {
                    put(ID, id);
                }
            };

            try (Response response = delete("https://api.twitch.tv/helix/eventsub/subscriptions", headers, params,
                    RequestBody.create("{}", JSON))) {
                // NOP, Just close response
            }
        }
    }

    @SuppressWarnings({ "unchecked", "serial" })
    private List<TwitchUser> getFollows(String token, String clientId, String userId)
            throws JsonMappingException, JsonProcessingException, IOException {
        Map<String, String> headers = new HashMap<>() {
            {
                put(CLIENT_ID, clientId);
                put(AUTHORIZATION, "Bearer " + token);
            }
        };

        Map<String, Object> params = new HashMap<>() {
            {
                put(FIRST, "100");
                put(FROM_ID, userId);
            }
        };

        try (Response response = get("https://api.twitch.tv/helix/users/follows", headers, params)) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) new ObjectMapper()
                    .readValue(response.body().string(), new TypeReference<Map<String, Object>>() {
                    }).get(DATA);

            List<TwitchUser> users = new ArrayList<>();
            data.forEach(d -> {
                users.add(new TwitchUser((String) d.get(TO_ID), (String) d.get(TO_NAME)));
            });

            return users;
        }
    }

    @SuppressWarnings({ "unchecked", "serial" })
    private List<TwitchUser> getUsers(String token, String clientId, List<String> userIds)
            throws JsonMappingException, JsonProcessingException, IOException {
        Map<String, String> headers = new HashMap<>() {
            {
                put(CLIENT_ID, clientId);
                put(AUTHORIZATION, "Bearer " + token);
            }
        };

        Map<String, Object> params = new HashMap<>() {
            {
                put(ID, userIds);
            }
        };

        try (Response response = get("https://api.twitch.tv/helix/users", headers, params)) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) new ObjectMapper()
                    .readValue(response.body().string(), new TypeReference<Map<String, Object>>() {
                    }).get(DATA);

            List<TwitchUser> users = new ArrayList<>();
            data.forEach(d -> {
                users.add(new TwitchUser((String) d.get(ID), (String) d.get(DISPLAY_NAME)));
            });

            return users;
        }
    }

    @SuppressWarnings("serial")
    private Map<TwitchUser, Map<String, String>> requestSubscription(String token, String clientId,
            List<TwitchUser> targets, String callbackUrl, String subscriptionSecret)
            throws JsonProcessingException, IOException {
        Map<String, String> headers = new HashMap<>() {
            {
                put(CLIENT_ID, clientId);
                put(AUTHORIZATION, "Bearer " + token);
            }
        };

        Map<String, Object> body = new HashMap<>() {
            {
                put(TYPE, "stream.online");
                put(VERSION, "1");
                put(TRANSPORT, new HashMap<>() {
                    {
                        put(METHOD, "webhook");
                        put(CALLBACK, callbackUrl);
                        put(SECRET, subscriptionSecret);
                    }
                });
            }
        };

        Map<TwitchUser, Map<String, String>> results = new HashMap<>();
        for (TwitchUser user : targets) {
            String userId = user.getId();

            Map<String, Object> condition = new HashMap<>() {
                {
                    put(BROADCASTER_USER_ID, userId);
                }
            };

            body.put(CONDITION, condition);

            try (Response response = post("https://api.twitch.tv/helix/eventsub/subscriptions", headers, null,
                    RequestBody.create(new ObjectMapper().writeValueAsString(body), JSON))) {
                Map<String, String> result = new HashMap<>() {
                    {
                        put("status", String.valueOf(response.code()));
                        put("message", response.message());
                    }
                };

                results.put(user, result);
            }
        }

        return results;
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
                                                                        exception == null
                                                                                || exception.getMessage() == null
                                                                                        ? "N/A"
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

    @SuppressWarnings("unchecked")
    private Response delete(String url, Map<String, String> headers, Map<String, Object> params, RequestBody body)
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

        Request.Builder reqBuilder = new Request.Builder().url(urlBuilder.build()).delete(body);
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
