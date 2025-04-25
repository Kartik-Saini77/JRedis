package com.jredis.components;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespSerializerTest {
    private final RespSerializer respSerializer = new RespSerializer();

    @Test
    public void testDeserializePing() {
        String ping = "*1\r\n$4\r\nPING\r\n";
        List<String[]> result = respSerializer.deserialize(ping.getBytes());

        for (String[] strings : result) {
            for (String string : strings) {
                System.out.print(string + " ");
            }
            System.out.println();
        }

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().length);
        assertEquals("PING", result.getFirst()[0]);
    }

    @Test
    public void testMultipleCommands() {
        String multipleCommands = "*2\r\n*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n";

        List<String[]> result = respSerializer.deserialize(multipleCommands.getBytes());

        for (String[] strings : result) {
            for (String string : strings) {
                System.out.print(string + " ");
            }
            System.out.println();
        }

        assertEquals(2, result.size());
        assertEquals(3, result.getFirst().length);
        assertEquals("SET", result.getFirst()[0]);
        assertEquals("SET", result.get(1)[0]);

    }
}