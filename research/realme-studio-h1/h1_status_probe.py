"""One owner-authorised H1 status query over Windows Bluetooth RFCOMM.

Default: show an offline plan and send nothing. --execute-status-query is
required for Bluetooth I/O. This utility has no setter, arbitrary-payload,
pairing, service-enablement, retry, firmware or automatic-ACK path.

Protocol: H1 Control (MIT), pinned source in THIRD-PARTY-NOTICES.md.
Windows ABI and service-by-UUID resolution:
https://learn.microsoft.com/en-us/windows/win32/api/ws2bth/ns-ws2bth-sockaddr_bth
https://learn.microsoft.com/en-us/windows/win32/bluetooth/bluetooth-and-connect
https://github.com/microsoft/win32metadata/blob/main/generation/WinSDK/RecompiledIdlHeaders/um/ws2bth.h
"""

import argparse
import ctypes
import datetime
import hashlib
import json
import os
import sys
import time
import uuid
from contextlib import ExitStack
from pathlib import Path

import windows_probe as cache
from rcsp_status import (
    FrameParser,
    MAX_RECEIVED_BYTES,
    SOURCE_COMMIT,
    SPP_UUID,
    STATUS_REQUEST,
    parse_status_payload,
)


SOCKET = ctypes.c_size_t
INT = ctypes.c_int32
ULONG = ctypes.c_uint32
AF_BTH = 32
SOCK_STREAM = 1
BTHPROTO_RFCOMM = 3
SOL_SOCKET = 0xFFFF
SO_ERROR = 0x1007
FIONBIO = 0x8004667E
SOCKET_ERROR = -1
INVALID_SOCKET = ctypes.c_size_t(-1).value
WSAEWOULDBLOCK = 10035
CONNECT_TIMEOUT_SECONDS = 12
RESPONSE_TIMEOUT_SECONDS = 6


class SOCKADDR_BTH(ctypes.Structure):
    # ws2bth.h uses pshpack1.h, not the platform's default alignment.
    _pack_ = 1
    _fields_ = [
        ("addressFamily", ctypes.c_uint16),
        ("btAddr", ctypes.c_uint64),
        ("serviceClassId", cache.GUID),
        ("port", ULONG),
    ]


class FD_SET(ctypes.Structure):
    _fields_ = [("fd_count", ULONG), ("fd_array", SOCKET * 64)]


class TIMEVAL(ctypes.Structure):
    _fields_ = [("tv_sec", INT), ("tv_usec", INT)]


class ProbeError(RuntimeError):
    def __init__(self, stage, message, code=None):
        super().__init__(message)
        self.stage = stage
        self.code = code


def service_address(address):
    target = SOCKADDR_BTH()
    target.addressFamily = AF_BTH
    target.btAddr = address
    target.serviceClassId = cache.GUID.from_buffer_copy(uuid.UUID(SPP_UUID).bytes_le)
    # Windows resolves the channel from the observed SPP UUID. Never guess a port.
    target.port = 0
    return target


def _load_winsock():
    if sys.platform != "win32":
        raise ProbeError("platform", "The live transport requires Windows")
    if (ctypes.sizeof(SOCKADDR_BTH) != 30 or SOCKADDR_BTH.btAddr.offset != 2
            or SOCKADDR_BTH.port.offset != 26 or ctypes.sizeof(TIMEVAL) != 8):
        raise ProbeError("abi", "Unexpected Windows Bluetooth structure layout")
    library = Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32" / "Ws2_32.dll"
    api = ctypes.WinDLL(str(library))
    specifications = {
        "WSAStartup": ([ctypes.c_uint16, ctypes.c_void_p], INT),
        "WSACleanup": ([], INT),
        "WSAGetLastError": ([], INT),
        "socket": ([INT, INT, INT], SOCKET),
        "ioctlsocket": ([SOCKET, INT, ctypes.POINTER(ULONG)], INT),
        "connect": ([SOCKET, ctypes.POINTER(SOCKADDR_BTH), INT], INT),
        "select": ([INT, ctypes.POINTER(FD_SET), ctypes.POINTER(FD_SET),
                    ctypes.POINTER(FD_SET), ctypes.POINTER(TIMEVAL)], INT),
        "getsockopt": ([SOCKET, INT, INT, ctypes.c_void_p, ctypes.POINTER(INT)], INT),
        "getpeername": ([SOCKET, ctypes.POINTER(SOCKADDR_BTH), ctypes.POINTER(INT)], INT),
        "send": ([SOCKET, ctypes.c_void_p, INT, INT], INT),
        "recv": ([SOCKET, ctypes.c_void_p, INT, INT], INT),
        "closesocket": ([SOCKET], INT),
    }
    for name, (arguments, result) in specifications.items():
        function = getattr(api, name)
        function.argtypes = arguments
        function.restype = result
    return api


