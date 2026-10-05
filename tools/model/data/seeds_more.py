"""Seed batch 2 (review of run qwen05: the model memorised seed phrasings, and single-seed hard
negatives landed only in test). More phrasings per tactic, and several seeds per hard-negative
concept so each concept appears in training and in the held-out splits. Fictional throughout.
"""

SCAM_MORE = {
    "guaranteed_returns": {
        "en": [
            (["guaranteed_returns"], "Our fund never makes a loss, {pct} every month is certain."),
            (["guaranteed_returns"], "Put in {amount} today and it becomes {amount_big} by month end, I promise."),
            (["guaranteed_returns"], "Zero-risk arbitrage, you cannot lose money with this setup."),
            (["guaranteed_returns"], "Expected profit is fixed at {pct}, the company bears all the risk."),
            (["guaranteed_returns"], "Every member is earning at least {amount} a day, it's assured."),
            (["guaranteed_returns"], "This IPO will list at a {pct} premium, 100 percent sure."),
        ],
        "hi": [
            (["guaranteed_returns"], "इसमें नुकसान का सवाल ही नहीं, हर महीने {pct} पक्का।"),
            (["guaranteed_returns"], "आज {amount} लगाइए, महीने के अंत तक {amount_big} हो जाएगा।"),
            (["guaranteed_returns"], "कंपनी पूरा जोखिम उठाती है, आपका मुनाफ़ा तय है।"),
            (["guaranteed_returns"], "यह IPO {pct} ऊपर खुलेगा, सौ प्रतिशत पक्का।"),
        ],
        "hi-Latn": [
            (["guaranteed_returns"], "Loss ka sawal hi nahi hai, har mahine {pct} fix."),
            (["guaranteed_returns"], "Aaj {amount} daalo, month end tak {amount_big} ban jayega."),
            (["guaranteed_returns"], "Company risk leti hai, aapka profit fix hai."),
            (["guaranteed_returns"], "IPO {pct} upar list hoga, sau taka pakka."),
        ],
    },
    "fake_social_proof": {
        "en": [
            (["fake_social_proof"], "Look at this, {name} from {city} just withdrew {amount_big}."),
            (["fake_social_proof"], "500+ happy investors, here are their payout receipts."),
            (["fake_social_proof"], "My uncle joined last month and already bought a car from the profits."),
            (["fake_social_proof"], "Sharing today's winners list, everyone in green."),
        ],
        "hi": [
            (["fake_social_proof"], "देखिए, {city} के {name} जी ने अभी {amount_big} निकाले।"),
            (["fake_social_proof"], "500 से ज़्यादा लोग कमा रहे हैं, उनकी रसीदें देखिए।"),
        ],
        "hi-Latn": [
            (["fake_social_proof"], "Dekho {city} wale {name} ne abhi {amount_big} nikale."),
            (["fake_social_proof"], "Mere chacha ne pichle mahine join kiya, profit se gaadi le li."),
        ],
    },
    "exclusivity": {
        "en": [
            (["exclusivity"], "This offer is by invitation only, please don't forward it."),
            (["exclusivity"], "Anchor investor quota, reserved for our top members."),
            (["exclusivity"], "We are onboarding only 20 new members this quarter."),
            (["exclusivity"], "You are among the chosen few to get the early access link."),
        ],
        "hi": [
            (["exclusivity"], "यह ऑफ़र सिर्फ़ आमंत्रित सदस्यों के लिए है।"),
            (["exclusivity"], "इस तिमाही केवल 20 नए सदस्य लिए जाएँगे।"),
        ],
        "hi-Latn": [
            (["exclusivity"], "Ye offer sirf invite wale members ke liye hai."),
            (["exclusivity"], "Anchor quota sirf top members ke liye reserve hai."),
        ],
    },
    "urgency": {
        "en": [
            (["urgency"], "The window closes at 3 pm sharp."),
            (["urgency"], "Do it right now, there is no time to think."),
            (["urgency"], "If you wait till tomorrow, the chance is gone."),
            (["urgency"], "Respond in the next {minutes} minutes."),
        ],
        "hi": [
            (["urgency"], "दोपहर 3 बजे के बाद मौका नहीं मिलेगा।"),
            (["urgency"], "सोचने का समय नहीं है, अभी कीजिए।"),
        ],
        "hi-Latn": [
            (["urgency"], "3 baje ke baad window band ho jayegi."),
            (["urgency"], "Sochne ka time nahi hai, abhi karo."),
        ],
    },
    "authority_claim": {
        "en": [
            (["authority_claim"], "Customs officer {name} here regarding your international parcel."),
            (["authority_claim"], "This is the RBI fraud monitoring desk."),
            (["authority_claim"], "I am a senior manager at {bank} head office."),
            (["authority_claim"], "Call from the TRAI department about your mobile number."),
        ],
        "hi": [
            (["authority_claim"], "मैं RBI के धोखाधड़ी निगरानी विभाग से हूँ।"),
            (["authority_claim"], "मैं {bank} के मुख्य कार्यालय से वरिष्ठ प्रबंधक बोल रहा हूँ।"),
            (["authority_claim"], "मैं कस्टम्स अधिकारी हूँ, आपके पार्सल के बारे में।"),
        ],
        "hi-Latn": [
            (["authority_claim"], "Main RBI ke fraud department se bol raha hoon."),
            (["authority_claim"], "Customs officer baat kar raha hoon, aapke parcel ke baare me."),
        ],
    },
    "legal_threat": {
        "en": [
            (["legal_threat"], "Police will reach your house within two hours."),
            (["legal_threat"], "Your SIM card will be deactivated and a case filed."),
            (["legal_threat"], "You are named in a narcotics smuggling investigation."),
            (["legal_threat"], "Failure to comply will result in immediate detention."),
        ],
        "hi": [
            (["legal_threat"], "दो घंटे में पुलिस आपके घर पहुँच जाएगी।"),
            (["legal_threat"], "आपका सिम बंद होगा और मुकदमा दर्ज होगा।"),
        ],
        "hi-Latn": [
            (["legal_threat"], "Do ghante me police aapke ghar aa jayegi."),
            (["legal_threat"], "Aapka SIM band hoga aur case darj hoga."),
        ],
    },
    "secrecy": {
        "en": [
            (["secrecy"], "This must stay between us, not even your spouse should know."),
            (["secrecy"], "If you tell the bank staff, the investigation will be compromised."),
            (["secrecy"], "Delete this chat after reading."),
        ],
        "hi": [
            (["secrecy"], "यह बात हमारे बीच रहनी चाहिए, पत्नी को भी नहीं।"),
            (["secrecy"], "बैंक वालों को बताया तो जाँच बिगड़ जाएगी।"),
        ],
        "hi-Latn": [
            (["secrecy"], "Ye baat humare beech rahegi, biwi ko bhi nahi batana."),
            (["secrecy"], "Chat padh ke delete kar dena."),
        ],
    },
    "channel_move": {
        "en": [
            (["channel_move"], "Continue on Signal, WhatsApp is not secure for this."),
            (["channel_move"], "Switch on your camera and join the Skype call."),
            (["channel_move"], "Save my number and message me directly."),
        ],
        "hi": [
            (["channel_move"], "मेरा नंबर सेव करके सीधे मैसेज करें।"),
            (["channel_move"], "कैमरा चालू करके कॉल पर बने रहें।"),
        ],
        "hi-Latn": [
            (["channel_move"], "Mera number save karke direct message karo."),
            (["channel_move"], "Camera on karke call pe raho."),
        ],
    },
    "install_request": {
        "en": [
            (["install_request"], "Get the {app} application, it is not on Play Store, use this link: {link}"),
            (["install_request"], "Tap the file I shared and allow installation from unknown sources."),
            (["install_request"], "Our trading software must be installed before market opens: {link}"),
        ],
        "hi": [
            (["install_request"], "यह ऐप प्ले स्टोर पर नहीं है, इस लिंक से डालिए: {link}"),
            (["install_request"], "भेजी गई फ़ाइल खोलें और इंस्टॉल की अनुमति दें।"),
        ],
        "hi-Latn": [
            (["install_request"], "Ye app Play Store pe nahi hai, is link se daalo: {link}"),
            (["install_request"], "Jo file bheji hai usko open karke install allow karo."),
        ],
    },
    "remote_access_request": {
        "en": [
            (["remote_access_request"], "Let our technician control your phone to fix the KYC issue."),
            (["remote_access_request"], "Turn on screen sharing in the video call so I can guide you."),
            (["remote_access_request"], "Download RustDesk and read me the ID shown on screen."),
        ],
        "hi": [
            (["remote_access_request"], "हमारे तकनीशियन को फ़ोन चलाने दीजिए, KYC ठीक हो जाएगा।"),
            (["remote_access_request"], "वीडियो कॉल में स्क्रीन शेयरिंग चालू कीजिए।"),
        ],
        "hi-Latn": [
            (["remote_access_request"], "Technician ko phone control karne do, KYC theek ho jayega."),
            (["remote_access_request"], "Video call me screen sharing on karo."),
        ],
    },
    "credential_request": {
        "en": [
            (["credential_request"], "Read out the 6-digit code that just came by SMS."),
            (["credential_request"], "What are the last four digits of your card and the expiry?"),
            (["credential_request"], "Type your MPIN here so we can verify the account."),
        ],
        "hi": [
            (["credential_request"], "अभी SMS पर आया 6 अंकों का कोड पढ़कर बताइए।"),
            (["credential_request"], "अपना MPIN यहाँ लिखिए।"),
        ],
        "hi-Latn": [
            (["credential_request"], "Abhi SMS pe jo 6 digit code aaya woh bolo."),
            (["credential_request"], "Card ke last 4 digit aur expiry batao."),
        ],
    },
    "payment_request": {
        "en": [
            (["payment_request"], "Move the full balance to this verification account today."),
            (["payment_request"], "Top up your trading wallet with {amount} to unlock the IPO."),
            (["payment_request"], "Send the processing charge of {amount} through Google Pay."),
            (["payment_request"], "Make the deposit to {handle} and share the screenshot."),
        ],
        "hi": [
            (["payment_request"], "पूरा बैलेंस इस सत्यापन खाते में डाल दीजिए।"),
            (["payment_request"], "IPO खोलने के लिए वॉलेट में {amount} डालिए।"),
            (["payment_request"], "{handle} पर पैसे डालकर स्क्रीनशॉट भेजिए।"),
        ],
        "hi-Latn": [
            (["payment_request"], "Poora balance verification account me daal do."),
            (["payment_request"], "Wallet me {amount} top up karo, IPO unlock hoga."),
            (["payment_request"], "{handle} pe paise daal ke screenshot bhejo."),
        ],
    },
    "fee_to_withdraw": {
        "en": [
            (["fee_to_withdraw"], "Your payout is ready, only the {amount} clearance charge is pending."),
            (["fee_to_withdraw"], "Account upgrade fee is required before you can withdraw."),
            (["fee_to_withdraw"], "Release of funds needs an anti-money-laundering certificate fee."),
        ],
        "hi": [
            (["fee_to_withdraw"], "आपका पैसा तैयार है, बस {amount} क्लियरेंस चार्ज बाकी है।"),
            (["fee_to_withdraw"], "पैसा निकालने से पहले अकाउंट अपग्रेड फ़ीस देनी होगी।"),
        ],
        "hi-Latn": [
            (["fee_to_withdraw"], "Payout ready hai, bas {amount} clearance charge baaki hai."),
            (["fee_to_withdraw"], "Withdraw se pehle upgrade fee deni padegi."),
        ],
    },
    "small_win_bait": {
        "en": [
            (["small_win_bait"], "Rate 3 hotels on Google Maps and get {amount_small} instantly."),
            (["small_win_bait"], "Welcome bonus of {amount_small} sent, check your UPI."),
            (["small_win_bait"], "Complete 5 simple reviews today, {amount_small} each."),
        ],
        "hi": [
            (["small_win_bait"], "गूगल मैप पर 3 होटल को रेटिंग दीजिए, तुरंत {amount_small} पाइए।"),
            (["small_win_bait"], "{amount_small} का वेलकम बोनस भेज दिया है।"),
        ],
        "hi-Latn": [
            (["small_win_bait"], "Google Maps pe 3 hotel rate karo, turant {amount_small}."),
            (["small_win_bait"], "Welcome bonus {amount_small} bhej diya, UPI check karo."),
        ],
    },
}

