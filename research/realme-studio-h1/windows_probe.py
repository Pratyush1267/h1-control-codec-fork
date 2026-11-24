"""Inspect an already-known headset's Windows Bluetooth cache, without inquiry.

No sockets, pairing changes, service enablement, GATT writes, or vendor payloads
are used. Bluetooth addresses and unrelated devices are omitted from output.

API scope:
https://learn.microsoft.com/en-us/windows/win32/api/bluetoothapis/ns-bluetoothapis-bluetooth_device_search_params
https://learn.microsoft.com/en-us/windows/win32/api/bluetoothapis/nf-bluetoothapis-bluetoothenumerateinstalledservices
"""

import argparse
import ctypes
import datetime
import json
import os
import sys
import uuid
from pathlib import Path


DWORD = ctypes.c_uint32
BOOL = ctypes.c_int32
WORD = ctypes.c_uint16
BYTE = ctypes.c_uint8
HANDLE = ctypes.c_void_p
ERROR_SUCCESS = 0
ERROR_MORE_DATA = 234
ERROR_NO_MORE_ITEMS = 259
MAX_SERVICES = 256
DEFAULT_NAME = "realme TechLife Studio H1"


class SYSTEMTIME(ctypes.Structure):
    _fields_ = [
        ("wYear", WORD),
        ("wMonth", WORD),
        ("wDayOfWeek", WORD),
        ("wDay", WORD),
        ("wHour", WORD),
        ("wMinute", WORD),
        ("wSecond", WORD),
        ("wMilliseconds", WORD),
    ]


class BLUETOOTH_ADDRESS(ctypes.Union):
    _fields_ = [("ullLong", ctypes.c_uint64), ("rgBytes", BYTE * 6)]


class BLUETOOTH_DEVICE_INFO(ctypes.Structure):
    _fields_ = [
        ("dwSize", DWORD),
        ("Address", BLUETOOTH_ADDRESS),
        ("ulClassofDevice", DWORD),
        ("fConnected", BOOL),
        ("fRemembered", BOOL),
        ("fAuthenticated", BOOL),
        ("stLastSeen", SYSTEMTIME),
        ("stLastUsed", SYSTEMTIME),
        ("szName", ctypes.c_wchar * 248),
    ]


class BLUETOOTH_DEVICE_SEARCH_PARAMS(ctypes.Structure):
    _fields_ = [
        ("dwSize", DWORD),
        ("fReturnAuthenticated", BOOL),
        ("fReturnRemembered", BOOL),
        ("fReturnUnknown", BOOL),
        ("fReturnConnected", BOOL),
        ("fIssueInquiry", BOOL),
        ("cTimeoutMultiplier", BYTE),
        ("hRadio", HANDLE),
    ]


class GUID(ctypes.Structure):
    _fields_ = [
        ("Data1", DWORD),
        ("Data2", WORD),
        ("Data3", WORD),
        ("Data4", BYTE * 8),
    ]


KNOWN_SERVICES = {
    "00001101-0000-1000-8000-00805f9b34fb": "Serial Port (SPP)",
    "0000110b-0000-1000-8000-00805f9b34fb": "Audio Sink (A2DP)",
    "0000110c-0000-1000-8000-00805f9b34fb": "A/V Remote Control Target",
    "0000110e-0000-1000-8000-00805f9b34fb": "A/V Remote Control",
    "0000110f-0000-1000-8000-00805f9b34fb": "A/V Remote Control Controller",
    "0000111e-0000-1000-8000-00805f9b34fb": "Hands-Free (HFP)",
    "df21fe2c-2515-4fdb-8886-f12c4d67927c": "Google Fast Pair Message Stream",
}


def cache_only_search_params():
    params = BLUETOOTH_DEVICE_SEARCH_PARAMS()
    params.dwSize = ctypes.sizeof(params)
    params.fReturnAuthenticated = 1
    params.fReturnRemembered = 1
    params.fReturnConnected = 1
    params.fReturnUnknown = 0
    params.fIssueInquiry = 0
    params.cTimeoutMultiplier = 0
    params.hRadio = None
    return params


def service_record(value):
    canonical = str(uuid.UUID(str(value)))
    return {
        "uuid": canonical,
        "recognized_name": KNOWN_SERVICES.get(canonical),
        "headset_control_verified": False,
    }


def _load_api():
    if sys.platform != "win32":
        raise RuntimeError("This cache probe requires Windows")
    if ctypes.sizeof(BLUETOOTH_DEVICE_INFO) != 560 or ctypes.sizeof(GUID) != 16:
        raise RuntimeError("Unexpected native structure layout; refusing API calls")
    library = Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32" / "bthprops.cpl"
    api = ctypes.WinDLL(str(library), use_last_error=True)
    api.BluetoothFindFirstDevice.argtypes = [
        ctypes.POINTER(BLUETOOTH_DEVICE_SEARCH_PARAMS),
        ctypes.POINTER(BLUETOOTH_DEVICE_INFO),
    ]
    api.BluetoothFindFirstDevice.restype = HANDLE
    api.BluetoothFindNextDevice.argtypes = [HANDLE, ctypes.POINTER(BLUETOOTH_DEVICE_INFO)]
    api.BluetoothFindNextDevice.restype = BOOL
    api.BluetoothFindDeviceClose.argtypes = [HANDLE]
    api.BluetoothFindDeviceClose.restype = BOOL
    api.BluetoothEnumerateInstalledServices.argtypes = [
        HANDLE,
        ctypes.POINTER(BLUETOOTH_DEVICE_INFO),
        ctypes.POINTER(DWORD),
        ctypes.POINTER(GUID),
    ]
    api.BluetoothEnumerateInstalledServices.restype = DWORD
    return api


