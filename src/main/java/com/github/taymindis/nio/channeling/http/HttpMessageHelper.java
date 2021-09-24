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

    public static Map<String,String> massageHeaderContentToHeaderMap(String headerContent) throws Exception {
        return massageHeaderContentToHeaderMap(headerContent, true);
    }

    public static Integer hexToInt(String hex) {
//        return Integer.parseInt(hex,16);
        return (int) Long.parseLong(hex, 16);
    }

    public static String intToHex(Integer val) {
        return Integer.toHexString(val);
    }


    public static Map<String,String> massageHeaderContentToHeaderMap(String headerContent, boolean skipStatusLine) throws Exception {

        String[] headers = headerContent.split("\\r?\\n");

        if (headers.length < 2) {
            throw new Exception("Invalid Request content " + Arrays.toString(headers));
        }

        String[] statusText = headers[0].split("\\s", 3);

        Map<String,String> headerMap = new HashMap<>();

        if(!skipStatusLine) {
            headerMap.put("method", statusText[0]);
            headerMap.put("path", statusText[1]);
            headerMap.put("httpVersion", statusText[2]);
        }

        for (int i = 1, size = headers.length; i < size; i++) {
            String header = headers[i];
            String[] keyPair = header.split(":\\s?");
            String name = keyPair[0];
            String value = keyPair[1];
            headerMap.put(name, value);
        }

        return headerMap;
    }

    public static String massageResponseToString(HttpResponseMessage responseMessage)  {

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

    public static String headerToString(Map<String,String> headerMap) {
        return headerToString(headerMap, "");
    }

    public static byte[] headerToBytes(Map<String,String> headerMap) {
        return headerToBytes(headerMap, "");
    }

    public static byte[] headerToBytes(Map<String,String> headerMap, String statusLine) {
        return headerToString(headerMap, statusLine).getBytes();
    }

    public static String headerToString(Map<String,String> headerMap, String statusLine) {
        StringBuilder headerBuilder = new StringBuilder(statusLine);

        if(!statusLine.isEmpty()){
            headerBuilder.append("\r\n");
        }

        headerMap.forEach((key, value) ->
                headerBuilder.append(key).append(": ").append(value).append("\r\n"));

        headerBuilder.append("\r\n");


        return headerBuilder.toString();
    }

}