class QueryOnlyRfcomm:
    """One socket, one fixed outbound packet; every I/O wait has a deadline."""

    def __init__(self, address):
        self.address = address
        self.api = None
        self.handle = INVALID_SOCKET
        self.started = False
        self.connected = False
        self.socket_created = False
        self.socket_closed = False
        self.close_error = None
        self.remote_channel = None
        self.send_calls = 0
        self.bytes_sent = 0

    def _error(self, stage):
        code = self.api.WSAGetLastError()
        return ProbeError(stage, f"Windows socket operation failed (WSA {code})", code)

    def _fdset(self):
        value = FD_SET()
        value.fd_count = 1
        value.fd_array[0] = self.handle
        return value

    def _wait(self, deadline, *, read=False, write=False):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise ProbeError("wait", "Bluetooth I/O deadline expired")
        seconds = int(remaining)
        timeout = TIMEVAL(seconds, int((remaining - seconds) * 1_000_000))
        readable = self._fdset() if read else None
        writable = self._fdset() if write else None
        exceptional = self._fdset()
        count = self.api.select(
            0, ctypes.byref(readable) if readable else None,
            ctypes.byref(writable) if writable else None,
            ctypes.byref(exceptional), ctypes.byref(timeout),
        )
        if count == SOCKET_ERROR:
            raise self._error("select")
        if count == 0:
            raise ProbeError("wait", "No socket event before the Bluetooth I/O deadline")
        return bool(readable and readable.fd_count), bool(writable and writable.fd_count), bool(exceptional.fd_count)

    def _socket_error(self):
        error = INT()
        size = INT(ctypes.sizeof(error))
        if self.api.getsockopt(self.handle, SOL_SOCKET, SO_ERROR, ctypes.byref(error), ctypes.byref(size)) == SOCKET_ERROR:
            raise self._error("getsockopt")
        return error.value

    def open(self):
        self.api = _load_winsock()
        # WSAStartup writes WSADATA; a bounded buffer is larger than either Windows ABI layout.
        data = ctypes.create_string_buffer(512)
        status = self.api.WSAStartup(0x0202, ctypes.byref(data))
        if status:
            raise ProbeError("WSAStartup", "Windows socket initialisation failed", status)
        self.started = True
        self.handle = self.api.socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)
        if self.handle == INVALID_SOCKET:
            raise self._error("socket")
        self.socket_created = True
        nonblocking = ULONG(1)
        # This changes only the local socket's blocking mode, not headset settings.
        if self.api.ioctlsocket(self.handle, ctypes.c_int32(FIONBIO).value, ctypes.byref(nonblocking)) == SOCKET_ERROR:
            raise self._error("ioctlsocket")
        deadline = time.monotonic() + CONNECT_TIMEOUT_SECONDS
        target = service_address(self.address)
        status = self.api.connect(self.handle, ctypes.byref(target), ctypes.sizeof(target))
        if status == SOCKET_ERROR:
            code = self.api.WSAGetLastError()
            if code not in (WSAEWOULDBLOCK, 10036, 10037):
                raise ProbeError("connect", f"Bluetooth SPP connection failed (WSA {code})", code)
            _, writable, exceptional = self._wait(deadline, write=True)
            code = self._socket_error()
            if code or not writable or exceptional:
                raise ProbeError("connect", f"Bluetooth SPP connection did not succeed (WSA {code})", code)
        self.connected = True
        peer = SOCKADDR_BTH()
        size = INT(ctypes.sizeof(peer))
        if self.api.getpeername(self.handle, ctypes.byref(peer), ctypes.byref(size)) == 0:
            if peer.addressFamily == AF_BTH and peer.btAddr == self.address and 1 <= peer.port <= 30:
                self.remote_channel = peer.port

    def send_status_once(self, deadline):
        if not self.connected:
            raise ProbeError("send", "No connected SPP socket")
        if self.send_calls:
            raise ProbeError("send", "A status request was already attempted; no retry is permitted")
        _, writable, exceptional = self._wait(deadline, write=True)
        if not writable or exceptional:
            raise ProbeError("send", "Socket is not ready for the one allowed status request")
        data = ctypes.create_string_buffer(STATUS_REQUEST, len(STATUS_REQUEST))
        self.send_calls += 1
        count = self.api.send(self.handle, ctypes.byref(data), len(STATUS_REQUEST), 0)
        if count == SOCKET_ERROR:
            raise self._error("send")
        self.bytes_sent = count
        # Do not retry a partial send: close the connection and retain the evidence.
        if count != len(STATUS_REQUEST):
            raise ProbeError("send", "The fixed status request was only partially sent; stopping")
        return count

    def read_once(self, deadline, limit):
        readable, _, exceptional = self._wait(deadline, read=True)
        if exceptional:
            raise ProbeError("receive", "SPP socket reported an exceptional event", self._socket_error())
        if not readable:
            return None
        data = ctypes.create_string_buffer(min(4096, limit))
        count = self.api.recv(self.handle, ctypes.byref(data), len(data), 0)
        if count == SOCKET_ERROR:
            code = self.api.WSAGetLastError()
            if code == WSAEWOULDBLOCK:
                return None
            raise ProbeError("receive", f"SPP receive failed (WSA {code})", code)
        if count == 0:
            raise ProbeError("receive", "The headset closed the SPP control connection")
        return data.raw[:count]

    def close(self):
        if self.handle != INVALID_SOCKET:
            result = self.api.closesocket(self.handle)
            self.socket_closed = result == 0
            if result == SOCKET_ERROR:
                self.close_error = self.api.WSAGetLastError()
            self.handle = INVALID_SOCKET
        if self.started:
            self.api.WSACleanup()
            self.started = False


