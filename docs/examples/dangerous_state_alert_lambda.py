"""Demo VF lambda that alerts on transitions into configured dangerous states.

The VF Lambda wrapper is expected to perform the normal run-start handshake before
calling ``handler``. This example uses only Python's standard library.
"""

import json
import time
import urllib.error
import urllib.parse
import urllib.request


VARIABLE_NAME = "danger-monitor-state-v1"
RULE_ID = "danger-transition-v1"

DANGEROUS_LOCATION_STATES = {"FALL_EMERGENCY", "FIRE", "PANIC"}
DANGEROUS_BOOLEAN_FIELDS = {"alarm", "smoke", "coAlarm", "panic"}
DANGEROUS_FALL_STATES = {"fall_detected", "fall_confirmed", "calling"}


def _url(api_host, path):
    base = api_host.rstrip("/")
    return f"{base}{path}" if base.endswith("/vf") else f"{base}/vf{path}"


def _call(method, url, api_key, body=None, content_type=None):
    headers = {"LAMBDA_API_KEY": api_key}
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, response.headers.get("Content-Type", ""), response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} failed: HTTP {error.code}: {detail}") from error


def _check_action_response(body):
    response = json.loads(body or b"{}")
    if response.get("resultCode", 0) != 0:
        raise RuntimeError(f"VF API error: {response}")
    return response


def _load_previous(api_host, api_key):
    name = urllib.parse.quote(VARIABLE_NAME, safe="")
    status, content_type, body = _call(
        "GET", _url(api_host, f"/variables/{name}"), api_key)
    if status == 204:
        return None
    if "application/octet-stream" not in content_type:
        _check_action_response(body)
        raise RuntimeError(f"Unexpected variable content type: {content_type}")
    return json.loads(body)


def _save_current(api_host, api_key, state):
    name = urllib.parse.quote(VARIABLE_NAME, safe="")
    body = json.dumps(state, separators=(",", ":")).encode()
    _, _, response = _call(
        "PUT",
        _url(api_host, f"/variables/{name}"),
        api_key,
        body,
        "application/octet-stream",
    )
    _check_action_response(response)


def _current_state(event):
    location = event.get("location") or {}
    location_state = event.get("newLocationState")
    if location_state is None:
        location_state = location.get("currentState")

    devices = {
        device["deviceUuid"]: dict(device.get("currentState") or {})
        for device in event.get("locationDevices") or []
        if device.get("deviceUuid")
    }
    changed_device = event.get("deviceUuid")
    if changed_device and event.get("newDeviceState") is not None:
        devices.setdefault(changed_device, {}).update(event["newDeviceState"])

    return {
        "locationId": event["locationId"],
        "locationState": location_state,
        "devices": devices,
        "eventTime": event["time"],
    }


def _dangerous_changes(previous, current):
    reasons = []
    old_location = str(previous.get("locationState") or "").upper()
    new_location = str(current.get("locationState") or "").upper()
    if (new_location in DANGEROUS_LOCATION_STATES and
            old_location not in DANGEROUS_LOCATION_STATES):
        reasons.append({
            "resourceType": "LOCATION",
            "resourceId": str(current["locationId"]),
            "field": "state",
            "previousValue": previous.get("locationState"),
            "newValue": current.get("locationState"),
        })

    old_devices = previous.get("devices") or {}
    for device_uuid, new_state in current.get("devices", {}).items():
        old_state = old_devices.get(device_uuid) or {}
        for field in DANGEROUS_BOOLEAN_FIELDS:
            if new_state.get(field) is True and old_state.get(field) is not True:
                reasons.append({
                    "resourceType": "DEVICE",
                    "resourceId": device_uuid,
                    "field": field,
                    "previousValue": old_state.get(field),
                    "newValue": True,
                })

        old_fall = str(old_state.get("fallStatus") or "").lower()
        new_fall = str(new_state.get("fallStatus") or "").lower()
        if new_fall in DANGEROUS_FALL_STATES and old_fall not in DANGEROUS_FALL_STATES:
            reasons.append({
                "resourceType": "DEVICE",
                "resourceId": device_uuid,
                "field": "fallStatus",
                "previousValue": old_state.get("fallStatus"),
                "newValue": new_state.get("fallStatus"),
            })
    return reasons


def _create_alert(api_host, api_key, lambda_input, event, reasons):
    payload = {
        "idempotencyKey": f"{lambda_input['id']}:{event['key']}:{RULE_ID}",
        "ruleId": RULE_ID,
        "severity": "CRITICAL",
        "locationId": event["locationId"],
        "occurredAt": event["time"],
        "runId": lambda_input["runId"],
        "eventKey": event["key"],
        "reasons": reasons,
    }
    _, _, response = _call(
        "POST",
        _url(api_host, "/alerts"),
        api_key,
        json.dumps(payload, separators=(",", ":")).encode(),
        "application/json",
    )
    _check_action_response(response)


def handler(lambda_input, _context=None):
    started_at = int(time.time() * 1000)
    try:
        api_host = lambda_input["apiHosts"][0]
        api_key = lambda_input["apiKey"]
        previous = _load_previous(api_host, api_key)

        for event in sorted(lambda_input.get("inputs") or [], key=lambda item: item["time"]):
            current = _current_state(event)
            if previous is not None:
                reasons = _dangerous_changes(previous, current)
                if reasons:
                    _create_alert(api_host, api_key, lambda_input, event, reasons)

            # Advance the snapshot only after alert creation succeeds. A retry after
            # an alert succeeds is safe because POST /alerts is idempotent.
            _save_current(api_host, api_key, current)
            previous = current

        return {
            "startCode": 0,
            "startTime": started_at,
            "endTime": int(time.time() * 1000),
        }
    except Exception as error:  # The worker maps a nonblank errorMessage to LAMBDA_ERROR.
        return {
            "startCode": 0,
            "startTime": started_at,
            "endTime": int(time.time() * 1000),
            "errorMessage": str(error),
        }
