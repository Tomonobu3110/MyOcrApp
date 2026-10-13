"""
実際のCloudWatch Logs / 家計簿履歴から作成したテストケース(test_cases/test_cases.json)を
使って lambda_handler をローカルで実行し、期待値と比較する。

実行方法:
    cd lambda
    python -m unittest test_lambda_function -v
"""
import json
import unittest
from pathlib import Path

from lambda_function import lambda_handler

TEST_CASES_PATH = Path(__file__).parent / "test_cases" / "test_cases.json"


def load_test_cases():
    with open(TEST_CASES_PATH, encoding="utf-8") as f:
        return json.load(f)


def make_test(case, index):
    def test(self):
        response = lambda_handler(case["event"], None)
        self.assertEqual(response["statusCode"], 200, msg=response.get("body"))
        body = json.loads(response["body"])
        expected = case["expected"]
        self.assertEqual(body.get("shop"), expected["shop"], "shop不一致")
        self.assertEqual(body.get("date"), expected["date"], "date不一致")
        self.assertEqual(body.get("payment"), expected["payment"], "payment不一致")
        self.assertEqual(body.get("items"), expected["items"], "items不一致")

    test.__name__ = f"test_case_{index:02d}"
    return test


class TestLambdaFunctionAgainstRealTestCases(unittest.TestCase):
    pass


for i, case in enumerate(load_test_cases(), start=1):
    setattr(TestLambdaFunctionAgainstRealTestCases, f"test_case_{i:02d}", make_test(case, i))


if __name__ == "__main__":
    unittest.main(verbosity=2)
