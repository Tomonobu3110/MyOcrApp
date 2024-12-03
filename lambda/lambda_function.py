import json
import logging
import re
import difflib
from collections import Counter

# ���O�ݒ�
logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    """
    Lambda�֐��̃G���g���|�C���g
    """
    print(event)
    try:
        # �C�x���g��body���擾���AJSON�Ƃ��ăf�R�[�h
        body = event.get("body", "")
        if not body:
            raise ValueError("Body is empty")
        
        # body��JSON�����
        start = find_third_quote_position(body)
        end   = find_last_quote_position(body)
        #print(body[start + 1:end])
        message = re.sub(r'\s+', ' ', body[start + 1:end])
        print(message)
        #body_json = json.loads(body_text)
        #message = body_json.get("message", "No message provided")
        
        # ���O�ɏo��
        logger.info(f"Received message: {message}")
        print(f"Console log: Received message: {message}")  # �R���\�[���o��
        
        # ���X
        shop = ""
        if "FamilyMart" in message:
            shop = "�t�@�~�}"
        if "�܂��΂�������" in message:
            shop = "�܂��΂�������"

        # ���t
        date = ""
        date_pattern = r'\d{4}�N\s?\d{1,2}��\s?\d{1,2}��|\d{4}/\d{2}/\d{2}'
        matches = re.findall(date_pattern, message)
        for match in matches:
            date = match

        # ����������
        items = []
        add_items_from_message(message, items)
        
        # ���v���z
        norm_msg = message.replace("O", "0")
        tel_pattern = r'\d{2}-\d{4}-\d{4}|\d{1}-\d{4}-\d{4}'
        masked_string = re.sub(date_pattern, 'X', re.sub(tel_pattern, 'X', norm_msg))
        numbers = extract_and_convert_numbers(masked_string)
        numbers_as_int = [int(num) for num in numbers]
        payment = find_max_duplicate(numbers_as_int)

        # ���퉞��
        return {
            "statusCode": 200,
            "body": json.dumps({
                "message": "��͐���", 
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
    # ���������"��\�Ȃǂ̓��ꕶ�����G�X�P�[�v����
    # �Ⴆ�΁A" -> \", \ -> \\
    escaped_string = input_string.replace('"', '\\"').replace('\\', '\\\\')
    return escaped_string

def find_third_quote_position(input_string):
    # �_�u���N�I�[�g�̈ʒu�����X�g�Ŏ擾
    positions = [i for i, char in enumerate(input_string) if char == '"']
    
    # 3�ڂ̈ʒu��Ԃ��i���X�g�̃C���f�b�N�X��2�̈ʒu��3�ځj
    if len(positions) >= 3:
        return positions[2]  # 0-based index�Ȃ̂ŁA3�ڂ̓C���f�b�N�X2
    else:
        return -1  # 3�ڂ̃_�u���N�I�[�g���Ȃ��ꍇ��-1��Ԃ�

def find_last_quote_position(input_string):
    # �Ō�̃_�u���N�I�[�g�̈ʒu��Ԃ�
    position = input_string.rfind('"')
    return position

def find_max_duplicate(numbers):
    # �v�f�̏o���񐔂��J�E���g
    count = Counter(numbers)
    # �o���񐔂�2��ȏ�̗v�f�𒊏o
    duplicates = [num for num, freq in count.items() if freq >= 2]
    # �d������v�f�̍ő�l��Ԃ��i�d�����Ȃ��ꍇ�� None ��Ԃ��j
    return max(duplicates) if duplicates else None

def extract_and_convert_numbers(input_string):
    # �J���}��؂�ƃX�y�[�X�����e���鐳�K�\��
    pattern = r'\d{1,3}(?:,\s?\d{3})*'
    # �p�^�[���}�b�`���O�ŃJ���}��؂�̐��������ׂĒ��o
    matches = re.findall(pattern, input_string)
    # ���o����������𐔒l�ɕϊ��i�J���}�ƃX�y�[�X���폜�j
    numbers = [int(match.replace(",", "").replace(" ", "")) for match in matches]
    return numbers

def calculate_similarity(str1, str2):
    return difflib.SequenceMatcher(None, str1, str2).ratio()

def contains_similar_string(long_string, target_string, threshold=0.6):
    # ����������̒��ŕ�����������`�F�b�N
    for i in range(len(long_string) - len(target_string) + 1):
        substring = long_string[i:i+len(target_string)]
        similarity = difflib.SequenceMatcher(None, substring, target_string).ratio()
        if similarity >= threshold:
            return True
    return False

# JSON�`���̃f�[�^�i��j
category_items_json = '''
[
    { 
        "category": "���C��", 
        "item": [
            "���F���^�e�B���g", 
            "���C���{�[L�J�x���l"
        ]
    },
    { 
        "category": "����", 
        "item": [ 
            "�A�T�q���r�[��",
            "�n�C�{�[��",
            "�A�b�v���p���`500",
            "196ST�[��W������"
        ]
    },
    {
        "category": "�A�C�X",
        "item": [
            "�ጩ�����ӂ�"
        ]
    },
    {
        "category": "�R�[�q�[",
        "item": [
            "�u�����h",
            "�����_���[�j���O�V���b�g"
        ]
    },
    {
        "category": "�J�t�F���e",
        "item": [
            "�J�t�F���e"
        ]
    },
    {
        "category": "�������͂�",
        "item": [
            "�R�N�|�����؍�",
            "�h�J�b�v���[����"
        ]
    },
    {
        "category": "�h�����N",
        "item": [
            "���C�{�X�e�B�[",
            "�U�o�X�~���N���[�O���g"
        ]
    }
]
'''

# JSON���p�[�X
category_items = json.loads(category_items_json)

# ���b�Z�[�W�Ɋ�Â��ĕi�����`�F�b�N����֐�
def add_items_from_message(message, items):
    # �e�J�e�S���Ƃ��̕i�����X�g���`�F�b�N
    for category_data in category_items:
        category = category_data["category"]
        item_list = category_data["item"]
        
        # �i�����X�g���`�F�b�N
        for item in item_list:
            if contains_similar_string(message, item, 0.7):  # �ގ��x0.7���g�p
                items.append(category)
