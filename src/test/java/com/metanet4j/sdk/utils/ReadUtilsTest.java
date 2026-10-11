package com.metanet4j.sdk.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReadUtilsTest {

    @Test
    void readsSequentialBytesIncludingAnEmptySlice() {
        ReadUtils reader = new ReadUtils(new byte[] {1, 2, 3, 4});
        assertArrayEquals(new byte[] {1, 2}, reader.readBytes(2));
        assertArrayEquals(new byte[0], reader.readBytes(0));
        assertEquals((byte) 3, reader.readByte());
        assertArrayEquals(new byte[] {4}, reader.readBytes(1));
        assertThrows(IllegalArgumentException.class, reader::readByte);
    }

    @Test
    void rejectsNegativeTruncatedAndOversizedReads() {
        ReadUtils reader = new ReadUtils(new byte[] {1});
        assertThrows(IllegalArgumentException.class, () -> reader.readBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> reader.readBytes(2));
        assertThrows(IllegalArgumentException.class, () -> reader.readBytes(Integer.MAX_VALUE));
        assertEquals((byte) 1, reader.readByte());
    }
}
