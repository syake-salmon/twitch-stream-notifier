package com.syakeapps.tsn.maintenance.application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

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

    /* REQUEST HEADER */
    private static final String CLIENT_ID = "Client-Id";
    private static final String AUTHORIZATION = "Authorization";

    /* REQUEST BODY */
    private static final String ID = "id";

    /* RESPONSE BODY */
    private static final String DATA = "data";

    /* FIREBASE */
    private static final String DOCUMENT_ID = "Er5sQcTGNVGdGW7edDew";
    /* FIELDS */
    private static final String TWITCH_TOKEN = "TWITCH_TOKEN";

    /* OTHERS */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Logger LOGGER = Logger.getLogger(Function.class.getName());

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {

        String projectId = System.getenv(GCP_PROJECT_ID);
        String clientId = System.getenv(TWITCH_CLIENT_ID);

        try {
            // >>>>>>>>>> Firebase初期化
            LOGGER.info("Firebaseの初期化を開始します.");

            Firestore db = initializeFirestore(projectId);
            DocumentReference docRef = db.collection(projectId).document(DOCUMENT_ID);

            LOGGER.info("Firebaseの初期化が完了しました.");
            // <<<<<<<<<< Firebase初期化

            // >>>>>>>>>> 保存済Twitchトークンの取得
            LOGGER.info("保存済Twitchトークンの取得を開始します.");

            String token = (String) docRef.get().get().get(TWITCH_TOKEN);

            LOGGER.info("保存済Twitchトークンの取得が完了しました.");
            // <<<<<<<<<< 保存済Twitchトークンの取得

            // >>>>>>>>>> 現サブスクリプションIDの取得
            LOGGER.info("現サブスクリプションIDの取得を開始します.");

            List<String> ids = getSubscriptionIds(token, clientId);

            LOGGER.info(String.format("現サブスクリプションIDの取得が完了しました. ID_SIZE=[%d]", ids.size()));
            // <<<<<<<<<< 現サブスクリプションIDの取得

            // >>>>>>>>>> 現サブスクリプションの破棄
            LOGGER.info("現サブスクリプションの破棄を開始します.");

            revokeSubscriptions(token, clientId, ids);

            LOGGER.info("現サブスクリプションの破棄が完了しました.");
            // <<<<<<<<<< 現サブスクリプションの破棄

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "メンテナンス処理中に例外が発生しました.");
            throw e;
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
                // NOP, Just close
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