def matching_cached_device(api, expected_name):
    params = cache_only_search_params()
    info = BLUETOOTH_DEVICE_INFO()
    info.dwSize = ctypes.sizeof(info)
    ctypes.set_last_error(0)
    handle = api.BluetoothFindFirstDevice(ctypes.byref(params), ctypes.byref(info))
    if not handle:
        error = ctypes.get_last_error()
        if error == ERROR_NO_MORE_ITEMS:
            raise RuntimeError("Windows returned no known Bluetooth devices")
        raise ctypes.WinError(error)

    matches = {}
    try:
        while True:
            if info.szName.casefold() == expected_name.casefold():
                matches[info.Address.ullLong] = BLUETOOTH_DEVICE_INFO.from_buffer_copy(bytes(info))
            info = BLUETOOTH_DEVICE_INFO()
            info.dwSize = ctypes.sizeof(info)
            ctypes.set_last_error(0)
            if not api.BluetoothFindNextDevice(handle, ctypes.byref(info)):
                error = ctypes.get_last_error()
                if error not in (ERROR_SUCCESS, ERROR_NO_MORE_ITEMS):
                    raise ctypes.WinError(error)
                break
    finally:
        if not api.BluetoothFindDeviceClose(handle):
            raise ctypes.WinError(ctypes.get_last_error())

    if len(matches) != 1:
        raise RuntimeError(
            f"Expected one exact-name headset in the Windows cache; found {len(matches)}. "
            "No services were inspected."
        )
    return next(iter(matches.values()))


def enabled_service_uuids(api, info):
    # Microsoft describes this as installed/enabled services, NOT fresh SDP.
    # One bounded buffer avoids a retry or active inquiry of any kind.
    buffer = (GUID * MAX_SERVICES)()
    count = DWORD(MAX_SERVICES)
    status = api.BluetoothEnumerateInstalledServices(
        None, ctypes.byref(info), ctypes.byref(count), buffer
    )
    if status == ERROR_MORE_DATA:
        raise RuntimeError("Installed-service inventory exceeded the bounded buffer; no complete result")
    if status != ERROR_SUCCESS:
        raise ctypes.WinError(status)
    if count.value > MAX_SERVICES:
        raise RuntimeError("Invalid service count; refusing to read outside the buffer")
    return sorted({str(uuid.UUID(bytes_le=bytes(buffer[i]))) for i in range(count.value)})


def build_observation(info, services):
    return {
        "observed_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "method": "Windows native Bluetooth cache and installed/enabled-service enumeration",
        "device": {
            "name": info.szName,
            "windows_reports_connected": bool(info.fConnected),
            "remembered": bool(info.fRemembered),
            "authenticated_pairing": bool(info.fAuthenticated),
            "class_of_device_hex": f"0x{info.ulClassofDevice:06x}",
            "address": "redacted",
            "physical_model_label_verified": False,
            "firmware_version": None,
        },
        "installed_enabled_services": [service_record(value) for value in services],
        "safety": {
            "bluetooth_inquiry_issued": False,
            "fresh_sdp_requested": False,
            "gatt_discovery_requested": False,
            "socket_opened": False,
            "vendor_commands_sent": False,
            "pairing_or_service_state_changed": False,
        },
        "limitations": [
            "Enabled Windows services are not the complete fresh remote SDP database.",
            "No BLE advertising or GATT services were measured.",
            "The Bluetooth display name does not verify the printed model or firmware.",
            "Service presence or absence here does not establish remote ANC/EQ support.",
        ],
    }


def save_new_json(path, observation):
    payload = json.dumps(observation, indent=2, ensure_ascii=False) + "\n"
    # Exclusive creation: never overwrite an existing observation or user file.
    with Path(path).open("x", encoding="utf-8") as output:
        output.write(payload)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", default=DEFAULT_NAME, help="Exact name of the already-known headset")
    parser.add_argument("--output", type=Path, help="Optional NEW redacted JSON observation file")
    args = parser.parse_args()
    try:
        api = _load_api()
        info = matching_cached_device(api, args.name)
        observation = build_observation(info, enabled_service_uuids(api, info))
        if args.output:
            save_new_json(args.output, observation)
        print(json.dumps(observation, indent=2, ensure_ascii=False))
        return 0
    except (OSError, RuntimeError, ValueError) as error:
        print(json.dumps({"error": str(error), "vendor_commands_sent": False}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
