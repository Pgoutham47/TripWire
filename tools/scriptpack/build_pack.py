#!/usr/bin/env python3
"""Builds the bundled script pack (PRD 9.6) from the definitions below.

Output: core/src/main/resources/script_pack.json

Everything about scams that can change without an app update lives here: families and their
signal weights, stages, hard rules, keyword rules, app lists, known-bad lists and every
warning sentence in every supported language. Edit this file, then run:

    python3 tools/scriptpack/build_pack.py
    python3 tools/scriptpack/sign_pack.py core/src/main/resources/script_pack.json   # for updates

Signal names are documented in core/src/main/kotlin/com/tripwire/core/script/ScriptPack.kt.
Weights are log-odds contributions against "benign". They are starting points to be tuned on
the test sets (PRD 16), not measured values.
"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "core", "src", "main", "resources", "script_pack.json")

VERSION = 2  # 2: authority_impersonation became authority_claim (a claim, not a verdict)
LANGS = ["en", "hi"]


def L(en, hi):
    return {"en": en, "hi": hi}


# ---------------------------------------------------------------------------------------------
# Families (PRD 9.2 - 9.4)
# ---------------------------------------------------------------------------------------------

fake_investment = {
    "id": "fake_investment",
    "priorLogit": -3.0,
    "names": L("fake investment", "फ़र्ज़ी निवेश"),
    "descriptions": L(
        "A stranger adds you to a stock-tips or IPO group, shows fake profits, sends a trading app from a link, then asks you to deposit money you can never withdraw.",
        "कोई अनजान व्यक्ति आपको स्टॉक टिप्स या IPO ग्रुप में जोड़ता है, नकली मुनाफ़ा दिखाता है, लिंक से ट्रेडिंग ऐप भेजता है, फिर पैसे जमा करवाता है जो कभी वापस नहीं निकलते।",
    ),
    "signals": {
        "tag:guaranteed_returns": 2.0,
        "tag:fake_social_proof": 1.2,
        "tag:exclusivity": 1.0,
        "tag:urgency": 0.5,
        "tag:secrecy": 0.6,
        "tag:channel_move": 0.9,
        "tag:install_request": 1.4,
        "tag:payment_request": 1.0,
        "tag:fee_to_withdraw": 2.0,
        "tag:small_win_bait": 0.6,
        "tag:credential_request": 0.3,
        "tag:authority_claim": 0.1,
        "tag:legal_threat": -0.8,
        "event:group_added": 0.7,
        "ctx:private_after_group": 0.8,
        "ctx:claims_broker": 0.6,
        "ctx:cross_app": 0.3,
        "ctx:kyc_mention": -0.8,
        "entity:apk_link": 1.2,
        "entity:upi_handle": 0.5,
        "entity:telegram": 0.4,
        "entity:amount": 0.2,
        "event:app_installed": 0.3,
        "event:upi_link_opened": 0.4,
        "event:payment_app_opened": 0.2,
        "event:payment_sms": 0.2,
        "check:install_source:fail": 1.0,
        "check:valid_handle:fail": 1.5,
        "check:valid_handle:pass": -3.0,
        "check:name_mismatch:fail": 0.8,
        "check:lookalike:fail": 1.2,
        "check:known_bad:fail": 3.0,
        "ctx:transactional": -2.0,
        "ctx:repeat_payment": 1.0,
        "ctx:proceeded": 0.3,
    },
    "stages": [
        {"stage": "HOOK", "evidence": ["tag:guaranteed_returns", "tag:fake_social_proof", "tag:small_win_bait", "tag:exclusivity"], "minEvidence": 1.0},
        {"stage": "GROOMING", "evidence": ["tag:channel_move", "ctx:private_after_group", "ctx:cross_app", "tag:exclusivity", "tag:secrecy", "tag:urgency", "entity:telegram"], "minEvidence": 1.2},
        {"stage": "COMMITMENT", "evidence": ["tag:install_request", "entity:apk_link", "check:install_source:fail", "event:app_installed"], "minEvidence": 1.2},
        {"stage": "EXTRACTION", "evidence": ["tag:payment_request", "check:valid_handle:fail", "event:upi_link_opened", "entity:upi_handle", "event:payment_app_opened"], "minEvidence": 1.0},
        {"stage": "LOCK_IN", "evidence": ["tag:fee_to_withdraw", "ctx:repeat_payment"], "minEvidence": 1.5},
    ],
    "hardRules": [
        # PRD ENG-06 example: investment tactics plus a payment to a handle that fails the @valid check.
        {"id": "investment_invalid_handle", "moment": "payment", "minFamilyLogit": -1.0, "failedChecks": ["valid_handle"]},
        # The case asked for money, the family leads benign, and now a payment starts.
        {"id": "investment_payment_after_request", "moment": "payment", "minFamilyLogit": 0.0, "requireAll": ["tag:payment_request"]},
        {"id": "investment_link_install", "moment": "install", "minFamilyLogit": -0.5, "failedChecks": ["install_source"],
         "requireAny": ["tag:install_request", "entity:apk_link", "tag:guaranteed_returns"]},
    ],
    "checks": ["valid_handle", "install_source", "name_mismatch", "lookalike", "known_bad"],
    "templates": {
        "payment": {
            "headline": L("Stop. This payment matches a known investment scam.", "रुकिए। यह भुगतान एक जाने-पहचाने निवेश घोटाले से मेल खाता है।"),
            "closing": L("Money sent by UPI usually cannot be recalled.", "UPI से भेजा गया पैसा आमतौर पर वापस नहीं आता।"),
        },
        "install": {
            "headline": L("Stop. This app matches a known investment scam.", "रुकिए। यह ऐप एक जाने-पहचाने निवेश घोटाले से मेल खाता है।"),
            "closing": L("Fake trading apps show profits that do not exist.", "नकली ट्रेडिंग ऐप ऐसा मुनाफ़ा दिखाते हैं जो असल में होता ही नहीं।"),
        },
    },
}

digital_arrest = {
    "id": "digital_arrest",
    "priorLogit": -3.5,
    "names": L("digital arrest", "डिजिटल अरेस्ट"),
    "descriptions": L(
        "Callers posing as police or officials say you are under investigation, keep you on a video call, and make you move money to a 'safe account'.",
        "पुलिस या अधिकारी बनकर कॉल करने वाले कहते हैं कि आपकी जाँच चल रही है, आपको वीडियो कॉल पर रोके रखते हैं, और पैसे 'सुरक्षित खाते' में भिजवाते हैं।",
    ),
    "signals": {
        # A claim alone is weak evidence (genuine officials make it too); the threat is the real
        # marker of this script, so it carries the weight the claim gave up.
        "tag:authority_claim": 1.2,
        "tag:legal_threat": 2.5,
        "tag:secrecy": 1.2,
        "tag:urgency": 0.5,
        "tag:remote_access_request": 1.5,
        "tag:credential_request": 1.0,
        "tag:payment_request": 0.9,
        "tag:channel_move": 0.6,
        "tag:guaranteed_returns": -1.0,
        "tag:fake_social_proof": -0.6,
        "event:call_started": 0.2,
        "ctx:video_call": 0.8,
        "ctx:long_call": 1.0,
        "ctx:during_call": 0.8,
        "ctx:recent_call": 0.5,
        "event:screen_share_started": 1.0,
        "event:remote_app_opened": 1.0,
        "entity:upi_handle": 0.3,
        "event:payment_app_opened": 0.2,
        "event:upi_link_opened": 0.3,
        "check:valid_handle:pass": -1.0,
        "check:known_bad:fail": 3.0,
        "ctx:transactional": -2.0,
        "ctx:repeat_payment": 1.0,
        "ctx:proceeded": 0.3,
    },
    "stages": [
        {"stage": "HOOK", "evidence": ["tag:authority_claim", "tag:legal_threat"], "minEvidence": 1.5},
        {"stage": "GROOMING", "evidence": ["tag:secrecy", "ctx:video_call", "ctx:long_call", "tag:urgency", "tag:channel_move"], "minEvidence": 1.0},
        {"stage": "COMMITMENT", "evidence": ["tag:remote_access_request", "event:screen_share_started", "event:remote_app_opened", "tag:credential_request"], "minEvidence": 1.0},
        {"stage": "EXTRACTION", "evidence": ["tag:payment_request", "event:payment_app_opened", "event:upi_link_opened", "ctx:during_call", "entity:upi_handle"], "minEvidence": 1.0},
        {"stage": "LOCK_IN", "evidence": ["ctx:repeat_payment"], "minEvidence": 1.0},
    ],
    "hardRules": [
        {"id": "arrest_payment_during_call", "moment": "payment", "minFamilyLogit": -1.0, "requireAny": ["ctx:during_call", "ctx:recent_call"]},
        {"id": "arrest_screen_share", "moment": "screen_share", "minFamilyLogit": -1.5,
         "requireAny": ["ctx:during_call", "ctx:recent_call", "tag:remote_access_request"]},
    ],
    "checks": ["known_bad"],
    "templates": {
        "payment": {
            "headline": L("Stop. This payment matches the digital arrest scam.", "रुकिए। यह भुगतान डिजिटल अरेस्ट घोटाले से मेल खाता है।"),
            "closing": L("Police never arrest anyone on a video call. No officer asks you to move money to a safe account.",
                         "पुलिस कभी वीडियो कॉल पर किसी को गिरफ़्तार नहीं करती। कोई अधिकारी पैसे 'सुरक्षित खाते' में भेजने को नहीं कहता।"),
        },
        "screen_share": {
            "headline": L("Stop. No bank or police officer ever needs to see your screen.", "रुकिए। किसी बैंक या पुलिस अधिकारी को कभी आपकी स्क्रीन देखने की ज़रूरत नहीं होती।"),
            "closing": L("Whoever sees your screen can see your codes and empty your account.", "जो आपकी स्क्रीन देखता है, वह आपके कोड देखकर खाता खाली कर सकता है।"),
        },
        "call": {
            "headline": L("Police never arrest anyone on a video call.", "पुलिस कभी वीडियो कॉल पर किसी को गिरफ़्तार नहीं करती।"),
            "closing": L("This call matches a known scam pattern. You can hang up. Call someone you trust.",
                         "यह कॉल एक जाने-पहचाने घोटाले से मेल खाती है। आप फ़ोन काट सकते हैं। किसी भरोसेमंद व्यक्ति को कॉल करें।"),
        },
    },
}

# P1 families (ENG-08). Shipped in the pack; their weights need tuning on the test sets before beta.
bank_kyc = {
    "id": "bank_kyc",
    "priorLogit": -3.5,
    "names": L("bank or KYC impersonation", "बैंक या KYC के नाम पर धोखा"),
    "descriptions": L(
        "Someone pretending to be your bank says your account or KYC will be blocked, then asks for codes, a remote-access app or a payment.",
        "कोई आपका बैंक बनकर कहता है कि आपका खाता या KYC बंद हो जाएगा, फिर कोड, रिमोट-ऐक्सेस ऐप या भुगतान माँगता है।",
    ),
    "signals": {
        "tag:authority_claim": 0.8,
        "tag:legal_threat": 0.6,
        "tag:urgency": 0.8,
        "tag:credential_request": 1.8,
        "tag:remote_access_request": 1.8,
        "tag:install_request": 1.0,
        "tag:payment_request": 0.6,
        "tag:guaranteed_returns": -1.0,
        "entity:apk_link": 1.2,
        "ctx:kyc_mention": 1.2,
        "event:app_installed": 0.3,
        "check:install_source:fail": 1.0,
        "check:lookalike:fail": 1.5,
        "event:remote_app_opened": 1.2,
        "event:screen_share_started": 1.0,
        "ctx:during_call": 0.5,
        "event:payment_app_opened": 0.2,
        "check:known_bad:fail": 3.0,
        "ctx:transactional": -1.5,
        "ctx:proceeded": 0.3,
    },
    "stages": [
        {"stage": "HOOK", "evidence": ["tag:authority_claim", "tag:legal_threat", "tag:urgency", "ctx:kyc_mention"], "minEvidence": 1.5},
        {"stage": "GROOMING", "evidence": ["tag:credential_request", "ctx:during_call"], "minEvidence": 1.0},
        {"stage": "COMMITMENT", "evidence": ["tag:remote_access_request", "tag:install_request", "entity:apk_link", "event:remote_app_opened", "event:screen_share_started", "check:install_source:fail"], "minEvidence": 1.2},
        {"stage": "EXTRACTION", "evidence": ["tag:payment_request", "event:payment_app_opened"], "minEvidence": 0.6},
    ],
    "hardRules": [
        {"id": "kyc_remote_access", "moment": "screen_share", "minFamilyLogit": -1.0, "requireAny": ["tag:remote_access_request", "ctx:during_call"]},
        {"id": "kyc_link_app", "moment": "install", "minFamilyLogit": -1.0, "failedChecks": ["install_source"], "requireAny": ["tag:install_request", "entity:apk_link"]},
    ],
    "checks": ["install_source", "lookalike", "known_bad"],
    "templates": {
        "install": {
            "headline": L("Stop. Banks never send apps through chat links.", "रुकिए। बैंक कभी चैट के लिंक से ऐप नहीं भेजते।"),
            "closing": L("An app from a link can read your codes and messages.", "लिंक से आया ऐप आपके कोड और मैसेज पढ़ सकता है।"),
        },
        "screen_share": {
            "headline": L("Stop. Your bank never needs to see your screen.", "रुकिए। आपके बैंक को कभी आपकी स्क्रीन देखने की ज़रूरत नहीं होती।"),
            "closing": L("Whoever sees your screen can see your codes and empty your account.", "जो आपकी स्क्रीन देखता है, वह आपके कोड देखकर खाता खाली कर सकता है।"),
        },
    },
}

task_scam = {
    "id": "task_scam",
    "priorLogit": -3.5,
    "names": L("task or job scam", "टास्क या नौकरी का धोखा"),
    "descriptions": L(
        "You are paid small amounts for simple online tasks, then asked to pay for 'prepaid tasks' and fees that never come back.",
        "आसान ऑनलाइन टास्क के लिए पहले थोड़े पैसे मिलते हैं, फिर 'प्रीपेड टास्क' और फीस के लिए पैसे माँगे जाते हैं जो कभी वापस नहीं आते।",
    ),
    "signals": {
        "tag:small_win_bait": 2.2,
        "tag:guaranteed_returns": 0.8,
        "tag:payment_request": 1.0,
        "tag:fee_to_withdraw": 2.0,
        "tag:channel_move": 0.6,
        "tag:exclusivity": 0.3,
        "tag:urgency": 0.4,
        "entity:telegram": 0.5,
        "entity:upi_handle": 0.4,
        "event:upi_link_opened": 0.4,
        "event:payment_app_opened": 0.2,
        "ctx:repeat_payment": 1.2,
        "check:known_bad:fail": 3.0,
        "ctx:transactional": -2.0,
        "ctx:proceeded": 0.3,
        "tag:legal_threat": -0.8,
    },
    "stages": [
        {"stage": "HOOK", "evidence": ["tag:small_win_bait", "tag:guaranteed_returns"], "minEvidence": 1.2},
        {"stage": "GROOMING", "evidence": ["tag:channel_move", "entity:telegram", "tag:exclusivity"], "minEvidence": 0.8},
        {"stage": "COMMITMENT", "evidence": ["tag:payment_request", "entity:upi_handle"], "minEvidence": 0.9},
        {"stage": "EXTRACTION", "evidence": ["ctx:repeat_payment", "tag:fee_to_withdraw"], "minEvidence": 1.0},
        {"stage": "LOCK_IN", "evidence": ["tag:fee_to_withdraw"], "minEvidence": 1.5},
    ],
    "hardRules": [
        {"id": "task_payment_after_request", "moment": "payment", "minFamilyLogit": 0.0, "requireAll": ["tag:payment_request"]},
    ],
    "checks": ["known_bad"],
    "templates": {
        "payment": {
            "headline": L("Stop. This payment matches a known task scam.", "रुकिए। यह भुगतान एक जाने-पहचाने टास्क घोटाले से मेल खाता है।"),
            "closing": L("Real jobs never ask you to pay to earn.", "असली नौकरी में कमाने के लिए कभी पैसे नहीं देने पड़ते।"),
        },
    },
}

courier = {
    "id": "courier",
    "priorLogit": -3.8,
    "names": L("courier or parcel scam", "कूरियर या पार्सल का धोखा"),
    "descriptions": L(
        "A caller says a parcel in your name holds illegal items or needs a customs fee, then hands you to fake police or asks for payment.",
        "कॉल करने वाला कहता है कि आपके नाम के पार्सल में गैरकानूनी सामान है या कस्टम फीस बाकी है, फिर नकली पुलिस से बात करवाता है या पैसे माँगता है।",
    ),
    "signals": {
        "tag:authority_claim": 0.8,
        "tag:legal_threat": 1.5,
        "tag:payment_request": 0.8,
        "tag:credential_request": 1.0,
        "tag:channel_move": 0.6,
        "tag:urgency": 0.5,
        "tag:secrecy": 0.8,
        "tag:remote_access_request": 1.0,
        "tag:guaranteed_returns": -1.0,
        "ctx:video_call": 0.5,
        "ctx:during_call": 0.5,
        "event:payment_app_opened": 0.2,
        "check:known_bad:fail": 3.0,
        "ctx:transactional": -1.0,
        "ctx:proceeded": 0.3,
    },
    "stages": [
        {"stage": "HOOK", "evidence": ["tag:authority_claim", "tag:legal_threat"], "minEvidence": 1.5},
        {"stage": "GROOMING", "evidence": ["tag:channel_move", "tag:secrecy", "ctx:video_call"], "minEvidence": 0.8},
        {"stage": "COMMITMENT", "evidence": ["tag:credential_request", "tag:remote_access_request"], "minEvidence": 1.0},
        {"stage": "EXTRACTION", "evidence": ["tag:payment_request", "event:payment_app_opened", "ctx:during_call"], "minEvidence": 1.0},
    ],
    "hardRules": [],
    "checks": ["known_bad"],
    "templates": {},
}

# ---------------------------------------------------------------------------------------------
# Keyword rules (TAC-04 fallback; alwaysOn rules also run beside the model, PRD 10.8)
# English, Hindi (Devanagari) and Hinglish. Patterns are case-insensitive Java regexes.
# Devanagari patterns avoid \b, which does not work reliably around combining marks.
# ---------------------------------------------------------------------------------------------

keyword_rules = [
    {"tactic": "guaranteed_returns", "confidence": 0.75, "patterns": [
        r"\bguarantee[d]?\s+(profit|returns?|income|earning)",
        r"\b(assured|fixed|sure[- ]?shot)\s+(daily\s+|monthly\s+|weekly\s+)?(profit|returns?|income)",
        r"\b(daily|weekly|monthly)\s+\d{1,3}\s?%\s*(profit|returns?)",
        r"\b\d{1,3}\s?%\s*(profit|returns?)\s*(daily|per day|a day|weekly|monthly|guaranteed|in \d+ days)",
        r"\b(no|zero)\s+(risk|loss)\b", r"\brisk[- ]free\b", r"\bdouble\s+your\s+(money|investment)",
        r"\b\d+x\s+returns?\b",
        r"\bpakk[ae]\s+(profit|munafa|return)", r"\bguarantee\s+(hai|ke saath|deta|dete)", r"\bpaisa\s+double\b",
        r"\bloss\s+(nahi|nahin)\s+hoga", r"\b100\s?%\s+(safe|profit|pakka)",
        r"गारंटी", r"पक्का\s*(मुनाफ़ा|मुनाफा|प्रॉफिट)", r"पैसा\s*डबल", r"निश्चित\s*(मुनाफ़ा|मुनाफा|रिटर्न)", r"कोई\s*(जोखिम|रिस्क|नुकसान)\s*नहीं",
    ]},
    {"tactic": "fake_social_proof", "confidence": 0.65, "patterns": [
        r"\b(i|we|members?|he|she|they|our team)\s+(made|earned|got|booked|received)\s+(₹|rs\.?|inr)?\s?[\d,.]+\s?(lakh|lakhs|k|cr|crore)?",
        r"\bprofit\s+screenshot", r"\b(today'?s|yesterday'?s)\s+profit", r"\bbooked\s+profit",
        r"\bmembers\s+(are\s+)?(earning|making)", r"\b(withdrawal|payout)\s+(successful|received|done)",
        r"\bthank\s*(you|u)\s+sir\b.{0,40}(profit|earned|returns?)",
        r"\b(kamaye|kamaya)\b", r"कमाए", r"मुनाफ़ा\s*कमाया", r"मुनाफा\s*कमाया",
    ]},
    {"tactic": "exclusivity", "confidence": 0.65, "patterns": [
        r"\bvip\b", r"\bpre[- ]?ipo\b", r"\ballotment\b", r"\binstitutional\s+(account|trading|quota)",
        r"\b(only|limited)\s+(for\s+)?(\d+\s+)?(members|seats|slots|people)", r"\bexclusive(ly)?\b",
        r"\binsider\s+(tip|tips|info|information)", r"\b(special|premium)\s+(group|membership|access|account)",
        r"\bselected\s+(members|few|investors)", r"\botc\s+(trading|account)", r"\bblock\s+deal",
        r"\bsirf\s+aap(ke|ko)\s+liye", r"चुनिंदा", r"विशेष\s*(सदस्य|ग्रुप|अवसर)", r"सिर्फ़?\s*आपके\s*लिए",
    ]},
    {"tactic": "urgency", "confidence": 0.6, "patterns": [
        r"\b(only|just)\s+today\b", r"\btoday\s+only\b", r"\blast\s+(chance|day|\d+\s+(slots|seats|hours))",
        r"\bimmediately\b", r"\burgent(ly)?\b", r"\bwithin\s+\d+\s+(minutes|mins|hours|hrs)",
        r"\bhurry\b", r"\bact\s+now\b", r"\bbefore\s+(market\s+(opens|closes)|tonight|today)",
        r"\baaj\s+hi\b", r"\babhi\s+(karo|kijiye|karna|karein|turant)", r"\bjaldi\b", r"\bturant\b",
        r"आज\s*ही", r"तुरंत", r"जल्दी\s*(करें|कीजिए|करो)",
    ]},
    {"tactic": "authority_claim", "confidence": 0.7, "patterns": [
        r"\b(cbi|ncb|enforcement directorate|cyber\s+(cell|crime|police)|crime\s+branch|narcotics|customs\s+(department|officer)|income\s+tax\s+(department|officer)|trai|supreme\s+court|high\s+court)\b",
        r"\b(mumbai|delhi|bangalore|bengaluru|hyderabad|kolkata|chennai)\s+police\b",
        r"\bpolice\s+(station|officer|inspector|headquarters)\b",
        r"\b(i\s+am|this\s+is)\s+(calling\s+)?(from\s+)?(the\s+)?(police|cbi|rbi|customs|fedex|dhl|trai|cyber\s+cell)",
        r"\b(rbi|sebi)\s+(officer|official|department)\b",
        r"\b(sbi|hdfc|icici|axis|kotak|pnb)\s+(bank\s+)?(customer\s+care|officer|manager|kyc\s+(team|department))",
        r"\b(police|cbi)\s+(se|wale|wala)\b",
        r"पुलिस", r"सीबीआई", r"साइबर\s*(सेल|क्राइम)", r"क्राइम\s*ब्रांच", r"कस्टम्स?\s*(विभाग|अधिकारी)", r"नारकोटिक्स",
    ]},
    {"tactic": "legal_threat", "confidence": 0.75, "alwaysOn": True, "patterns": [
        r"\b(arrest|arrested|arrest\s+warrant|warrant)\b", r"\bnon[- ]bailable\b", r"\bmoney\s+laundering\b",
        r"\b(drugs?|illegal\s+items?|narcotics)\s+(found|in\s+(the|your)\s+parcel)",
        r"\b(fir|case)\s+(has\s+been\s+)?(registered|filed|lodged)\b", r"\blegal\s+action\b",
        r"\bcourt\s+(case|notice|summons)\b",
        r"\baccount\s+(will\s+be\s+)?(frozen|freezed|blocked|suspended|seized)",
        r"\bgiraft?aa?r\b", r"\bjail\s+(ho|jaoge|jayenge|bhej)",
        r"गिरफ़्तार", r"गिरफ्तार", r"वारंट", r"मनी\s*लॉन्ड्रिंग", r"केस\s*दर्ज", r"खाता\s*(बंद|फ्रीज़|फ्रीज|सीज़)",
    ]},
    {"tactic": "secrecy", "confidence": 0.7, "patterns": [
        r"\b(don'?t|do\s+not|never)\s+(tell|discuss|inform|disclose)\b",
        r"\b(don'?t|do\s+not)\s+share\s+(this|it)\s+with\s+(anyone|family|others|your)",
        r"\bkeep\s+(this|it)\s+(confidential|secret|between\s+us)", r"\bstrictly\s+confidential\b",
        r"\bnational\s+secret", r"\bsecret\s+(investigation|operation)",
        r"\bkisi\s+ko\s+(mat|na|nahi)\s+(batana|bataye|bataiye|bataana|batao)", r"\bkisi\s+ko\s+kuch\s+mat",
        r"किसी\s*को\s*(मत|ना|न)\s*(बताना|बताएं|बताइए|बताएँ|बताओ)", r"गुप्त\s*(रखें|रखिए|जाँच)",
    ]},
    {"tactic": "channel_move", "confidence": 0.65, "patterns": [
        r"\b(join|message|contact|ping|dm|text|whatsapp)\s+(me|us|my\s+assistant|our\s+(team|assistant|analyst|mentor))\s+(on|at|via|in)\s+(telegram|whatsapp|signal|private|skype)",
        r"\b(whatsapp|telegram|message|contact|call|ping)\s+me\s+(at|on)\s+\+?\d",
        r"\bt\.me/", r"\b(personal|private)\s+(chat|message|dm|number|whatsapp)",
        r"\b(come|move|shift)\s+to\s+(private|telegram|whatsapp|video\s+call)",
        r"\bjoin\s+(the\s+|our\s+)?video\s+call", r"\bstay\s+on\s+(the\s+)?video\s+call",
        r"\bvideo\s+call\s+(karo|pe\s+aao|par\s+aao|join\s+karo|pe\s+raho)",
        r"\badd\s+(my|our)\s+(assistant|analyst|mentor)", r"\bprivate\s+(mein|me)\s+(message|baat)",
        r"टेलीग्राम", r"प्राइवेट\s*(चैट|मैसेज)", r"वीडियो\s*कॉल\s*(पर|पे)",
    ]},
    {"tactic": "install_request", "confidence": 0.75, "alwaysOn": True, "patterns": [
        r"\b(download|install)\s+(our|the|this)?\s?(trading\s+)?(app|application|apk|software|platform)\b",
        r"\.apk\b", r"\bapk\b", r"\b(app|apk)\s+(link|download)\b",
        r"\b(download|install)\s+karo\b", r"\bapp\s+download\s+kar", r"\bregister\s+on\s+(our|the|this)\s+(app|platform)",
        r"ऐप\s*(डाउनलोड|इंस्टॉल)", r"डाउनलोड\s*करें", r"इंस्टॉल\s*करें",
    ]},
    # Remote-access tool names are unambiguous, so they run beside the model (PRD 10.8).
    {"tactic": "remote_access_request", "confidence": 0.8, "alwaysOn": True, "patterns": [
        r"\b(anydesk|any\s+desk|teamviewer|team\s+viewer|quick\s?support|airdroid|rustdesk|zoho\s+assist)\b",
        r"\bremote\s+(access|support|desktop|control)\b",
    ]},
    # "Share your screen" is ordinary in meetings and classes; only the model can tell the context,
    # so this pattern is a fallback for when the model is unavailable, never an override.
    {"tactic": "remote_access_request", "confidence": 0.7, "patterns": [
        r"\b(share|sharing)\s+(your\s+|the\s+)?screen\b", r"\bscreen\s?share\b", r"\bscreen\s+share\s+kar",
        r"स्क्रीन\s*शेयर",
    ]},
    {"tactic": "credential_request", "confidence": 0.8, "alwaysOn": True, "patterns": [
        r"\b(tell|share|send|give|forward|read\s+out)(\s+me)?\s+(the\s+|your\s+)?(otp|code|pin|cvv|password|card\s+(number|details)|bank\s+details|account\s+(number|details)|upi\s+pin|mpin)\b",
        r"\b(enter|type)\s+(your\s+)?(upi\s+)?pin\b", r"\botp\s+(batao|bataiye|bhejo|share\s+karo|bolo)\b",
        r"\botp\s+kya\s+aaya", r"\b(aadhaar|aadhar|pan)\s+(number|card|details)\s+(send|bhejo|share)",
        r"ओटीपी\s*(बताएं|बताइए|बताओ|भेजें)", r"पिन\s*(बताएं|डालें|बताइए)",
    ]},
    {"tactic": "payment_request", "confidence": 0.7, "patterns": [
        r"\b(send|transfer|pay|deposit|invest|remit)\s+(rs\.?|₹|inr)\s?[\d,]+",
        r"\b(send|transfer|pay|deposit|invest)\s+[\d,]{3,}\s*(rs|rupees|₹)?",
        r"\b(send|transfer|pay|deposit)\s+(the\s+)?(money|amount|funds|fee|payment)\b",
        r"\b(pay|send|deposit|transfer)\b.{0,25}\b(to|on|at|in)\s+(this|the|our|my)\s+(upi|account|id|number|qr|wallet)",
        r"\bscan\s+(the|this)\s+qr\b", r"\bminimum\s+(deposit|investment)\b",
        r"\b(safe|secure|verification|rbi)\s+account\b",
        r"\b(paise|paisa|payment|amount)\s+(bhejo|bhejiye|transfer\s+karo|daalo|daal\s+do|jama\s+karo)",
        r"\bpay\s+karo\b",
        r"(पैसे|राशि|पेमेंट|भुगतान)\s*(भेजें|भेजिए|भेजो|ट्रांसफर\s*करें|जमा\s*करें)", r"सुरक्षित\s*खात",
    ]},
    {"tactic": "fee_to_withdraw", "confidence": 0.8, "alwaysOn": True, "patterns": [
        r"\b(pay|deposit|clear)\b.{0,30}\b(tax|fee|charges?|commission|gst|security\s+deposit|margin|penalty)\b.{0,40}\b(withdraw|release|unlock|get\s+your|receive|refund)",
        r"\b(withdraw(al)?|release|unlock)\b.{0,40}\b(tax|fee|charges?|commission|gst|deposit)\b",
        r"\b\d{1,2}\s?%\s*(tax|fee|gst|commission)\b",
        r"\b(tax|fee)\s+(bharo|jama\s+karo|dena\s+hoga|pay\s+karo)",
        r"निकासी.{0,30}(शुल्क|टैक्स|फीस)", r"(टैक्स|शुल्क|फीस)\s*(भरें|जमा\s*करें|देना\s*होगा)",
    ]},
    {"tactic": "small_win_bait", "confidence": 0.65, "patterns": [
        r"(₹|rs\.?|inr)\s?\d{2,4}\s+(sent|credited|received|paid)\s+(for|to\s+you)",
        r"\b(first|your)\s+(task|payout|commission)\s+(is\s+)?(complete|completed|paid|credited)",
        r"\bper\s+task\b", r"\b(like|subscribe|rate|review)\b.{0,30}\b(earn|get)\b.{0,10}(₹|rs)",
        r"\b(earn|kamao|kamaye)\s+(₹|rs\.?)\s?\d{2,5}\s+(per|daily|every|a\s+day|roz)",
        r"\bbonus\s+(credited|added)\b", r"\bprepaid\s+task",
        r"प्रति\s*टास्क", r"टास्क\s*(पूरा|करें)",
    ]},
]

# ---------------------------------------------------------------------------------------------
# App lists (data, not code: PRD 14.7)
# ---------------------------------------------------------------------------------------------

apps = {
    "messaging": [
        {"packageName": "com.whatsapp", "label": "WhatsApp"},
        {"packageName": "com.whatsapp.w4b", "label": "WhatsApp Business"},
        {"packageName": "org.telegram.messenger", "label": "Telegram"},
        {"packageName": "org.telegram.messenger.web", "label": "Telegram"},
        {"packageName": "org.thunderdog.challegram", "label": "Telegram X"},
        {"packageName": "com.google.android.apps.messaging", "label": "Messages"},
        {"packageName": "com.samsung.android.messaging", "label": "Messages"},
        {"packageName": "com.android.mms", "label": "Messages"},
        {"packageName": "com.truecaller", "label": "Truecaller"},
    ],
    "payment": [
        {"packageName": "com.phonepe.app", "label": "PhonePe"},
        {"packageName": "net.one97.paytm", "label": "Paytm"},
        {"packageName": "com.google.android.apps.nbu.paisa.user", "label": "Google Pay"},
        {"packageName": "in.org.npci.upiapp", "label": "BHIM"},
        {"packageName": "com.dreamplug.androidapp", "label": "CRED"},
        {"packageName": "com.mobikwik_new", "label": "MobiKwik"},
        {"packageName": "com.sbi.lotusintouch", "label": "YONO SBI"},
        {"packageName": "com.csam.icici.bank.imobile", "label": "iMobile"},
        {"packageName": "com.snapwork.hdfc", "label": "HDFC Bank"},
        {"packageName": "com.axis.mobile", "label": "Axis Mobile"},
        {"packageName": "com.msf.kbank.mobile", "label": "Kotak"},
        {"packageName": "com.naviapp", "label": "Navi"},
    ],
    "remoteAccess": [
        {"packageName": "com.anydesk.anydeskandroid", "label": "AnyDesk"},
        {"packageName": "com.teamviewer.quicksupport.market", "label": "TeamViewer QuickSupport"},
        {"packageName": "com.teamviewer.teamviewer.market.mobile", "label": "TeamViewer"},
        {"packageName": "com.sand.airdroid", "label": "AirDroid"},
        {"packageName": "com.sand.airdroidbiz", "label": "AirDroid Business"},
        {"packageName": "com.carriez.flutter_hbb", "label": "RustDesk"},
        {"packageName": "com.zoho.assist.agent", "label": "Zoho Assist"},
    ],
    "stores": [
        "com.android.vending", "com.sec.android.app.samsungapps", "com.vivo.appstore", "com.bbk.appstore",
        "com.heytap.market", "com.oppo.market", "com.xiaomi.market", "com.xiaomi.mipicks",
        "com.huawei.appmarket", "com.amazon.venezia", "com.oneplus.market",
    ],
    "linkInstallers": [
        "com.google.android.packageinstaller", "com.android.packageinstaller", "com.miui.packageinstaller",
        "com.android.chrome", "com.whatsapp", "org.telegram.messenger", "com.android.documentsui",
        "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.vivo.browser",
        "com.android.browser", "org.mozilla.firefox", "com.opera.browser", "com.UCMobile.intl",
    ],
}

brands = [
    {"name": "Zerodha", "aliases": ["kite zerodha"], "packages": ["com.zerodha.kite3", "com.zerodha.coin"], "domains": ["zerodha.com"]},
    {"name": "Groww", "aliases": [], "packages": ["com.nextbillion.groww"], "domains": ["groww.in"]},
    {"name": "Upstox", "aliases": [], "packages": ["in.upstox.pro"], "domains": ["upstox.com"]},
    {"name": "Angel One", "aliases": ["angelone", "angel broking"], "packages": ["com.msf.angelmobile"], "domains": ["angelone.in"]},
    {"name": "SBI", "aliases": ["yono", "state bank"], "packages": ["com.sbi.lotusintouch"], "domains": ["sbi.co.in", "onlinesbi.sbi"]},
    {"name": "HDFC Bank", "aliases": ["hdfc"], "packages": ["com.snapwork.hdfc"], "domains": ["hdfcbank.com"]},
    {"name": "ICICI Bank", "aliases": ["icici", "imobile"], "packages": ["com.csam.icici.bank.imobile"], "domains": ["icicibank.com"]},
    {"name": "Axis Bank", "aliases": ["axisbank"], "packages": ["com.axis.mobile"], "domains": ["axisbank.com"]},
    {"name": "Paytm", "aliases": [], "packages": ["net.one97.paytm"], "domains": ["paytm.com"]},
    {"name": "PhonePe", "aliases": [], "packages": ["com.phonepe.app"], "domains": ["phonepe.com"]},
]

# ---------------------------------------------------------------------------------------------
# Reason sentences (EXP-01, EXP-03): one per signal, each tied to something observed.
# Placeholders: {when} {group} {app} {sender} {handle}
# ---------------------------------------------------------------------------------------------

reasons = {
    "event:group_added": L("A stranger added you to a group {when}.", "एक अनजान व्यक्ति ने आपको {when} एक ग्रुप में जोड़ा।"),
    "tag:guaranteed_returns": L("They promised guaranteed profit.", "उन्होंने पक्के मुनाफ़े का वादा किया।"),
    "tag:fake_social_proof": L("The chat showed big profits made by other people.", "चैट में दूसरों के बड़े मुनाफ़े दिखाए गए।"),
    "tag:exclusivity": L("They offered special access, like a VIP or pre-IPO slot.", "उन्होंने VIP या प्री-IPO जैसी ख़ास पेशकश की।"),
    "tag:urgency": L("They pressured you to act today.", "उन्होंने आज ही करने का दबाव डाला।"),
    "tag:secrecy": L("They told you not to tell anyone.", "उन्होंने कहा कि किसी को न बताएँ।"),
    "tag:channel_move": L("They moved you to a private chat.", "उन्होंने आपको प्राइवेट चैट पर बुलाया।"),
    "ctx:private_after_group": L("A group member then messaged you privately.", "फिर ग्रुप के एक सदस्य ने आपको अलग से मैसेज किया।"),
    "tag:install_request": L("They asked you to install an app.", "उन्होंने आपसे एक ऐप इंस्टॉल करने को कहा।"),
    "entity:apk_link": L("They sent an app file link.", "उन्होंने ऐप फ़ाइल का लिंक भेजा।"),
    "check:install_source:fail": L("They sent you an app from a link, not from the Play Store.", "उन्होंने Play Store की जगह लिंक से ऐप भेजा।"),
    "tag:payment_request": L("They asked you to send money.", "उन्होंने आपसे पैसे भेजने को कहा।"),
    "tag:fee_to_withdraw": L("They asked for a fee to release your money.", "उन्होंने पैसा निकालने के लिए फीस माँगी।"),
    "tag:small_win_bait": L("They paid you a small amount first to win your trust.", "भरोसा जीतने के लिए उन्होंने पहले थोड़े पैसे दिए।"),
    "tag:authority_claim": L("The caller claimed to be police or an official.", "कॉल करने वाले ने ख़ुद को पुलिस या अधिकारी बताया।"),
    "tag:legal_threat": L("They threatened you with arrest or a case.", "उन्होंने गिरफ़्तारी या केस की धमकी दी।"),
    "tag:remote_access_request": L("They asked to see your screen.", "उन्होंने आपकी स्क्रीन देखने को कहा।"),
    "tag:credential_request": L("They asked for your bank details or a code.", "उन्होंने आपके बैंक की जानकारी या कोड माँगा।"),
    "ctx:video_call": L("They kept you on a video call.", "उन्होंने आपको वीडियो कॉल पर रखा।"),
    "ctx:long_call": L("The call went on for a long time.", "कॉल बहुत देर तक चली।"),
    "ctx:during_call": L("You are on a call with them right now.", "आप अभी उनसे कॉल पर हैं।"),
    "ctx:recent_call": L("They called you a few minutes ago.", "उन्होंने कुछ मिनट पहले आपको कॉल किया था।"),
    "ctx:claims_broker": L("They claimed to be a registered advisor.", "उन्होंने ख़ुद को रजिस्टर्ड सलाहकार बताया।"),
    "check:name_mismatch:fail": L("The name on this payment address is not the name they gave you.", "इस पेमेंट पते पर लिखा नाम वह नहीं है जो उन्होंने बताया था।"),
    "check:lookalike:fail": L("The app's name copies a real company.", "ऐप का नाम किसी असली कंपनी की नकल है।"),
    "ctx:repeat_payment": L("They keep asking for more money.", "वे बार-बार और पैसे माँग रहे हैं।"),
}

# ---------------------------------------------------------------------------------------------
# Fixed strings (warnings, notices, checks, packs, ally alerts). Every key in every language.
# ---------------------------------------------------------------------------------------------

strings = {
    "family.unknown": L("scam", "धोखाधड़ी"),
    "notice.title": L("Tripwire noticed a pattern", "Tripwire ने एक पैटर्न देखा"),
    "notice.body": L("This chat matches a known {family} pattern. Tap to see why.", "यह चैट एक जाने-पहचाने {family} पैटर्न से मेल खाती है। कारण देखने के लिए टैप करें।"),

    "headline.generic.payment": L("Stop. This payment matches a known {family} scam pattern.", "रुकिए। यह भुगतान एक जाने-पहचाने {family} घोटाले से मेल खाता है।"),
    "headline.generic.install": L("Stop. This app matches a known {family} scam pattern.", "रुकिए। यह ऐप एक जाने-पहचाने {family} घोटाले से मेल खाता है।"),
    "headline.generic.screen_share": L("Stop. No bank or police officer ever needs to see your screen.", "रुकिए। किसी बैंक या पुलिस अधिकारी को कभी आपकी स्क्रीन देखने की ज़रूरत नहीं होती।"),
    "headline.generic.call": L("This call matches a known {family} scam pattern.", "यह कॉल एक जाने-पहचाने {family} घोटाले से मेल खाती है।"),
    "closing.generic.payment": L("Money sent by UPI usually cannot be recalled.", "UPI से भेजा गया पैसा आमतौर पर वापस नहीं आता।"),
    "closing.generic.install": L("An app from a link can read your codes and messages.", "लिंक से आया ऐप आपके कोड और मैसेज पढ़ सकता है।"),
    "closing.generic.screen_share": L("Whoever sees your screen can see your codes and empty your account.", "जो आपकी स्क्रीन देखता है, वह आपके कोड देखकर खाता खाली कर सकता है।"),
    "closing.generic.call": L("You can hang up. Call someone you trust.", "आप फ़ोन काट सकते हैं। किसी भरोसेमंद व्यक्ति को कॉल करें।"),

    "btn.dont_pay": L("Don't pay", "भुगतान न करें"),
    "btn.dont_install": L("Don't install", "इंस्टॉल न करें"),
    "btn.stop_sharing": L("Stop sharing", "शेयर करना बंद करें"),
    "btn.hang_up": L("Hang up and call 1930", "फ़ोन काटें और 1930 पर कॉल करें"),
    "btn.call_ally": L("Call {ally} first", "पहले {ally} को कॉल करें"),
    "btn.verify_sebi": L("Verify on SEBI Check", "SEBI Check पर जाँचें"),
    "btn.pay_anyway": L("Pay anyway (hold 3 seconds)", "फिर भी भुगतान करें (3 सेकंड दबाए रखें)"),
    "btn.install_anyway": L("Install anyway (hold 3 seconds)", "फिर भी इंस्टॉल करें (3 सेकंड दबाए रखें)"),
    "btn.continue_anyway": L("Continue anyway (hold 3 seconds)", "फिर भी जारी रखें (3 सेकंड दबाए रखें)"),
    "btn.dismiss": L("Close this notice", "यह सूचना बंद करें"),

    "check.valid_handle.pass": L("This payment address is a SEBI @valid address.", "यह पेमेंट पता SEBI का @valid पता है।"),
    "check.valid_handle.fail": L("This payment address is not a SEBI @valid address. Registered brokers must use one.", "यह पेमेंट पता SEBI का @valid पता नहीं है। रजिस्टर्ड ब्रोकर को @valid पता ही इस्तेमाल करना होता है।"),
    "check.install_source.pass": L("This app came from an app store.", "यह ऐप ऐप स्टोर से आया है।"),
    "check.install_source.fail": L("This app came from a link, not from an app store.", "यह ऐप ऐप स्टोर से नहीं, एक लिंक से आया है।"),
    "check.install_source.unknown": L("Tripwire could not tell where this app came from.", "Tripwire यह नहीं जान पाया कि यह ऐप कहाँ से आया।"),
    "check.known_bad.fail": L("This payment address or link has been reported in other scams.", "यह पेमेंट पता या लिंक दूसरे घोटालों में रिपोर्ट हो चुका है।"),
    "check.name_mismatch.pass": L("The name on this payment address matches who they said they are.", "इस पेमेंट पते का नाम उनके बताए नाम से मिलता है।"),
    "check.name_mismatch.fail": L("This payment goes to {payee}, not to {claimed}.", "यह भुगतान {claimed} को नहीं, {payee} को जा रहा है।"),
    "check.lookalike.pass": L("This is the official {brand} app.", "यह {brand} का असली ऐप है।"),
    "check.lookalike.fail": L("{subject} copies the name of {brand}, but it is not the official app.", "{subject} {brand} के नाम की नकल है, यह असली ऐप नहीं है।"),

    "time.today": L("today", "आज"),
    "time.yesterday": L("yesterday", "कल"),
    "time.days_ago": L("{n} days ago", "{n} दिन पहले"),

    "timeline.group_added": L("Added to {group}", "{group} में जोड़ा गया"),
    "timeline.app_installed": L("Installed {app} from a link", "लिंक से {app} इंस्टॉल किया"),
    "timeline.payment": L("Payment to {handle}", "{handle} को भुगतान"),
    "timeline.call": L("Call from a stranger", "अनजान नंबर से कॉल"),
    "timeline.video_call": L("Video call from a stranger", "अनजान नंबर से वीडियो कॉल"),
    "timeline.screen_share": L("Screen sharing started", "स्क्रीन शेयरिंग शुरू हुई"),
    "timeline.paid": L("Money was sent", "पैसे भेजे गए"),

    "stage.contact": L("first contact", "पहला संपर्क"),
    "stage.hook": L("bait or threat", "लालच या डर"),
    "stage.grooming": L("building trust", "भरोसा जमाना"),
    "stage.commitment": L("getting you to act", "आपसे कुछ करवाना"),
    "stage.extraction": L("asking for money", "पैसे की माँग"),
    "stage.lock_in": L("demanding more", "और पैसे की माँग"),

    "tactic.guaranteed_returns": L("Promised guaranteed profit", "पक्के मुनाफ़े का वादा"),
    "tactic.fake_social_proof": L("Showed other people's profits", "दूसरों का मुनाफ़ा दिखाया"),
    "tactic.exclusivity": L("Offered special access", "ख़ास पेशकश"),
    "tactic.urgency": L("Pressure to act now", "जल्दी करने का दबाव"),
    "tactic.authority_claim": L("Claimed to be police or an official", "ख़ुद को पुलिस या अधिकारी बताया"),
    "tactic.legal_threat": L("Threatened arrest or a case", "गिरफ़्तारी या केस की धमकी"),
    "tactic.secrecy": L("Told you to keep it secret", "बात छिपाने को कहा"),
    "tactic.channel_move": L("Moved you to a private chat", "प्राइवेट चैट पर बुलाया"),
    "tactic.install_request": L("Asked you to install an app", "ऐप इंस्टॉल करने को कहा"),
    "tactic.remote_access_request": L("Asked to see your screen", "स्क्रीन देखने को कहा"),
    "tactic.credential_request": L("Asked for codes or bank details", "कोड या बैंक जानकारी माँगी"),
    "tactic.payment_request": L("Asked for money", "पैसे माँगे"),
    "tactic.fee_to_withdraw": L("Asked for a fee to release money", "पैसा निकालने के लिए फीस माँगी"),
    "tactic.small_win_bait": L("Paid a small amount first", "पहले थोड़े पैसे दिए"),

    "pack.text_deleted": L("(message text deleted after the retention period)", "(तय समय के बाद मैसेज हटा दिया गया)"),
    "pack.payment_app_opened": L("Opened {app} to pay", "भुगतान के लिए {app} खोला"),
    "pack.user_reply": L("You replied", "आपका जवाब"),
    "pack.unknown": L("unknown", "पता नहीं"),
    "pack.call_script": L(
        "My name is {name}. I want to report a {type} fraud. I paid {amount} at {time}. The transaction reference is {utr}. The money went to {handle}. The scammer's numbers are {numbers}. I have the full timeline and screenshots ready.",
        "मेरा नाम {name} है। मैं {type} धोखाधड़ी की शिकायत करना चाहता/चाहती हूँ। मैंने {time} पर {amount} भेजे। ट्रांज़ैक्शन रेफ़रेंस {utr} है। पैसा {handle} को गया। धोखेबाज़ के नंबर {numbers} हैं। मेरे पास पूरी टाइमलाइन और स्क्रीनशॉट हैं।",
    ),
    "pack.note": L("This pack was generated on the user's phone by Tripwire. It is an aid to filing a complaint, not certified evidence.",
                   "यह पैक Tripwire ने उपयोगकर्ता के फ़ोन पर बनाया है। यह शिकायत दर्ज करने में मदद के लिए है, प्रमाणित सबूत नहीं।"),

    "ally.warning_shown": L("Tripwire: {name}'s phone showed a warning that matches a {family} scam (stage: {stage}). Please call them now.",
                            "Tripwire: {name} के फ़ोन पर {family} घोटाले से मेल खाती चेतावनी दिखी (चरण: {stage})। कृपया अभी उन्हें कॉल करें।"),
    "ally.proceeded": L("Tripwire: {name} went ahead after a {family} scam warning (stage: {stage}). Please call them now.",
                        "Tripwire: {name} {family} घोटाले की चेतावनी के बाद भी आगे बढ़ गए (चरण: {stage})। कृपया अभी उन्हें कॉल करें।"),
    "ally.already_paid": L("Tripwire: {name} says they already paid in a possible {family} scam. Help them call 1930 within the hour.",
                           "Tripwire: {name} कहते हैं कि उन्होंने संभावित {family} घोटाले में भुगतान कर दिया है। एक घंटे के अंदर 1930 पर कॉल करने में मदद करें।"),
    "ally.protection_off": L("Tripwire: protection was turned off on {name}'s phone during an active {family} case. Please call them.",
                             "Tripwire: {name} के फ़ोन पर चल रहे {family} मामले के दौरान सुरक्षा बंद कर दी गई। कृपया उन्हें कॉल करें।"),
}

pack = {
    "version": VERSION,
    "createdAt": "2026-10-05",
    "thresholds": {"watch": 40, "warn": 60, "timeLinkMinutes": 30, "riskHalfLifeDays": 14.0, "tuneMin": -10, "tuneMax": 15},
    "families": [fake_investment, digital_arrest, bank_kyc, task_scam, courier],
    "apps": apps,
    "keywordRules": keyword_rules,
    "knownBad": {
        # Test-only entries on a provider that does not exist. Real lists ship in signed updates (CHK-05).
        "handles": ["reported.example@tripwiretest"],
        "numbers": [],
        "domains": ["reported-scam.example"],
    },
    "brands": brands,
    # SEBI format: <name>.<category>@valid<bank>, e.g. abc.brk@validhdfc. Open question 23.2: confirm the exact rule.
    "validHandlePattern": r"^[a-z0-9][a-z0-9._-]*\.(brk|mf)@valid[a-z]+$",
    # Open question 23.2: whether SEBI Check offers a deep link with the handle filled in.
    "sebiCheckUrl": "https://www.sebi.gov.in/",
    "reasons": reasons,
    "reasonGroups": [
        ["check:install_source:fail", "tag:install_request", "entity:apk_link"],
        ["ctx:private_after_group", "tag:channel_move"],
        ["ctx:during_call", "ctx:recent_call", "ctx:video_call"],
        ["tag:fee_to_withdraw", "ctx:repeat_payment", "tag:payment_request"],
    ],
    "storyOpeners": ["event:group_added"],
    "strings": strings,
}


def check_languages():
    missing = []
    for k, v in list(reasons.items()) + list(strings.items()):
        for lang in LANGS:
            if not v.get(lang):
                missing.append(f"{k}:{lang}")
    for f in pack["families"]:
        for lang in LANGS:
            if not f["names"].get(lang) or not f["descriptions"].get(lang):
                missing.append(f"{f['id']}.names:{lang}")
        for m, t in f["templates"].items():
            for part in ("headline", "closing"):
                for lang in LANGS:
                    if not t.get(part, {}).get(lang):
                        missing.append(f"{f['id']}.{m}.{part}:{lang}")
    if missing:
        sys.exit("missing strings: " + ", ".join(missing))


if __name__ == "__main__":
    check_languages()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(pack, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    print(f"wrote {OUT} (version {VERSION}, {len(pack['families'])} families, {len(keyword_rules)} keyword rules)")
