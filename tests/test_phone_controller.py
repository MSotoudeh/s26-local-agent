import base64
import json
import unittest
from unittest.mock import Mock

from host.phone_controller import (
    MobilerunUsbSession,
    PhoneControlError,
    flatten_nodes,
    parse_bounds,
    parse_provider_token,
    unwrap_api,
)


class PhoneControllerUnitTests(unittest.TestCase):
    def test_parse_provider_token(self):
        raw = 'Row: 0 result={"status":"success","result":"secret-value"}'
        self.assertEqual(parse_provider_token(raw), "secret-value")

    def test_parse_bounds(self):
        self.assertEqual(parse_bounds("28, 1308, 1052, 1454"), (28, 1308, 1052, 1454))

    def test_unwrap_nested_json(self):
        outer = {"status": "success", "result": '{"phone_state":{"packageName":"x"}}'}
        self.assertEqual(unwrap_api(outer)["phone_state"]["packageName"], "x")

    def test_flatten(self):
        tree = [{"text": "A", "children": [{"text": "B", "children": []}]}]
        self.assertEqual([x["text"] for x in flatten_nodes(tree)], ["A", "B"])

    def test_input_text_uses_utf8_base64(self):
        session = MobilerunUsbSession(adb="adb")
        session._request = Mock(return_value={"status": "success"})
        session.input_text("سلام", clear=True)
        _, _, kwargs = session._request.mock_calls[0]
        encoded = kwargs["form"]["base64_text"]
        self.assertEqual(base64.b64decode(encoded).decode("utf-8"), "سلام")
        self.assertTrue(kwargs["form"]["clear"])

    def test_wait_for_package(self):
        session = MobilerunUsbSession(adb="adb")
        session.phone_state = Mock(
            side_effect=[
                {"packageName": "old.app"},
                {"packageName": "target.app"},
            ]
        )
        state = session.wait_for_package(
            "target.app", timeout=0.2, poll_interval=0.0
        )
        self.assertEqual(state["packageName"], "target.app")

    def test_wait_for_package_times_out(self):
        session = MobilerunUsbSession(adb="adb")
        session.phone_state = Mock(return_value={"packageName": "old.app"})
        with self.assertRaises(PhoneControlError):
            session.wait_for_package(
                "target.app", timeout=0.001, poll_interval=0.0
            )

    def test_get_retries_one_transient_timeout(self):
        session = MobilerunUsbSession(adb="adb")
        session._open = True
        session._request = Mock(
            side_effect=[TimeoutError("transient"), {"status": "success", "result": {"x": 1}}]
        )
        self.assertEqual(
            session._get_with_retry("phone_state", timeout=0.1, retry_delay=0.0),
            {"x": 1},
        )
        self.assertEqual(session._request.call_count, 2)


if __name__ == "__main__":
    unittest.main()
