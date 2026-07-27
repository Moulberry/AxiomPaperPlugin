package com.moulberry.axiom.integration.prism;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrismAxiomSerializationTest {

    @Test
    void v2PartsPreserveNullAndEmptyStrings() {
        String encoded = PrismAxiomSerialization.encodeParts(null, "", "value");

        assertArrayEquals(
            new String[]{null, "", "value"},
            PrismAxiomSerialization.decodeParts(encoded, 3)
        );
    }

    @Test
    void v2PartsRepresentAnAllNullBlockEntityPayload() {
        String encoded = PrismAxiomSerialization.encodeParts(null, null);

        assertEquals("v2;n;n", encoded);
        assertArrayEquals(
            new String[]{null, null},
            PrismAxiomSerialization.decodeParts(encoded, 2)
        );
    }

    @Test
    void readsLegacyRecordsWithFewerFields() {
        String previous = Base64.getEncoder().encodeToString("before".getBytes(StandardCharsets.UTF_8));
        String next = Base64.getEncoder().encodeToString("after".getBytes(StandardCharsets.UTF_8));

        String[] decoded = PrismAxiomSerialization.decodeParts(previous + ";" + next, 4);

        assertEquals("before", decoded[0]);
        assertEquals("after", decoded[1]);
        assertNull(decoded[2]);
        assertNull(decoded[3]);
    }

    @Test
    void boundedV2PartsAcceptOnlyThreeOrFourFields() {
        String threeFields = PrismAxiomSerialization.encodeParts("uuid", "snapshot", null);
        String fourFields = PrismAxiomSerialization.encodeParts("uuid", "snapshot", null, "vehicle");

        assertArrayEquals(
            new String[]{"uuid", "snapshot", null},
            PrismAxiomSerialization.decodeParts(threeFields, 3, 4)
        );
        assertArrayEquals(
            new String[]{"uuid", "snapshot", null, "vehicle"},
            PrismAxiomSerialization.decodeParts(fourFields, 3, 4)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeParts(PrismAxiomSerialization.encodeParts("uuid", "snapshot"), 3, 4)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeParts(
                PrismAxiomSerialization.encodeParts("uuid", "snapshot", null, "vehicle", "extra"),
                3,
                4
            )
        );
    }

    @Test
    void rejectsMalformedV2Records() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeParts("v2;sYWJj;n;extra", 2)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeParts("v2;x;n", 2)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeParts("v2;s%%%", 1)
        );
    }

    @Test
    void rejectsMalformedLegacyRecords() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeParts("YQ==;Yg==;Yw==", 2)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeParts("%%%", 1)
        );
    }

    @Test
    void worldTimeRequiresStrictBooleanAndFieldCount() {
        assertEquals(
            new PrismAxiomSerialization.WorldTimeState(12000, true),
            PrismAxiomSerialization.decodeWorldTimeState("12000,true")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeWorldTimeState("12000,TRUE")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeWorldTimeState("12000,true,trailing")
        );
    }

    @Test
    void locationRejectsMalformedAndNonFiniteValuesBeforeWorldLookup() {
        String worldUuid = "00000000-0000-0000-0000-000000000000";
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeLocation("missing,fields")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeLocation(worldUuid + ",NaN,0,0,0,0")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PrismAxiomSerialization.decodeLocation("not-a-uuid,0,0,0,0,0")
        );
    }

    @Test
    void byteEncodingRoundTrips() {
        byte[] value = new byte[]{0, 1, -1, 127};
        assertArrayEquals(value, PrismAxiomSerialization.decodeBytes(PrismAxiomSerialization.encodeBytes(value)));
    }
}
