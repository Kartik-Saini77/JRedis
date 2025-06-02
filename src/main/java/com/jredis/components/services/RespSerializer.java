package com.jredis.components.services;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class RespSerializer {

    public String serializeBulkString(String command) {
        int len = command.length();
        String respHeader = "$" + len + "\r\n";

        return respHeader + command + "\r\n";
    }

    public String serializeArray(String[] command) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(command.length).append("\r\n");
        for (String cmd : command) {
            sb.append(serializeBulkString(cmd));
        }
        return sb.toString();
    }

    public String serializeArray(List<String> command) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(command.size()).append("\r\n");
        for (String cmd : command) {
            sb.append(cmd); // Commands are already serialized
        }
        return sb.toString();
    }

    public String serializeInteger(int value) {
        return ":" + value + "\r\n";
    }

    public List<String[]> deserialize(byte[] command) {
        String data = new String(command, StandardCharsets.UTF_8).trim();
        char[] dataArr = data.toCharArray();
        List<String[]> commands = new ArrayList<>();

        int i=0;
        while (i < dataArr.length) {
            try {
                char curr = dataArr[i];
                if (curr == '\u0000') {
                    break;
                }
                if (curr == '*') {
                    i++;
                    int arrLen = 0;
                    while (i < dataArr.length && dataArr[i] != '\r' && dataArr[i+1] != '\n') {
                        arrLen = arrLen * 10 + (dataArr[i] - '0');
                        i++;
                    }
                    i += 2;
                    if (dataArr[i] == '*') {
                        for(int t=0; t<arrLen; t++) {
                            i++;
                            int nestedLen = 0;
                            while (i < dataArr.length && dataArr[i] != '\r' && dataArr[i+1] != '\n') {
                                nestedLen = nestedLen * 10 + (dataArr[i] - '0');
                                i++;
                            }
                            i += 2;
                            String[] subArray = new String[nestedLen];
                            i = getParts(dataArr, i, subArray);
                            commands.add(subArray);
                        }
                    } else {
                        String[] subArray = new String[arrLen];
                        i = getParts(dataArr, i, subArray);
                        commands.add(subArray);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Error parsing RESP command: " + e.getMessage());
            }
        }

        return commands;
    }

    private int getParts(char[] dataArr, int i, String[] subArray) {
        int j = 0, dataLen = dataArr.length, arrLen = subArray.length;
        while (i < dataLen && j < arrLen) {
            try {
                if (dataArr[i] == '$') {
                    i++;
                    int len = 0;
                    while (i < dataLen && dataArr[i] != '\r' && dataArr[i+1] != '\n') {
                        len = len * 10 + (dataArr[i] - '0');
                        i++;
                    }
                    i += 2;
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < len; k++) {
                        sb.append(dataArr[i]);
                        i++;
                    }
                    i += 2;
                    subArray[j++] = sb.toString();
                } else {
                    break;
                }
            } catch (Exception e) {
                throw new RuntimeException("Error parsing RESP command: " + e.getMessage());
            }
        }
        return i;
    }

    public String[] parseArray(String[] parts) {
        String len = parts[0];
        int length = Integer.parseInt(len);
        String[] command = new String[length];

        int idx = 0;
        for (int i = 2; i < parts.length; i += 2) {
            command[idx++] = parts[i];
        }

        return command;
    }
}