def exchange_status_once(transport, observation, capture):
    """No settings and no ACKs; stop on the first matching response or timeout."""

    deadline = time.monotonic() + RESPONSE_TIMEOUT_SECONDS
    transport.send_status_once(deadline)
    parser = FrameParser()
    total = 0
    try:
        while total < MAX_RECEIVED_BYTES:
            chunk = transport.read_once(deadline, MAX_RECEIVED_BYTES - total)
            if chunk is None:
                continue
            if not chunk:
                raise ProbeError("receive", "Empty receive result")
            total += len(chunk)
            capture["received_chunks"].append({
                "received_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "hex": chunk.hex(" ").upper(),
            })
            for frame in parser.feed(chunk):
                observation["received_frame_metadata"].append(frame.metadata())
                if not frame.matches_status_request():
                    continue
                observation["matching_status_response_received"] = True
                observation["response_status"] = frame.status
                if frame.status != 0:
                    observation["outcome"] = "headset_returned_nonzero_status"
                    return
                observation["reported_status"] = parse_status_payload(frame.payload)
                observation["outcome"] = "status_response_verified"
                return
        raise ProbeError("receive", "Receive byte limit reached without a matching response")
    finally:
        observation["received_bytes"] = total
        observation["unrecognised_bytes"] = parser.skipped_bytes
        observation["incomplete_buffer_bytes"] = len(parser.buffer)


def new_observation():
    return {
        "observed_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "method": "One fixed source-backed GET_SYS_INFO query over Windows native SPP/RFCOMM",
        "protocol_source_commit": SOURCE_COMMIT,
        "device_name": cache.DEFAULT_NAME,
        "device_address": "redacted",
        "service_uuid": SPP_UUID,
        "remote_rfcomm_channel": None,
        "outcome": "not_started",
        "matching_status_response_received": False,
        "received_frame_metadata": [],
        "reported_status": None,
        "safety": {
            "bluetooth_inquiry_issued": False,
            "full_sdp_inventory_requested": False,
            "windows_uuid_resolution_may_use_sdp": True,
            "gatt_operations_requested": False,
            "socket_created": False,
            "control_connection_established": False,
            "socket_closed": False,
            "vendor_query_send_calls": 0,
            "vendor_bytes_sent": 0,
            "setting_change_commands_sent": False,
            "automatic_acks_sent": False,
            "automatic_retries": False,
            "pairing_or_service_enablement_changed": False,
        },
        "limitations": [
            "A status response is not a verified setting-changing command.",
            "Reported noise mode may be stale after physical ANC-button changes.",
            "Firmware, game and spatial state are not inferred from undocumented attributes.",
            "No Fast Pair account-key operation or vendor authentication handshake is attempted.",
            "Audio continuity and physical mode are not independently measured by this utility.",
        ],
    }


