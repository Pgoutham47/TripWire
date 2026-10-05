#!/usr/bin/env python3
"""Builds the replay scenarios (PRD 16.1) into core/src/main/resources/scenarios/.

Each scenario is a whole conversation, replayed through the real pipeline by
com.tripwire.core.replay.ReplayHarness. They are fictional: numbers are in the unused
+91 90000 00xxx range and every UPI handle contains "tripwiredemo" (PRD 22: never use a
real person's chat or a real scam handle). Results on these sets are test-set results only.

    python3 tools/scenarios/build_scenarios.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "core", "src", "main", "resources", "scenarios")

WA = "com.whatsapp"
TG = "org.telegram.messenger"
SMS = "com.google.android.apps.messaging"
PHONEPE = "com.phonepe.app"
GPAY = "com.google.android.apps.nbu.paisa.user"
PKG_INSTALLER = "com.google.android.packageinstaller"
PLAY = "com.android.vending"
DIALER = "com.android.dialer"

DAY = 24 * 60


def msg(t, app, text, sender, phone=None, group=None, **expect):
    obs = {"type": "message", "app": app, "timestamp": 0, "source": "notification", "text": text, "senderName": sender}
    if phone:
        obs["senderPhone"] = phone
    if group:
        obs["groupName"] = group
    return step(t, obs, **expect)


def group_add(t, app, group, sender, phone=None, **expect):
    obs = {"type": "group_added", "app": app, "timestamp": 0, "source": "notification",
           "text": f"{sender} added you", "senderName": sender, "groupName": group}
    if phone:
        obs["senderPhone"] = phone
    return step(t, obs, **expect)


def call(t, kind, app, phone, sender=None, video=False, **expect):
    obs = {"type": kind, "app": app, "timestamp": 0, "source": "call", "senderPhone": phone,
           "senderName": sender or phone, "isVideoCall": video}
    return step(t, obs, **expect)


def install(t, pkg, label, installer, **expect):
    obs = {"type": "app_installed", "app": installer, "timestamp": 0, "source": "package",
           "installedPackage": pkg, "installedLabel": label, "installerPackage": installer}
    return step(t, obs, **expect)


def upi_link(t, handle, name, rupees, **expect):
    obs = {"type": "upi_link_opened", "app": "com.tripwire.app", "timestamp": 0, "source": "upi_link",
           "upi": {"payeeHandle": handle, "payeeName": name, "amount": {"paise": rupees * 100},
                   "rawUri": f"upi://pay?pa={handle}&pn={name.replace(' ', '%20')}&am={rupees}&cu=INR"}}
    return step(t, obs, **expect)


def payment_app(t, pkg, **expect):
    return step(t, {"type": "payment_app_opened", "app": pkg, "timestamp": 0, "source": "usage"}, **expect)


def screen_share(t, app, **expect):
    return step(t, {"type": "screen_share_started", "app": app, "timestamp": 0, "source": "screen"}, **expect)


def payment_sms(t, text, **expect):
    return step(t, {"type": "payment_sms", "app": SMS, "timestamp": 0, "source": "notification", "text": text,
                    "senderName": "AX-BANKSMS"}, **expect)


def step(t, obs, warn=None, notice=None, stage=None):
    s = {"t": t, "obs": obs}
    if warn is not None:
        s["expectWarning"] = warn
    if notice is not None:
        s["expectNotice"] = notice
    if stage is not None:
        s["expectStageAtLeast"] = stage
    return s


scenarios = []


def scenario(id, description, family, language, steps):
    scenarios.append({"id": id, "description": description, "family": family, "language": language, "steps": steps})


# ---------------------------------------------------------------- scam scripts

admin = "+919000000011"
scenario(
    "fi_ramesh_hinglish",
    "The PRD 4 story: added to an IPO group, fake profits, private Hinglish pitch, app from a link, payment to a non-@valid handle.",
    "fake_investment", "hi-Latn",
    [
        group_add(0, WA, "Elite IPO Club 88", "~ Rahul Mehta", admin, notice=False),
        msg(2 * DAY, WA, "Today's profit: our members booked ₹4.2 lakh profit 🚀 Thank you sir for the tips", "~ Vikas", "+919000000012", "Elite IPO Club 88", notice=False),
        msg(2 * DAY + 5, WA, "Guaranteed 30% returns in 10 days. Pre-IPO allotment only for VIP members.", "~ Rahul Mehta", admin, "Elite IPO Club 88", stage="HOOK"),
        msg(3 * DAY, WA, "Sir aapka allotment confirm hai, bas aaj hi karna padega, kisi ko mat batana", "~ Rahul Mehta", admin,
            notice=True, stage="GROOMING"),
        msg(4 * DAY, WA, "Download our SATFIN Pro app from this link https://satfin-pro.top/download/SATFINPro.apk and register with your mobile number", "~ Rahul Mehta", admin,
            stage="COMMITMENT"),
        install(4 * DAY + 10, "com.satfin.pro", "SATFIN Pro", PKG_INSTALLER, warn=True),
        msg(6 * DAY, WA, "Deposit ₹2,00,000 to satfin.tripwiredemo@ybl to activate your IPO allotment. Only today.", "~ Rahul Mehta", admin),
        upi_link(6 * DAY + 5, "satfin.tripwiredemo@ybl", "SATFIN Trading", 200000, warn=True, stage="EXTRACTION"),
    ],
)

analyst = "+919000000022"
scenario(
    "fi_telegram_to_whatsapp",
    "English stock-tips Telegram group hands off to WhatsApp, then a payment app opens within minutes.",
    "fake_investment", "en",
    [
        group_add(0, TG, "NSE Profit Masters", "Priya Analyst", notice=False),
        msg(60, TG, "Members made ₹85,000 yesterday with our call. Fixed daily 5% profit, no risk.", "Priya Analyst", group="NSE Profit Masters"),
        msg(DAY, TG, "Limited 10 seats in our institutional account. WhatsApp me at +91 90000 00022 for VIP access.", "Priya Analyst", group="NSE Profit Masters",
            notice=True, stage="GROOMING"),
        msg(DAY + 30, WA, "Hello, this is Priya from NSE Profit Masters. Your VIP slot is reserved, confirm within 2 hours.", "+91 90000 00022", analyst),
        msg(DAY + 40, WA, "Minimum deposit is ₹50,000. Pay to our UPI and the profit shows in your dashboard.", "+91 90000 00022", analyst),
        payment_app(DAY + 45, GPAY, warn=True),
    ],
)

caller = "+919000000033"
scenario(
    "da_sarojini_hindi",
    "Digital arrest in Hindi: a call, threats by message, a video call, then a payment app opened during the call.",
    "digital_arrest", "hi",
    [
        call(0, "call_started", DIALER, caller, notice=False),
        msg(2, SMS, "मैं मुंबई क्राइम ब्रांच से बोल रहा हूँ। आपके आधार से मनी लॉन्ड्रिंग का केस दर्ज हुआ है। आपके नाम गिरफ्तारी वारंट जारी है।", caller, caller, stage="HOOK"),
        call(6, "call_ended", DIALER, caller),
        msg(10, WA, "तुरंत वीडियो कॉल पर जुड़ें। किसी को मत बताना, यह गुप्त जाँच है।", caller, caller, notice=True, stage="GROOMING"),
        call(12, "call_started", WA, caller, video=True, warn=True),
        msg(40, WA, "अपने सारे पैसे RBI सुरक्षित खाते में ट्रांसफर करें: rbi.verify.tripwiredemo@okaxis", caller, caller),
        payment_app(45, PHONEPE, warn=True, stage="EXTRACTION"),
    ],
)

cbi = "+919000000044"
scenario(
    "da_cbi_screen_share",
    "English digital arrest: drugs in a parcel, arrest threat, a video call, a request to share the screen.",
    "digital_arrest", "en",
    [
        msg(0, WA, "This is CBI cyber cell. A parcel in your name with drugs was found at Mumbai customs. A non-bailable warrant has been issued.", "+91 90000 00044", cbi, stage="HOOK"),
        msg(3, WA, "Do not tell your family. This is a secret investigation. Join the video call now.", "+91 90000 00044", cbi, notice=True),
        call(5, "call_started", WA, cbi, video=True, warn=True),
        msg(20, WA, "Share your screen so we can verify your bank accounts are not linked to the case.", "+91 90000 00044", cbi),
        screen_share(22, WA, warn=True),
    ],
)

recruiter = "+919000000055"
scenario(
    "task_priya_telegram",
    "Task scam: small payouts for likes, then prepaid tasks and a withdrawal tax.",
    "task_scam", "en",
    [
        msg(0, TG, "Part time job: earn ₹150 per task by liking YouTube videos. Work from home, daily payment.", "HR Ananya", recruiter, notice=False),
        msg(30, TG, "₹150 credited for your first task. Join our Telegram channel t.me/tripwiredemo_tasks for more.", "HR Ananya", recruiter),
        msg(DAY, TG, "Prepaid task: pay ₹1,000 to our UPI and get ₹1,500 back in 30 minutes.", "HR Ananya", recruiter),
        msg(DAY + 60, TG, "Pay ₹5,000 for the next task to receive your ₹7,500 commission.", "HR Ananya", recruiter),
        msg(2 * DAY, TG, "To withdraw your ₹12,000 you must pay 18% tax first.", "HR Ananya", recruiter, stage="EXTRACTION"),
        upi_link(2 * DAY + 10, "tasks.tripwiredemo@ybl", "Ananya Services", 2160, warn=True),
    ],
)

scenario(
    "kyc_sms_apk",
    "Bank KYC impersonation by SMS with an app link.",
    "bank_kyc", "en",
    [
        msg(0, SMS, "Dear customer, your SBI account will be blocked today. Update KYC immediately by installing our KYC app: https://sbi-kyc-update.top/kyc.apk", "+91 90000 00066", "+919000000066", stage="COMMITMENT"),
        install(15, "com.kyc.update.sbi", "SBI KYC Update", "com.android.chrome", warn=True),
    ],
)

# ---------------------------------------------------------------- benign lookalikes (PRD 9.5)

scenario("b_recruiter", "A real recruiter: stranger, offers a job, never asks for money.", None, "en", [
    msg(0, WA, "Hi, I'm Neha from TalentBridge. We have a Java developer opening in Pune. Can you share your updated resume?", "+91 90000 00101", "+919000000101", notice=False),
    msg(DAY, WA, "Thanks! Your interview is scheduled tomorrow at 11 am on Google Meet. Link will come by email.", "+91 90000 00101", "+919000000101", notice=False),
])

scenario("b_broker_rm", "A real broker's relationship manager; payment to a SEBI @valid handle.", None, "en", [
    msg(0, WA, "Hello sir, I am your relationship manager from Zerodha. You can apply for the new IPO from the Kite app. Returns depend on market conditions.", "Amit RM", "+919000000102", notice=False),
    msg(60, WA, "For your SIP top-up, please pay only through the app or our validated UPI handle zerodha.brk@validhdfc.", "Amit RM", "+919000000102", notice=False),
    upi_link(70, "zerodha.brk@validhdfc", "Zerodha Broking", 10000, warn=False),
])

scenario("b_delivery", "Delivery and OTP messages from an unknown sender.", None, "en", [
    msg(0, SMS, "Your order #40512 is out for delivery. Share OTP 4567 with the delivery agent only.", "AX-DLVERY", notice=False),
    msg(120, SMS, "Your order #40512 has been delivered. Rate your experience.", "AX-DLVERY", notice=False),
])

scenario("b_bank_alert", "Bank alerts: urgency-like wording, transactional format.", None, "en", [
    msg(0, SMS, "Rs.5,000.00 debited from A/c XX1234 on 01-10-26 to VPA shop.tripwiredemo@ybl. UPI Ref No 123456789012. Not you? Call 18001234 immediately.", "VM-BANKAL", notice=False),
    msg(60, SMS, "Your A/c XX1234 is credited with Rs.12,000.00. Avl Bal Rs.45,210.00", "VM-BANKAL", notice=False),
])

scenario("b_customer_pays", "A new customer of a small business: the stranger pays the user.", None, "en", [
    msg(0, WA, "Hi, I want to order 2 chocolate cakes for Saturday. What's your UPI id? I'll pay ₹1,200 advance now.", "+91 90000 00105", "+919000000105", notice=False),
    msg(10, WA, "Paid ₹1,200, please check and confirm the order.", "+91 90000 00105", "+919000000105", notice=False),
])

scenario("b_society_investors", "A genuine investing group among acquaintances.", None, "en", [
    group_add(0, WA, "Society Investors Club", "Mr Sharma", "+919000000106", notice=False),
    msg(30, WA, "Did anyone buy the Tata Motors dip today?", "Mr Sharma", "+919000000106", "Society Investors Club", notice=False),
    msg(40, WA, "I'm holding long term. Returns were decent this year but markets are volatile.", "Mrs Iyer", "+919000000107", "Society Investors Club", notice=False),
    msg(60, WA, "Let's discuss SIPs and index funds at the meetup on Sunday.", "Mr Sharma", "+919000000106", "Society Investors Club", notice=False),
])

scenario("b_jobs_channel", "A real jobs group on Telegram.", None, "en", [
    group_add(0, TG, "Python Jobs India", "Admin", notice=False),
    msg(60, TG, "Hiring: backend intern at a Bengaluru startup, stipend ₹15,000 per month. Apply on the company careers page.", "Admin", group="Python Jobs India", notice=False),
])

scenario("b_new_number_friend", "A family friend on a new number asks about a wedding.", None, "hi-Latn", [
    msg(0, WA, "Namaste uncle, main Ravi ka dost hoon, ye mera naya number hai. Shaadi ka card aapko mil gaya kya?", "+91 90000 00108", "+919000000108", notice=False),
    msg(30, WA, "Haan ji, function Sunday ko hai, aap zaroor aaiye.", "+91 90000 00108", "+919000000108", notice=False),
])

scenario("b_courier_legit", "A real courier update, then an unrelated Play Store install and a shop payment.", None, "en", [
    msg(0, SMS, "Your parcel will be delivered today between 2-5 pm. Track it at bluedart.com", "BX-BLUDRT", notice=False),
    install(30, "com.swiggy.android", "Swiggy", PLAY, warn=False),
    upi_link(60, "chaiwala.tripwiredemo@okicici", "Chai Point", 40, warn=False),
])

scenario("b_hindi_police_notice", "A real police-related message: verification visit, no money, no video call.", None, "hi", [
    msg(0, SMS, "पासपोर्ट सत्यापन के लिए पुलिस कल सुबह 10 बजे आपके पते पर आएगी। कृपया पहचान पत्र तैयार रखें।", "VK-PSPORT", notice=False),
])


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    names = []
    for s in scenarios:
        name = f"{s['id']}.json"
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as fh:
            json.dump(s, fh, ensure_ascii=False, indent=1)
            fh.write("\n")
        names.append(name)
    with open(os.path.join(OUT, "index.txt"), "w") as fh:
        fh.write("# Replay scenarios, generated by tools/scenarios/build_scenarios.py\n")
        fh.write("\n".join(names) + "\n")
    scams = sum(1 for s in scenarios if s["family"])
    print(f"wrote {len(scenarios)} scenarios ({scams} scam, {len(scenarios) - scams} benign) to {OUT}")
