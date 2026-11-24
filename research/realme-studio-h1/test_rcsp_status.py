"""Offline tests only; upstream fixture attribution is in THIRD-PARTY-NOTICES.md."""

import unittest

import rcsp_status as protocol


# Upstream author labels this a firmware 2.4.0 capture. It is not from this owner's unit.
UPSTREAM_STATUS_RESPONSE = bytes.fromhex(
    "FE DC BA 00 07 00 58 00 02 FF 02 00 26 16 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 "
    "02 06 00 02 09 00 08 0A 00 00 00 00 00 00 00 0A 0D 00 00 00 00 00 00 00 00 00 1D 0E 03 00 00 00 00 00 00 00 "
    "00 00 01 40 00 00 00 40 00 00 00 02 40 00 00 00 40 00 00 00 02 0F 00 EF"
)


class StatusParserTests(unittest.TestCase):
    def test_fixed_request_matches_upstream(self):
        self.assertEqual(protocol.STATUS_REQUEST.hex(" ").upper(), "FE DC BA C0 07 00 06 01 FF FF FF FF FF EF")
        self.assertEqual(len(protocol.STATUS_REQUEST), 14)
        self.assertEqual(protocol.STATUS_REQUEST[4], 0x07)

    def test_upstream_fixture_status(self):
        frames = protocol.FrameParser().feed(UPSTREAM_STATUS_RESPONSE)
        self.assertEqual(len(frames), 1)
        status = protocol.parse_status_payload(frames[0].payload)
        self.assertEqual(status["battery_percent"], 38)
        self.assertEqual(status["noise_mode"], "off")
        self.assertEqual([item["name"] for item in status["reported_noise_modes"]], ["off", "anc", "transparency"])
        self.assertIsNone(status["firmware_version"])
        self.assertIsNone(status["game_mode"])
        self.assertEqual(status["interpretation_warnings"], [])

    def test_sequence_is_checked_not_just_opcode(self):
        frame = protocol.FrameParser().feed(UPSTREAM_STATUS_RESPONSE)[0]
        self.assertFalse(frame.matches_status_request())  # fixture SN=2, this utility SN=1
        self.assertTrue(frame.matches_status_request(sequence=2))

    def test_commands_are_not_accepted_as_responses(self):
        frame = protocol.FrameParser().feed(protocol.STATUS_REQUEST)[0]
        self.assertFalse(frame.is_response)
        self.assertFalse(frame.matches_status_request())

    def test_split_at_every_boundary(self):
        for boundary in range(len(UPSTREAM_STATUS_RESPONSE) + 1):
            with self.subTest(boundary=boundary):
                parser = protocol.FrameParser()
                frames = parser.feed(UPSTREAM_STATUS_RESPONSE[:boundary]) + parser.feed(UPSTREAM_STATUS_RESPONSE[boundary:])
                self.assertEqual(len(frames), 1)
                self.assertEqual(protocol.parse_status_payload(frames[0].payload)["battery_percent"], 38)

    def test_coalesced_frames_and_unknown_prefix(self):
        parser = protocol.FrameParser()
        frames = parser.feed(b"unknown" + UPSTREAM_STATUS_RESPONSE * 2)
        self.assertEqual(len(frames), 2)
        self.assertEqual(parser.skipped_bytes, 7)

    def test_bad_trailer_and_oversized_length_recover(self):
        invalid = UPSTREAM_STATUS_RESPONSE[:-1] + b"\x00"
        oversized = bytes.fromhex("FE DC BA 00 07 FF FF")
        frames = protocol.FrameParser().feed(invalid + oversized + UPSTREAM_STATUS_RESPONSE)
        self.assertEqual(len(frames), 1)

    def test_partial_header_is_retained(self):
        parser = protocol.FrameParser()
        self.assertEqual(parser.feed(b"\xfe\xdc"), [])
        self.assertEqual(len(parser.feed(UPSTREAM_STATUS_RESPONSE[2:])), 1)

    def test_receive_size_is_bounded(self):
        with self.assertRaises(ValueError):
            protocol.FrameParser().feed(b"\x00" * (protocol.MAX_RECEIVED_BYTES + 1))

    def test_malformed_tlv_is_rejected(self):
        for payload in (b"", b"\x00", b"\xff\x00\x00", b"\xff\x02\x00", b"\xff\x02"):
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                protocol.parse_status_payload(payload)

    def test_duplicate_attribute_is_rejected(self):
        with self.assertRaises(ValueError):
            protocol.parse_status_payload(bytes.fromhex("FF 02 00 20 02 00 30"))

    def test_invalid_battery_is_not_clamped(self):
        result = protocol.parse_status_payload(bytes.fromhex("FF 02 00 FF"))
        self.assertIsNone(result["battery_percent"])
        self.assertTrue(result["interpretation_warnings"])

    def test_unknown_attributes_are_inventory_only(self):
        result = protocol.parse_status_payload(bytes.fromhex("FF 03 44 AA BB"))
        self.assertEqual(result["attribute_inventory"], [{"attribute_hex": "0x44", "value_bytes": 2}])
        self.assertIsNone(result["noise_mode"])
        self.assertNotIn("AA", str(result))

    def test_unknown_noise_mode_is_not_invented(self):
        result = protocol.parse_status_payload(bytes.fromhex("FF 0A 0D 55 00 00 00 00 00 00 00 00"))
        self.assertIsNone(result["noise_mode"])
        self.assertTrue(result["interpretation_warnings"])


if __name__ == "__main__":
    unittest.main()