def run_probe():
    observation = new_observation()
    capture = {
        "observed_at_utc": observation["observed_at_utc"],
        "device_address": "redacted",
        "protocol_source_commit": SOURCE_COMMIT,
        "service_uuid": SPP_UUID,
        "outbound_request_hex": STATUS_REQUEST.hex(" ").upper(),
        "received_chunks": [],
        "privacy": "Private raw protocol bytes may contain identifiers in undocumented attributes",
    }
    transport = None
    started = time.monotonic()
    try:
        api = cache._load_api()
        info = cache.matching_cached_device(api, cache.DEFAULT_NAME)
        observation["windows_connection_before"] = {
            "connected": bool(info.fConnected),
            "remembered": bool(info.fRemembered),
            "authenticated_pairing": bool(info.fAuthenticated),
        }
        if not (info.fConnected and info.fRemembered and info.fAuthenticated):
            raise ProbeError("preflight", "Headset must already be connected, remembered and paired")
        if SPP_UUID not in cache.enabled_service_uuids(api, info):
            raise ProbeError("preflight", "Observed Windows service inventory does not include SPP")
        transport = QueryOnlyRfcomm(info.Address.ullLong)
        transport.open()
        exchange_status_once(transport, observation, capture)
    except (OSError, RuntimeError, ValueError) as error:
        observation["outcome"] = "stopped_without_retry"
        observation["error"] = {
            "stage": getattr(error, "stage", "preflight_or_parse"),
            "message": str(error),
            "windows_socket_code": getattr(error, "code", None),
        }
    finally:
        if transport:
            transport.close()
            observation["remote_rfcomm_channel"] = transport.remote_channel
            observation["safety"].update({
                "socket_created": transport.socket_created,
                "control_connection_established": transport.connected,
                "socket_closed": transport.socket_closed,
                "vendor_query_send_calls": transport.send_calls,
                "vendor_bytes_sent": transport.bytes_sent,
            })
            if transport.close_error is not None:
                observation["socket_close_error"] = transport.close_error
        observation["elapsed_seconds"] = round(time.monotonic() - started, 3)
        capture["actual_vendor_send_calls"] = observation["safety"]["vendor_query_send_calls"]
        capture["actual_vendor_bytes_sent"] = observation["safety"]["vendor_bytes_sent"]
        capture["outcome"] = observation["outcome"]
    return observation, capture


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute-status-query", action="store_true", help="Authorise exactly one fixed vendor status query")
    parser.add_argument("--output", type=Path, help="NEW redacted observation JSON")
    parser.add_argument("--private-capture", type=Path, help="NEW PRIVATE raw-byte capture JSON, preferably outside the repository")
    args = parser.parse_args(argv)
    if not args.execute_status_query:
        print(json.dumps({
            "mode": "offline_plan_only",
            "device": cache.DEFAULT_NAME,
            "service_uuid": SPP_UUID,
            "source_commit": SOURCE_COMMIT,
            "allowed_request_hex": STATUS_REQUEST.hex(" ").upper(),
            "socket_opened": False,
            "vendor_bytes_sent": 0,
            "setting_encoders_available": False,
        }, indent=2))
        return 0
    if not args.output or not args.private_capture:
        parser.error("Execution requires both --output and --private-capture with new filenames")
    if args.output.resolve() == args.private_capture.resolve():
        parser.error("Public observation and private capture must be different files")
    try:
        # Reserve both files before inspecting/connecting so a filename conflict cannot lead to a repeated query.
        with ExitStack() as files:
            public_output = files.enter_context(args.output.open("x", encoding="utf-8", newline="\n"))
            private_output = files.enter_context(args.private_capture.open("x", encoding="utf-8", newline="\n"))
            observation, capture = run_probe()
            raw = json.dumps(capture, indent=2, ensure_ascii=False) + "\n"
            private_output.write(raw)
            observation["private_capture_sha256"] = hashlib.sha256(raw.encode("utf-8")).hexdigest()
            public_output.write(json.dumps(observation, indent=2, ensure_ascii=False) + "\n")
        print(json.dumps(observation, indent=2, ensure_ascii=False))
        return 0 if observation["outcome"] == "status_response_verified" else 1
    except OSError as error:
        print(json.dumps({"error": str(error), "query_not_retried": True}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
