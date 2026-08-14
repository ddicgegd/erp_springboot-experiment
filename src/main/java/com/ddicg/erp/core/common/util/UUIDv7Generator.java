package com.ddicg.erp.core.common.util;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/**
 * Thread-safe RFC 9562 compliant UUIDv7 generator.
 */
public class UUIDv7Generator {

    private static final Random RANDOM = new SecureRandom();

    private UUIDv7Generator() {}

    public static UUID generate() {
        long timestamp = System.currentTimeMillis();
        long msb = (timestamp & 0xFFFFFFFFFFFFL) << 16; // 48-bit timestamp
        msb |= (0x7L << 12);                           // version 7
        msb |= (RANDOM.nextLong() & 0x0FFFL);           // 12-bit rand_a

        long lsb = (0x2L << 62);                        // variant 2 (RFC 4122/9562)
        lsb |= (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL); // 62-bit rand_b

        return new UUID(msb, lsb);
    }
}
