"""Offline H1 status framing/parser. No Bluetooth I/O or setting encoders.

Adapted from H1 Control, copyright (c) 2026 Madhuraj, MIT licensed.
Source commit: a7d08b045164996c7b5153660eb7d640898b3242
See THIRD-PARTY-NOTICES.md for the license and exact upstream references.
"""

from dataclasses import dataclass


SOURCE_COMMIT = "a7d08b045164996c7b5153660eb7d640898b3242"
SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
HEADER = b"\xfe\xdc\xba"
END = 0xEF
GET_SYS_INFO = 0x07
STATUS_SEQUENCE = 1
# Exactly Commands.getStatus(1), also asserted by the upstream captured-fixture tests.
STATUS_REQUEST = bytes.fromhex("FE DC BA C0 07 00 06 01 FF FF FF FF FF EF")
MAX_BODY_LENGTH = 4096
MAX_RECEIVED_BYTES = 16384
NOISE_MODES = {0: "off", 1: "anc", 2: "transparency"}


@dataclass(frozen=True)
class Frame:
    flags: int
    opcode: int
    sequence: int
    status: int | None
    payload: bytes

    @property
    def is_response(self):
        return not bool(self.flags & 0x80)

    def matches_status_request(self, sequence=STATUS_SEQUENCE):
        return self.is_response and self.opcode == GET_SYS_INFO and self.sequence == sequence

    def metadata(self):
        return {
            "kind": "response" if self.is_response else "incoming_command",
            "opcode_hex": f"0x{self.opcode:02x}",
            "sequence": self.sequence,
            "status": self.status,
            "payload_bytes": len(self.payload),
        }


class FrameParser:
    """Bounded incremental RCSP parser; unrecognised bytes are counted, not echoed.

    Incoming realme button frames are intentionally not interpreted by this
    one-shot status utility. No incoming command generates an outbound ACK.
    """

    def __init__(self):
        self.buffer = bytearray()
        self.skipped_bytes = 0

    def feed(self, data):
        if len(self.buffer) + len(data) > MAX_RECEIVED_BYTES:
            raise ValueError("Receive/parser byte limit exceeded")
        self.buffer.extend(data)
        frames = []
        while self.buffer:
            if not self.buffer.startswith(HEADER):
                if len(self.buffer) < len(HEADER) and HEADER.startswith(self.buffer):
                    break
                del self.buffer[0]
                self.skipped_bytes += 1
                continue
            if len(self.buffer) < 7:
                break
            length = int.from_bytes(self.buffer[5:7], "big")
            if not 1 <= length <= MAX_BODY_LENGTH:
                del self.buffer[0]
                self.skipped_bytes += 1
                continue
            total = 8 + length
            if len(self.buffer) < total:
                break
            if self.buffer[total - 1] != END:
                del self.buffer[0]
                self.skipped_bytes += 1
                continue
            raw = bytes(self.buffer[:total])
            del self.buffer[:total]
            flags, opcode = raw[3:5]
            body = raw[7:-1]
            if flags & 0x80:
                frames.append(Frame(flags, opcode, body[0], None, body[1:]))
            elif len(body) >= 2:
                frames.append(Frame(flags, opcode, body[1], body[0], body[2:]))
            else:
                self.skipped_bytes += len(raw)
        return frames


def parse_status_payload(payload):
    """Decode only source-backed public-group attributes; preserve unknowns.

    A malformed TLV is rejected instead of silently reporting partial status.
    Battery percentages are not clamped into a plausible-looking value.
    """

    if not payload or payload[0] != 0xFF:
        raise ValueError("Status response does not contain the public function group")
    attributes = {}
    position = 1
    while position < len(payload):
        if position + 2 > len(payload):
            raise ValueError("Truncated status attribute header")
        length, attribute = payload[position:position + 2]
        end = position + 1 + length
        if length < 1 or end > len(payload):
            raise ValueError("Invalid or truncated status attribute length")
        if attribute in attributes:
            raise ValueError("Duplicate status attribute")
        attributes[attribute] = bytes(payload[position + 2:end])
        position = end

    status = {
        "battery_percent": None,
        "noise_mode": None,
        "reported_noise_modes": [],
        "game_mode": None,
        "spatial_audio": None,
        "firmware_version": None,
        "attribute_inventory": [
            {"attribute_hex": f"0x{attribute:02x}", "value_bytes": len(value)}
            for attribute, value in attributes.items()
        ],
        "interpretation_warnings": [],
    }
    battery = attributes.get(0x00)
    if battery is not None:
        if len(battery) != 1 or battery[0] > 100:
            status["interpretation_warnings"].append("Battery attribute has an unexpected format/value")
        else:
            status["battery_percent"] = battery[0]
    current = attributes.get(0x0D)
    if current is not None:
        if len(current) != 9 or current[0] not in NOISE_MODES:
            status["interpretation_warnings"].append("Current-noise attribute has an unknown format/mode")
        else:
            status["noise_mode"] = NOISE_MODES[current[0]]
    modes = attributes.get(0x0E)
    if modes is not None:
        if not modes or len(modes) != 1 + modes[0] * 9:
            status["interpretation_warnings"].append("Noise-mode list has an unexpected length")
        else:
            # The eight gain bytes per mode are not a music-EQ implementation.
            for index in range(modes[0]):
                code = modes[1 + index * 9]
                status["reported_noise_modes"].append({"code": code, "name": NOISE_MODES.get(code)})
    return status
