import json
import logging
import re
import difflib
from collections import Counter

# ログ設定
logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    """
    Lambda関数のエントリポイント
    """
    print(event)
    try:
        # イベントのbodyを取得し、JSONとしてデコード
        body = event.get("body", "")
        if not body:
            raise ValueError("Body is empty")
        
        # bodyのJSONを解析
        start = find_third_quote_position(body)
        end   = find_last_quote_position(body)
        #print(body[start + 1:end])
        message = re.sub(r'\s+', ' ', body[start + 1:end])
        print(message)
        #body_json = json.loads(body_text)
        #message = body_json.get("message", "No message provided")
        
        # ログに出力
        logger.info(f"Received message: {message}")
        print(f"Console log: Received message: {message}")  # コンソール出力
        
        # お店
        shop = ""
        if "FamilyMart" in message:
            shop = "ファミマ"
        if "まいばすけっと" in message:
            shop = "まいばすけっと"

        # 日付
        date = ""
        date_pattern = r'\d{4}年\s?\d{1,2}月\s?\d{1,2}日|\d{4}/\d{2}/\d{2}'
        matches = re.findall(date_pattern, message)
        for match in matches:
            date = match

        # 買ったもの
        items = []
        add_items_from_message(message, items)
        
        # 合計金額
        norm_msg = message.replace("O", "0")
        tel_pattern = r'\d{2}-\d{4}-\d{4}|\d{1}-\d{4}-\d{4}'
        masked_string = re.sub(date_pattern, 'X', re.sub(tel_pattern, 'X', norm_msg))
        numbers = extract_and_convert_numbers(masked_string)
        numbers_as_int = [int(num) for num in numbers]
        payment = find_max_duplicate(numbers_as_int)

        # 正常応答
        return {
            "statusCode": 200,
            "body": json.dumps({
                "message": "解析成功", 
                "shop": shop, 
                "date": date,
                #"numbers": numbers_as_int,
                "payment" : payment,
                "items": items
            }, ensure_ascii=False)
        }
    except json.JSONDecodeError:
        logger.error("Failed to decode JSON from body")
        print("Console log: Failed to decode JSON from body")
        return {
            "statusCode": 400,
            "body": json.dumps({"error": "Invalid JSON in body"})
        }
    except Exception as e:
        logger.error(f"Error processing the event: {e}")
        print(f"Console log: Error processing the event: {e}")
        return {
            "statusCode": 500,
            "body": json.dumps({"error": "Failed to process the message"})
        }

def escape_json_string(input_string):
    # 文字列内の"や\などの特殊文字をエスケープする
    # 例えば、" -> \", \ -> \\
    escaped_string = input_string.replace('"', '\\"').replace('\\', '\\\\')
    return escaped_string

def find_third_quote_position(input_string):
    # ダブルクオートの位置をリストで取得
    positions = [i for i, char in enumerate(input_string) if char == '"']
    
    # 3個目の位置を返す（リストのインデックスが2の位置が3個目）
    if len(positions) >= 3:
        return positions[2]  # 0-based indexなので、3個目はインデックス2
    else:
        return -1  # 3個目のダブルクオートがない場合は-1を返す

def find_last_quote_position(input_string):
    # 最後のダブルクオートの位置を返す
    position = input_string.rfind('"')
    return position

def find_max_duplicate(numbers):
    # 要素の出現回数をカウント
    count = Counter(numbers)
    # 出現回数が2回以上の要素を抽出
    duplicates = [num for num, freq in count.items() if freq >= 2]
    # 重複する要素の最大値を返す（重複がない場合は None を返す）
    return max(duplicates) if duplicates else None

def extract_and_convert_numbers(input_string):
    # カンマ区切りとスペースを許容する正規表現
    pattern = r'\d{1,3}(?:,\s?\d{3})*'
    # パターンマッチングでカンマ区切りの数字をすべて抽出
    matches = re.findall(pattern, input_string)
    # 抽出した文字列を数値に変換（カンマとスペースを削除）
    numbers = [int(match.replace(",", "").replace(" ", "")) for match in matches]
    return numbers

def calculate_similarity(str1, str2):
    return difflib.SequenceMatcher(None, str1, str2).ratio()

def contains_similar_string(long_string, target_string, threshold=0.6):
    # 長い文字列の中で部分文字列をチェック
    for i in range(len(long_string) - len(target_string) + 1):
        substring = long_string[i:i+len(target_string)]
        similarity = difflib.SequenceMatcher(None, substring, target_string).ratio()
        if similarity >= threshold:
            return True
    return False

# JSON形式のデータ（例）
category_items_json = '''
[
    { 
        "category": "ワイン", 
        "item": [
            "ヴェラタティント", 
            "レインボーLカベルネ"
        ]
    },
    { 
        "category": "お酒", 
        "item": [ 
            "アサヒ生ビール",
            "ハイボール",
            "アップルパンチ500",
            "196STゼロWレモン"
        ]
    },
    {
        "category": "アイス",
        "item": [
            "雪見だいふく"
        ]
    },
    {
        "category": "コーヒー",
        "item": [
            "ブレンド",
            "ワンダモーニングショット"
        ]
    },
    {
        "category": "カフェラテ",
        "item": [
            "カフェラテ"
        ]
    },
    {
        "category": "お昼ごはん",
        "item": [
            "コク旨博多豚骨",
            "辛カップラーメン"
        ]
    },
    {
        "category": "ドリンク",
        "item": [
            "ルイボスティー",
            "ザバスミルクヨーグルト"
        ]
    }
]
'''

# JSONをパース
category_items = json.loads(category_items_json)

# メッセージに基づいて品名をチェックする関数
def add_items_from_message(message, items):
    # 各カテゴリとその品名リストをチェック
    for category_data in category_items:
        category = category_data["category"]
        item_list = category_data["item"]
        
        # 品名リストをチェック
        for item in item_list:
            if contains_similar_string(message, item, 0.7):  # 類似度0.7を使用
                items.append(category)
