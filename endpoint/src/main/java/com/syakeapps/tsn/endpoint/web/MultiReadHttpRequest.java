package com.syakeapps.tsn.endpoint.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import com.google.api.client.util.IOUtils;
import com.google.cloud.functions.HttpRequest;

public class MultiReadHttpRequest implements HttpRequest {
    private ByteArrayOutputStream cachedBytes;
    private HttpRequest request;

    public MultiReadHttpRequest(HttpRequest request) {
        this.request = request;
    }

    private void cacheInputStream() throws IOException {
        cachedBytes = new ByteArrayOutputStream();
        IOUtils.copy(request.getInputStream(), cachedBytes);
    }

    @Override
    public Optional<String> getContentType() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    @Override
    public long getContentLength() {
        // TODO 自動生成されたメソッド・スタブ
        return 0;
    }

    @Override
    public Optional<String> getCharacterEncoding() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (cachedBytes == null) {
            cacheInputStream();
        }
        return new CachedServletInputStream();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    @Override
    public Optional<String> getFirstHeader(String name) {
        return request.getFirstHeader(name);
    }

    @Override
    public String getMethod() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    @Override
    public String getUri() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    @Override
    public String getPath() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    @Override
    public Optional<String> getQuery() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    @Override
    public Map<String, List<String>> getQueryParameters() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    @Override
    public Map<String, HttpPart> getParts() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    public class CachedServletInputStream extends ServletInputStream {
        private ByteArrayInputStream input;

        public CachedServletInputStream() {
            input = new ByteArrayInputStream(cachedBytes.toByteArray());
        }

        @Override
        public int read() throws IOException {
            return input.read();
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new RuntimeException("未実装です");
        }
    }
}