BENIGN_MORE2 = {
    # Hard negative: a colleague or teacher asks to share a screen.
    "work": {
        "en": [([], "Can you share your screen so I can see the spreadsheet error?"),
               ([], "Ma'am, please share your screen during the online class presentation."),
               ([], "I'll share my screen in the meeting and walk everyone through the plan."),
               ([], "Share your screen on Meet, let's debug the code together.")],
        "hi-Latn": [([], "Meeting me apni screen share karna, report dekhni hai."),
                    ([], "Class me presentation ke time screen share kar dena.")],
        "hi": [([], "मीटिंग में अपनी स्क्रीन शेयर कर दीजिए, रिपोर्ट देखनी है।")],
    },
    # Hard negative: genuine officials who ask for nothing, or point to official channels.
    "official_genuine": {
        "en": [([], "Beat constable will visit for tenant verification on Saturday morning."),
               ([], "Your passport has been dispatched by Speed Post."),
               ([], "Your PAN-Aadhaar link status: linked. No action required."),
               ([], "Court hearing for your property case is on 12 Nov, says your lawyer's office.")],
        "hi": [([], "किरायेदार सत्यापन के लिए शनिवार सुबह पुलिसकर्मी आएँगे।"),
               ([], "आपका पासपोर्ट स्पीड पोस्ट से भेज दिया गया है।"),
               ([], "पैन-आधार लिंक हो चुका है, कुछ करने की ज़रूरत नहीं।")],
        "hi-Latn": [([], "Tenant verification ke liye police Saturday ko aayegi."),
                    ([], "Passport speed post se bhej diya gaya hai.")],
    },
    # Hard negative: customers and shops say "send" and "share" without asking for money.
    "customer": {
        "en": [([], "Send me the price list for wedding catering please."),
               ([], "Can you share photos of the flat before I visit?"),
               ([], "Please send the invoice to my email."),
               ([], "Share the location, I'll come pick up the order.")],
        "hi": [([], "शादी के खाने की रेट लिस्ट भेज दीजिए।"),
               ([], "फ़्लैट की फ़ोटो भेज दीजिए।")],
        "hi-Latn": [([], "Rate list bhej do bhaiya."),
                    ([], "Flat ki photos bhej do, kal dekhne aaunga."),
                    ([], "Location share karo, main order lene aata hoon.")],
    },
    # Hard negative: family secrecy that is not isolation from protection.
    "family_logistics": {
        "en": [([], "Don't tell grandpa, we're throwing him a surprise birthday dinner."),
               ([], "Keep it a secret till Sunday, the gift is for Mom."),
               ([], "Can you send ₹500 till Friday? I'll return it on salary day.")],
        "hi": [([], "दादाजी को मत बताना, उनका सरप्राइज़ जन्मदिन है।"),
               ([], "रविवार तक छिपा कर रखना, तोहफ़ा मम्मी के लिए है।")],
        "hi-Latn": [([], "Nana ji ko mat batana, surprise party hai."),
                    ([], "Bhai 500 bhej de Friday tak, salary aate hi lauta dunga.")],
    },
    # Hard negative: genuine services telling you to install from the store or not share OTPs.
    "service": {
        "en": [([], "Download our app from the Play Store to track your service request."),
               ([], "Never share your OTP with anyone, including our staff."),
               ([], "Update your app from Google Play to get the new features.")],
        "hi": [([], "अपना ओटीपी किसी को न बताएँ, हमारे कर्मचारी भी कभी नहीं माँगते।"),
               ([], "सेवा अनुरोध देखने के लिए प्ले स्टोर से हमारा ऐप डाउनलोड करें।")],
        "hi-Latn": [([], "OTP kisi ko mat batana, bank kabhi nahi maangta."),
                    ([], "Play Store se app update kar lo.")],
    },
    # Hard negative: genuine urgency from known services.
    "delivery": {
        "en": [([], "Urgent: your parcel needs a signature, the courier is waiting outside."),
               ([], "Hurry, your food is at the gate and getting cold!")],
        "hi-Latn": [([], "Jaldi niche aao, delivery wala wait kar raha hai.")],
    },
    "investing_chat": {
        "en": [([], "I booked some profit on {stock} today, but markets can turn anytime."),
               ([], "Read the SEBI investor awareness page before buying any IPO.")],
        "hi-Latn": [([], "Aaj {stock} me thoda profit book kiya, par market ka bharosa nahi.")],
    },
    "recruiter": {
        "en": [([], "Congratulations, you are shortlisted. There are no charges at any stage of hiring."),
               ([], "Please upload your documents on our careers portal before Friday.")],
        "hi-Latn": [([], "Selection process me koi fees nahi hai, documents le aana.")],
    },
}


def merge(scam, benign):
    for tactic, by_lang in SCAM_MORE.items():
        for lang, items in by_lang.items():
            scam.setdefault(tactic, {}).setdefault(lang, []).extend(items)
    for cat, by_lang in BENIGN_MORE2.items():
        for lang, items in by_lang.items():
            benign.setdefault(cat, {}).setdefault(lang, []).extend(items)
