import json
import unittest

from host.phone_controller import (
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


if __name__ == "__main__":
    unittest.main()
