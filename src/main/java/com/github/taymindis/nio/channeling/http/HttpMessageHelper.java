package com.github.taymindis.nio.channeling.http;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HttpMessageHelper {
    public static String parseToString(byte[] consumedBytes) {
        return new String(consumedBytes, StandardCharsets.UTF_8);
    }
    public static void massageRequestHeader(HttpRequestMessage request, String headerContent) throws Exception {

        String[] headers = headerContent.split("\\r?\\n");

        if (headers.length < 2) {
            throw new Exception("Invalid Request content " + Arrays.toString(headers));
        }

        String[] statusText = headers[0].split("\\s", 3);

        request.setMethod(statusText[0]);
        request.setPath(statusText[1]);
        request.setHttpVersion(statusText[2]);

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
    public static String massageResponseToString(HttpResponseMessage responseMessage) throws Exception {

        StringBuilder responseBuilder = new StringBuilder(responseMessage.getHttpVersion()).append(" ");

        responseBuilder
                .append(responseMessage.getCode())
                .append(" ")
                .append(responseMessage.getStatusText())
        .append("\n");

        responseMessage.getHeaderMap().forEach((key, value) ->
                responseBuilder.append(key).append(": ").append(value).append("\n"));

        responseBuilder.append("\n");

        if(responseMessage.getContent()!= null) {
            responseBuilder.append(responseMessage.getContent());
        }


        return responseBuilder.toString();
    }



}
