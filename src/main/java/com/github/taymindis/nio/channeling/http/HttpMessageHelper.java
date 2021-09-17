package com.github.taymindis.nio.channeling.http;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HttpMessageHelper {
    public static String parseToString(byte[] consumedBytes) {
        return new String(consumedBytes, StandardCharsets.UTF_8);
    }
    public static void rawMessageToHeaderMap(HttpRequestMessage request, String headerContent) throws Exception {

        String[] headers = headerContent.split("\\r?\\n");

        if (headers.length < 2) {
            throw new Exception("Invalid Request content " + Arrays.toString(headers));
        }

        String[] statusText = headers[0].split("\\s", 3);

        request.setMethod(statusText[0]);
        request.setPath(statusText[1]);

        Map<String,String> headerMap = new HashMap<>();

        for (int i = 1, size = headers.length; i < size; i++) {
            String header = headers[i];
            String[] keyPair = header.split(":\\s?");
            String name = keyPair[0];
            String value = keyPair[1];
            headerMap.put(name, value);
        }

        request.setHeaderMap(headerMap);
    }



}
