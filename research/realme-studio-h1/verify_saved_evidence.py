"""Verify retained evidence offline; never connect to Bluetooth or the Internet."""

import argparse
import datetime
import hashlib
import json
from pathlib import Path

from rcsp_status import FrameParser, STATUS_REQUEST, parse_status_payload


DIRECTORY = Path(__file__).resolve().parent


def verify_capture(capture_path, observation):
    raw_file = capture_path.read_bytes()
    capture = json.loads(raw_file)
    request = bytes.fromhex(capture["outbound_request_hex"])
    received = b"".join(bytes.fromhex(chunk["hex"]) for chunk in capture["received_chunks"])
    parser = FrameParser()
    frames = parser.feed(received)
    matching = [frame for frame in frames if frame.matches_status_request() and frame.status == 0]
    if request != STATUS_REQUEST or len(matching) != 1:
        raise ValueError("Saved capture does not contain the fixed query and one matching successful reply")
    decoded = parse_status_payload(matching[0].payload)
    if decoded != observation["reported_status"]:
        raise ValueError("Saved capture and public status interpretation differ")
    if len(received) != observation["received_bytes"]:
        raise ValueError("Saved receive byte count differs")
    if capture["actual_vendor_send_calls"] != 1 or capture["actual_vendor_bytes_sent"] != len(STATUS_REQUEST):
        raise ValueError("Saved send count is not one complete fixed query")
    if parser.skipped_bytes or parser.buffer:
        raise ValueError("Saved response contains skipped or incomplete bytes")
    actual_hash = hashlib.sha256(raw_file).hexdigest()
    normalized_hash = hashlib.sha256(raw_file.replace(b"\r\n", b"\n")).hexdigest()
    recorded = observation["private_capture_sha256"]
    if recorded not in (actual_hash, normalized_hash):
        raise ValueError("Saved capture does not match the observation's hash")
    return {
        "actual_capture_file_sha256": actual_hash,
        "lf_normalized_capture_sha256": normalized_hash,
        "original_observation_hash": recorded,
        "original_hash_scope": "exact_file_bytes" if recorded == actual_hash else "LF-normalized_UTF-8_JSON_payload",
        "capture_file_bytes": len(raw_file),
        "protocol_received_bytes": len(received),
        "protocol_received_sha256": hashlib.sha256(received).hexdigest(),
        "matching_successful_status_responses": len(matching),
        "public_status_matches_private_bytes": True,
        "reported_battery_percent": decoded["battery_percent"],
        "reported_noise_mode": decoded["noise_mode"],
        "reported_noise_modes": decoded["reported_noise_modes"],
        "original_files_modified": False,
        "note": (
            "The first observation hashed its LF JSON payload before Windows text I/O wrote CRLF. "
            "The exact original file digest is recorded here without rewriting either original. "
            "The utility now explicitly writes LF, with an offline regression test; no live query was repeated."
            if recorded != actual_hash else "The digest covers the exact saved capture file bytes."
        ),
    }


def verify_source_files(directory, source_record):
    checked = []
    for record in source_record["source_files"]:
        payload = (directory / record["path"]).read_bytes()
        if len(payload) != record["bytes"] or hashlib.sha256(payload).hexdigest() != record["sha256"]:
            raise ValueError(f"Pinned source hash mismatch: {record['path']}")
        checked.append(record["path"])
    return {"verified_source_files": checked, "source_commit": source_record["source_commit"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--private-capture", type=Path)
    parser.add_argument("--source-directory", type=Path)
    parser.add_argument("--output", type=Path, help="Optional NEW offline verification JSON")
    args = parser.parse_args()
    if not args.private_capture and not args.source_directory:
        parser.error("Select a saved capture and/or retained source directory")
    result = {
        "verified_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "method": "Offline byte/hash and protocol interpretation verification",
        "bluetooth_operations": False,
        "network_requests": False,
    }
    if args.private_capture:
        observation = json.loads((DIRECTORY / "windows-status-observation.json").read_text(encoding="utf-8"))
        result["status_capture"] = verify_capture(args.private_capture, observation)
    if args.source_directory:
        source = json.loads((DIRECTORY / "h1-control-evidence.json").read_text(encoding="utf-8"))
        result["source_files"] = verify_source_files(args.source_directory, source)
    payload = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        with args.output.open("x", encoding="utf-8", newline="\n") as output:
            output.write(payload)
    print(payload, end="")


if __name__ == "__main__":
    main()
