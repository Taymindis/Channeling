package com.github.taymindis.nio.channeling.http;

import com.github.taymindis.nio.channeling.ChannelingBytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class HttpMessageHelper {
    public static String parseToString(ChannelingBytes consumedBytes) {
        return new String(consumedBytes.getBuff(), consumedBytes.getOffset(), consumedBytes.getLength(), StandardCharsets.UTF_8);
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

        Map<String, String> headerMap = new HashMap<>();

        for (int i = 1, size = headers.length; i < size; i++) {
            String header = headers[i];
            String[] keyPair = header.split(":\\s?",2);
            String name = keyPair[0];
            String value = keyPair[1];
            headerMap.put(name, value);
        }

        request.setHeaderMap(headerMap);
    }

    public static Map<String, String> massageHeaderContentToHeaderMap(String headerContent) throws Exception {
        return massageHeaderContentToHeaderMap(headerContent, true);
    }

    public static Integer hexToInt(String hex) {
//        return Integer.parseInt(hex,16);
        return (int) Long.parseLong(hex, 16);
    }

    public static String intToHex(Integer val) {
        return Integer.toHexString(val);
    }


    public static Map<String, String> massageHeaderContentToHeaderMap(String headerContent, boolean skipStatusLine) throws Exception {

        String[] headers = headerContent.split("\\r?\\n");

        if (headers.length < 2) {
            throw new Exception("Invalid Request content " + Arrays.toString(headers));
        }

//        String[] statusText = headers[0].split("\\s", 3);

        Map<String, String> headerMap = new HashMap<>();

        if (!skipStatusLine) {
            headerMap.put("status", headers[0]);
//            headerMap.put("httpVersion", statusText[0]);
//            headerMap.put("code", statusText[1]);
//            headerMap.put("status", statusText[2]);
        }

        for (int i = 1, size = headers.length; i < size; i++) {
            String header = headers[i];
            String[] keyPair = header.split(":\\s?",2);
            String name = keyPair[0];
            String value = keyPair[1];
            headerMap.put(name, value);
        }

        return headerMap;
    }

    public static String massageResponseToString(HttpResponseMessage responseMessage) {

        StringBuilder responseBuilder = new StringBuilder(responseMessage.getHttpVersion()).append(" ");

        responseBuilder
                .append(responseMessage.getCode())
                .append(" ")
                .append(responseMessage.getStatusText())
                .append("\n");

        responseMessage.getHeaderMap().forEach((key, value) ->
                responseBuilder.append(key).append(": ").append(value).append("\n"));

        responseBuilder.append("\n");

        if (responseMessage.getContent() != null) {
            responseBuilder.append(responseMessage.getContent());
        }


        return responseBuilder.toString();
    }

    public static String headerToString(Map<String, String> headerMap) {
        return headerToString(headerMap, "");
    }

    public static byte[] headerToBytes(Map<String, String> headerMap) {
        return headerToBytes(headerMap, "");
    }

    public static byte[] headerToBytes(Map<String, String> headerMap, String statusLine) {
        return headerToString(headerMap, statusLine).getBytes();
    }

    public static String headerToString(Map<String, String> headerMap, String statusLine) {
        StringBuilder headerBuilder = new StringBuilder(statusLine);

        if (!statusLine.isEmpty()) {
            headerBuilder.append("\r\n");
        }

        headerMap.forEach((key, value) ->
                headerBuilder.append(key).append(": ").append(value).append("\r\n"));

        headerBuilder.append("\r\n");


        return headerBuilder.toString();
    }

    public static String headerToString2(Map<String, Object> headerMap, String statusLine) {
        StringBuilder headerBuilder = new StringBuilder(statusLine);

        if (!statusLine.isEmpty()) {
            headerBuilder.append("\r\n");
        }

        headerMap.forEach((key, value) ->
                headerBuilder.append(key).append(": ").append(value).append("\r\n"));

        headerBuilder.append("\r\n");


        return headerBuilder.toString();
    }

    public static boolean isCompressed(final byte[] compressed) {
        return (compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
    }

//    public static String decompress(final byte[] compressed, Charset charset) throws IOException {
//        final StringBuilder outStr = new StringBuilder();
//        final CharBuffer outputBuffer = CharBuffer.allocate(1024);
//        outputBuffer.clear();
//        if ((compressed == null) || (compressed.length == 0)) {
//            return "";
//        }
//
//        if (isCompressed(compressed)) {
//            try (final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
//                 final InputStreamReader inputStreamReader = new InputStreamReader(gis, charset)
//            ) {
//                while (inputStreamReader.read(outputBuffer) > 0) {
//                    outputBuffer.flip();
//                    char[] ca = new char[outputBuffer.limit() - outputBuffer.position()];
//                    outputBuffer.get(ca);
//                    outStr.append(ca);
//                    if (!outputBuffer.hasRemaining()) {
//                        outputBuffer.clear();
//                    }
//                }
//            }
//        } else {
//            outStr.append(Arrays.toString(compressed));
//        }
//        return outStr.toString();
//    }

    public static String decompress(final ChannelingBytes compressed, Charset charset) throws IOException {
        final CharBuffer outputBuffer = CharBuffer.allocate(1024);
        outputBuffer.clear();
        if ((compressed == null) || (compressed.getLength() == 0)) {
            return "";
        }

        if (isCompressed(compressed.getBuff())) {
            final StringBuilder outStr = new StringBuilder();
            try (final GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed.getBuff(),
                    compressed.getOffset(), compressed.getLength()));
                 final InputStreamReader inputStreamReader = new InputStreamReader(gis, charset)
            ) {
                while (inputStreamReader.read(outputBuffer) > 0) {
                    outputBuffer.flip();
                    char[] ca = new char[outputBuffer.limit() - outputBuffer.position()];
                    outputBuffer.get(ca);
                    outStr.append(ca);
                    if (!outputBuffer.hasRemaining()) {
                        outputBuffer.clear();
                    }
                }
            }
            return outStr.toString();
        }


        return new String(compressed.getBuff(), compressed.getOffset(), compressed.getLength());


    }

}
