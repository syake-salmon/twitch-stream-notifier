package com.syakeapps.tsn.subscriber.bean;

import java.util.Map;

public class PubSubMessage {
    String data;
    Map<String, String> attributes;
    String messageId;
    String publishTime;
}
