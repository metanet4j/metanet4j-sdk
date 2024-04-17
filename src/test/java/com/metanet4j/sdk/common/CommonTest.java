package com.metanet4j.sdk.common;

import com.google.common.net.MediaType;
import org.junit.Test;

public class CommonTest {

    @Test
    public void testMediaType() {
        MediaType mediaType = MediaType.PDF;
        System.out.println(mediaType.toString());
    }
}
