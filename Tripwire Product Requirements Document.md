# Tripwire: Product Requirements Document

Oct 5, 2026 ·&#32;

## 1. About this document

This PRD says what to build for Tripwire, from first install to partner integrations. It pairs with the research report *Tripwire: On-Device AI That Stops Cross-App Scams at the Moment of Payment*. The report holds the evidence and the market case; this PRD holds the build specification. Together the two files are meant to be enough to understand and build the product with no other context.

**Scope rule.** Nothing here is cut to fit a deadline. Priorities reflect product value and dependency order only.

**Conventions used throughout**

- **Requirement IDs** look like `SIG-03`. The prefix names the module (section 8).
- **P0** = needed for the first working version. **P1** = needed for a public launch. **P2** = later.
- **Target** means a goal to validate in testing, not a measured result.
- **Must / should / may** carry their usual meaning: required, strongly preferred, optional.
- Figures marked with a source come from the research report. Everything else is a design decision made in this PRD.

**Where to look**

| If you want to know | Read |
| --- | --- |
| What the product is and why it exists | Sections 2 to 4 |
| Who it is for and how they use it | Sections 5 to 7 |
| Exactly what to build | Sections 8 to 13 |
| Quality bars, privacy and legal limits | Sections 14 to 17 |
| Order of work, business, competitors, risks | Sections 18 to 21 |
| How to show it, what is still undecided, and reference material | Sections 22 to 24 |

## 2. Product summary

Tripwire is an Android app that spots a scam while it is still unfolding and steps in just before the victim pays. It runs entirely on the phone.

**One-line pitch.** Tripwire follows each stranger who contacts you across WhatsApp, Telegram, SMS, app installs and UPI payments. It recognises when you are partway through a known scam script and stops you at the one step that cannot be undone: the payment.

**How it works, in five steps**

1. **Watch.** Tripwire reads incoming messages from people who are not in your contacts. It reads them on the phone and sends nothing to a server.
2. **Remember.** It keeps a private record for each stranger: what they said, which apps they used, and what they asked you to do.
3. **Recognise.** An on-device AI model labels each message with the persuasion tactics it uses. A small tracker then works out which scam script this looks like and how far along it is.
4. **Interrupt.** Tripwire stays silent until you are about to do something irreversible: install an app from a chat link, share your screen, or pay. Then it shows a full-screen warning, spoken in your language, with the reasons.
5. **Recover.** If money has already gone, it builds a ready-to-file complaint for the 1930 cyber-fraud helpline and alerts a trusted family member.

**What makes it different.** Existing tools judge one message, call or link at a time. Tripwire judges one person's behaviour over days and across apps, and it acts at the payment, not at the first message.

**Why on-device.** People will not accept an app that uploads their private chats. On-device processing is also faster, works offline and has no per-message cloud cost.

**Hackathon track.** Open Innovation (track 06), or FinTech & Commerce where that track exists.

**Platform.** Android only. iOS does not let third-party apps read other apps' notifications, so the core design cannot work there.

## 3. Problem and evidence

Indians lose roughly ₹20,000 crore a year to cyber fraud, most of it to scams that unfold over days across several apps, and most of the money is never recovered.

**The numbers**

| Fact | Figure | Source |
| --- | --- | --- |
| Money lost to cyber fraud, 2025 | ₹22,495 crore (2024: ₹22,845 crore) | Ministry of Home Affairs data, via [Moneylife](https://www.moneylife.in/article/fraud-alert-india-is-losing-over-22000-crore-a-year-in-cyber-scams-and-the-worst-is-yet-to-come/80612.html) |
| Cases, 2025 | 28.15 lakh (2024: 22.68 lakh) | Same |
| Calls to the 1930 helpline, 2025 | 32.4 million | Same |
| Share of 2025 losses from investment fraud | 76% | Same |
| Share from digital arrest | 9% | Same |
| Digital-arrest cases | 39,925 (2022) to 123,672 (2024); ₹1,935.51 crore lost in 2024 | Moneylife; [The420](https://the420.in/india-cybercrime-2025-losses-i4c-cpt-policy-reform/) |
| Alternative 2025 tally | ₹19,812.96 crore across 21,77,524 complaints | I4C data, via [The420](https://the420.in/india-cyber-fraud-losses-i4c-data-statewise-investment-scams/) |
| Upper-bound estimate of true 2025 losses | Up to ₹1.2 lakh crore (a projection, not a count) | I4C |
| Money not recovered | About three rupees in four | Moneylife |
| Fraud money frozen by first-hour action | 34% in Uttar Pradesh, 31% nationally | [The420](https://the420.in/up-first-hour-cyber-fraud-freezing/) |
| Victims who did not know they were being scammed | 78% of 3,780 contacted | FBI IC3 2025 Annual Report (US data) |

The two 2025 loss totals do not agree. Either way the loss is about ₹20,000 crore a year and complaints run into millions.

**How the dominant scam works.** The fake-investment scam follows a fixed order, documented by the Ministry of Home Affairs and the Enforcement Directorate ([Asianet News](https://newsable.asianetnews.com/india/whatsapp-telegram-investment-scams-mha-warns-of-guaranteed-profits-articleshow-zy4elyw), [The Quint](https://www.thequint.com/news/webqoof/fake-investment-trading-ipo-whatsapp-groups-and-apps-dupe-investors)):

1. The victim is added to a WhatsApp or Telegram group without asking.
2. Fake members post invented profits.
3. The victim is told to install a "trading app" from a private link, not an app store.
4. The victim is told to send money to accounts under various company names.
5. The app shows fake profits to draw more deposits.
6. Withdrawal is blocked and the victim is removed from the group.

**Why existing protection fails**

- **It checks one thing at a time.** Each message, link or payment looks harmless alone. The danger is in the sequence.
- **It sits at the wrong moment.** Spam filters act at first contact, when users ignore warnings. Helplines act after the loss.
- **No single company sees the whole sequence.** WhatsApp sees the chat, the payment app sees the payment, the bank sees the transfer.
- **The victim cannot see it either.** The FBI figure above shows most victims do not realise what is happening while it happens.
- **Time decides recovery.** Reporting within the first hour, the "golden hour", is what gets money frozen. Most victims report late.

**The problem statement.** A person being scammed has no tool that sees the whole sequence, warns at the moment that matters, and helps them act fast if they have already paid.

## 4. Goals, non-goals and success metrics

Tripwire succeeds if users abandon scam payments after its warning, and if it stays quiet enough that they keep it installed.

**Product goals**

1. **Stop the payment.** Interrupt the user before an irreversible step in a scam, with reasons they can check.
2. **Detect early.** Recognise the scam before the first payment request, not after.
3. **Stay quiet.** Warn only when it matters, so warnings keep their force.
4. **Speed up recovery.** If money has gone, get a complete complaint filed inside the golden hour.
5. **Keep chats private.** Never send message content off the phone.
6. **Work for Indian users.** Handle Hindi, Hinglish and regional languages, cheap data plans and weak connectivity.

**Non-goals**

- Tripwire does not block spam calls or identify callers. Truecaller and telecom tools do that.
- It is not an antivirus. It does not scan app code for malware.
- It does not guarantee protection or reimburse losses.
- It does not read or score conversations with saved contacts by default.
- It does not block payments by force. The user always keeps the final decision.
- It does not let a family member read the protected person's messages.
- It does not move, freeze or recover money itself. It prepares the complaint; the user files it.
- It does not support iOS.

**Success metrics.** All figures are targets to validate.

| Metric | Definition | Target |
| --- | --- | --- |
| Payment abandonment rate | Share of full-screen payment warnings after which the user does not pay within 24 hours | 60% or higher |
| Stage at first detection | Scam stage when the first banner appears, on the test set | Before the payment request in 80% of scripted scams |
| False full-screen warnings | Full-screen warnings the user marks as wrong | Fewer than 1 per user per month |
| Benign false-flag rate | Ordinary stranger conversations that reach banner level, on the benign test set | Under 2% |
| Interception delay | Time from the risky action to the warning on screen | Under 300 ms |
| Time to complaint | Time from "I already paid" to a complete evidence pack | Under 2 minutes to build, under 10 minutes to file |
| Retention | Users with permissions still granted after 30 days | 60% or higher |
| Battery cost | Extra battery use per day | Under 3% |
| Setup completion | Users who finish setup once started | 70% or higher |

**The headline metric** is payment abandonment rate. It is the closest measurable stand-in for money saved.

## 5. Users and personas

Tripwire has two users per household: the person who installs it and the person it protects. They are often different people.

**User roles**

| Role | Who they are | What they do in the product |
| --- | --- | --- |
| Protected user | The person whose phone runs Tripwire | Receives warnings, decides whether to proceed, files complaints |
| Trusted ally | A family member or friend chosen by the protected user | Gets an alert when a serious warning fires; can call the protected user |
| Protector | The person who sets Tripwire up, often an adult child | Installs it on a parent's phone, usually becomes the ally, pays for the family plan |

One person can hold all three roles on their own phone.

**Primary segment: the family protector.** A 25 to 40-year-old urban professional who worries about a parent. They have usually heard a scam story in their own circle. They are the buyer and the installer, so setup must be quick and explainable to a parent in person.

**Secondary segments**

- **First-time retail investors, 30 to 55.** The main target of fake stock-tip and IPO groups.
- **Senior citizens.** The main target of digital-arrest calls from fake police or officials.
- **Job seekers and students.** Targets of task and job scams.
- **Later:** the Indian diaspora, and Southeast Asian markets with similar scam operations.

**Personas**

**Ramesh, 54, Pune.** Salaried, speaks Marathi, uses WhatsApp daily and PhonePe weekly. New to stock investing. He trusts what looks official and dislikes being told he is wrong. *Needs:* a warning in Marathi that shows evidence, not an accusation. *Risk:* he dismisses the first warning.

**Anil, 29, Bengaluru (Ramesh's son).** Software engineer. Installs Tripwire on his father's phone during a visit home. *Needs:* a five-minute setup, confidence that it will not spy on his father, and an alert he can act on from another city.

**Sarojini, 71, Hyderabad.** Retired teacher, lives alone, speaks Telugu, reads small text with difficulty. Target of a "your Aadhaar is linked to money laundering" call. *Needs:* a spoken warning, a single large button to call her daughter, and nothing that requires typing.

**Priya, 23, Lucknow.** Job seeker, joins many Telegram groups. Offered "₹150 per task" work. *Needs:* a tool that tells real part-time work from a task scam without flagging every recruiter.

**Paying customers (business)**

| Customer | Why they would pay |
| --- | --- |
| Phone makers (vivo/iQOO, HMD, Lava and others) | A "scam shield" feature to market in India |
| Brokers, banks, payment apps | Regulatory pressure from SEBI and RBI to cut fraud |
| Insurers | Lower claims on cyber-fraud cover |

## 6. Core concepts and glossary

These terms are used with fixed meanings in the rest of this document.

**Product terms**

| Term | Meaning |
| --- | --- |
| Counterparty | Any person or group that contacts the user and is not a saved contact. Also called a stranger. The unit Tripwire tracks. |
| Ledger | The private on-device record Tripwire keeps for one counterparty: events, tags, stage, risk. |
| Event | One thing that happened: a message, a group invite, a call, an app install, a payment attempt. |
| Signal collector | A component that turns something on the phone into events. Example: the notification reader. |
| Tactic tag | A label for a persuasion tactic found in a message, such as "guaranteed returns" or "urgency". There are 14 (Appendix A). |
| Tactic reader | The on-device language model that assigns tactic tags to a message. |
| Scam family | A type of scam with its own script. Example: fake investment, digital arrest. |
| Script | The ordered stages a scam family moves through. |
| Stage | The current position in a script. Example: Hook, Grooming, Extraction. |
| Progression engine | The tracker that reads tags and events and decides the family, stage and risk for a counterparty. Not a language model. |
| Risk score | A number from 0 to 100 for one counterparty. Higher means more likely a scam, further along. |
| Case | A counterparty whose risk has crossed the watch threshold. Cases appear in the app. |
| Tripwire moment | An irreversible or high-stakes action by the user: installing an app from a link, sharing the screen, or paying. |
| Intervention | Anything Tripwire shows the user: a banner, a full-screen warning, a spoken explanation. |
| Grounded check | A check against an external fact, not a model opinion. Example: is this UPI handle an `@valid` handle? |
| Trusted ally | A person the user chooses to be alerted when a serious warning fires. |
| Evidence pack | A document with everything needed to file a cyber-fraud complaint: timeline, screenshots, numbers, transaction details. |
| Script pack | A signed file of updated scam scripts and thresholds that Tripwire downloads. Contains no user data. |

**Domain terms**

| Term | Meaning |
| --- | --- |
| UPI | Unified Payments Interface, India's instant bank-to-bank payment system. |
| UPI handle / VPA | A payment address such as `name@ybl`. |
| `@valid` handle | A UPI handle format that SEBI requires registered brokers and mutual funds to use for collecting investor money. Live since October 2025. |
| SEBI | Securities and Exchange Board of India, the market regulator. |
| SEBI Check | SEBI's tool for verifying a broker's payment details. |
| FRI | Financial Fraud Risk Indicator. A Department of Telecommunications label that rates phone numbers by fraud risk. |
| 1930 | India's national cyber-fraud helpline number. |
| cybercrime.gov.in | The national portal for filing cybercrime complaints. |
| I4C | Indian Cyber Crime Coordination Centre, under the Ministry of Home Affairs. |
| UTR | Unique Transaction Reference. The ID of a bank or UPI transfer, needed in a complaint. |
| Golden hour | The first hour after a fraudulent transfer, when banks can most often freeze the money. |
| Digital arrest | A scam where callers posing as police or officials claim the victim is under investigation and must stay on a video call and transfer money. |
| APK | An Android app file. Installing one from a link, outside an app store, is called sideloading. |
| NPU | Neural processing unit. The chip in a phone that runs AI models efficiently. |
| DPDP Act | Digital Personal Data Protection Act, 2023. India's data-protection law. |

## 7. User journeys

Tripwire has nine journeys. Most of the time the user is in journey 2 and sees nothing.

### Journey 1: Setup

1. The protector installs Tripwire from Google Play on the protected user's phone.
2. A welcome screen explains in three plain sentences what Tripwire does and that chats never leave the phone.
3. The user picks a language for warnings.
4. A disclosure screen lists exactly what Tripwire reads and why. The user must tap "I agree" to continue.
5. The user grants notification access. This is the only required permission.
6. The user is offered optional permissions one at a time, each with its reason: contacts, display over other apps, usage access, on-screen reading, phone state.
7. The user adds a trusted ally by picking a contact. The ally gets an SMS invitation. This step can be skipped.
8. Tripwire downloads the language model over Wi-Fi if it is not already present.
9. A final screen shows "Tripwire is watching" and a test button that plays a sample warning.

Target: under 90 seconds, excluding the model download.

### Journey 2: Silent monitoring

1. A message arrives from someone not in contacts.
2. Tripwire opens or updates that counterparty's ledger.
3. The tactic reader tags the message. The progression engine updates the stage and risk.
4. Risk stays below the watch threshold. Nothing is shown.

Messages from saved contacts are ignored.

### Journey 3: Early notice

1. A counterparty's risk crosses the watch threshold. Example: an unknown group admin posts guaranteed returns, then moves to private chat.
2. Tripwire posts one quiet notification: "This chat matches a known fake-investment pattern. Tap to see why."
3. Tapping opens the counterparty timeline.
4. The user can mark the counterparty as trusted, which stops tracking, or do nothing.

At most one early notice per counterparty per stage.

### Journey 4: Install interception

1. The user taps a link from a risky counterparty and begins installing an app from outside the Play Store.
2. Tripwire shows a full-screen warning before the install completes, and speaks it aloud.
3. The warning states who sent the link, what they have been promising, and that the app is not from an app store.
4. The user chooses "Don't install", "Call my ally first" or "Install anyway".
5. "Install anyway" needs a three-second hold. The choice is logged and risk rises.

If Tripwire could not catch the install in time, it warns right after and offers to uninstall.

### Journey 5: Payment interception

1. The user opens a payment app, or taps a payment link or QR code, while a high-risk case is active.
2. Tripwire shows a full-screen warning with the cross-app timeline of how the user got here.
3. It shows the result of the grounded check. Example: "This handle is not a SEBI `@valid` handle. Registered brokers must use one."
4. The user chooses "Don't pay", "Call my ally first", "Verify on SEBI Check" or "Pay anyway".
5. "Pay anyway" needs a three-second hold. The ally is alerted if one is set.

### Journey 6: Screen-share or remote-access interception

1. During or soon after a call from a stranger, the user opens a remote-access app or starts screen sharing.
2. Tripwire shows a full-screen warning: no bank or police officer ever needs to see your screen.
3. The user chooses "Stop" or "Continue anyway" with a hold.

### Journey 7: Digital-arrest call

1. A stranger calls, often by WhatsApp video, claiming to be police or an official.
2. Messages from the same counterparty carry authority, threat and secrecy tags. Risk rises fast.
3. Tripwire shows a full-screen notice during the call: "Police do not arrest anyone by video call. This is a known scam."
4. The user can tap one button to call the ally or to hang up and dial 1930.
5. If the user opens a payment app, journey 5 fires with the digital-arrest explanation.

### Journey 8: "I already paid"

1. The user taps "I already paid" on the home screen or on any warning.
2. Tripwire asks which case, then asks for the amount and time. It pre-fills what it can from payment SMS.
3. It builds the evidence pack: timeline, counterparty numbers and handles, transaction reference, screenshots, app details.
4. It shows a countdown of the golden hour and one button: "Call 1930 now".
5. It offers to open cybercrime.gov.in with the pack ready to attach, and to share the pack with the ally and the bank.
6. It lists next steps: call the bank's fraud line, do not pay any "recovery fee", keep the phone and chats as they are.

### Journey 9: Ally alert

1. A full-screen warning fires on the protected user's phone, or they choose "Pay anyway".
2. The ally receives a push notification, or an SMS if they do not have the app.
3. The alert names the scam type and stage. It contains no message content.
4. The ally taps "Call now". After the call they can mark the alert as handled.

### What happens on each choice

| User choice on a full-screen warning | Result |
| --- | --- |
| Stops the action | Case stays open. Counterparty is flagged. User is offered "Block and report". |
| Calls ally first | Call starts. Warning returns when the call ends. |
| Proceeds anyway | Logged. Risk rises. Ally alerted. "I already paid" shortcut pinned for 24 hours. |
| Marks as trusted | Tracking stops for this counterparty. Feedback stored to tune thresholds. |

## 8. Functional requirements

The product has 16 modules. The first working version needs every P0 line below and nothing else.

### 8.1 Onboarding and permissions (ONB)

| ID | Requirement | Priority |
| --- | --- | --- |
| ONB-01 | Show a disclosure screen that lists every type of data read, why, and that it stays on the phone. Require explicit consent before any permission prompt. | P0 |
| ONB-02 | Guide the user into system settings to grant notification access, and detect when it is granted. | P0 |
| ONB-03 | Ask for each optional permission separately, with its reason. The app must work with notification access alone. | P0 |
| ONB-04 | Let the user choose the warning language. | P0 |
| ONB-05 | Download and verify the model files. Resume interrupted downloads. Use Wi-Fi by default. | P0 |
| ONB-06 | Offer a test button that plays a sample full-screen warning. | P0 |
| ONB-07 | On each app open, check which protections are on and show any that are off. | P0 |
| ONB-08 | Add a trusted ally from contacts and send an invitation. | P1 |
| ONB-09 | Guide the user to exempt Tripwire from battery optimisation, with steps for each phone maker. | P1 |
| ONB-10 | "Set up for someone else" mode: larger explanations, and the protected user confirms consent themselves. | P1 |

### 8.2 Signal collection (SIG)

| ID | Requirement | Priority |
| --- | --- | --- |
| SIG-01 | Read notifications from WhatsApp, WhatsApp Business, Telegram and the SMS app. Extract sender, group name, text and time. | P0 |
| SIG-02 | Decide whether the sender is a stranger by checking contacts. Without contacts permission, treat any sender shown as a raw phone number as a stranger. | P0 |
| SIG-03 | Ignore notifications from saved contacts and discard their text at once. | P0 |
| SIG-04 | Detect "you were added to a group" events. | P0 |
| SIG-05 | Extract entities with fixed rules, not the model: URLs, UPI handles, phone numbers, amounts, app-file links, app names. | P0 |
| SIG-06 | Detect a newly installed app and read where it was installed from. Flag any app not installed from a known app store. | P0 |
| SIG-07 | Detect a payment app coming to the foreground, using usage access and a list of UPI app names. | P0 |
| SIG-08 | Register as a handler for UPI payment links, so the user can pick "Check with Tripwire". Read the payee handle, name and amount, then pass the payment on to the user's payment app. | P0 |
| SIG-09 | Write every observation as one normalised event: counterparty, app, type, text, entities, time. | P0 |
| SIG-10 | On-screen reading, opt-in: read visible chat text in supported messaging apps. This catches messages that produced no notification, and the user's own replies. | P1 |
| SIG-11 | Detect the system install screen opening, so the warning can appear before the install completes. Needs on-screen reading. | P1 |
| SIG-12 | Detect calls: cellular call state and number, and WhatsApp or Telegram calls through their ongoing-call notification. | P1 |
| SIG-13 | Detect screen sharing and remote access: a known remote-access app in the foreground, or the system screen-cast consent screen. | P1 |
| SIG-14 | Read payment-confirmation SMS to capture amount, payee and transaction reference. | P1 |
| SIG-15 | Link one counterparty across apps when the same phone number, UPI handle or link appears, or when a message says "contact me on Telegram at X". | P1 |
| SIG-16 | Decode a QR code from an image the user shares to Tripwire, and read the UPI details inside. | P1 |
| SIG-17 | Read images such as profit screenshots with an on-device vision model. | P2 |
| SIG-18 | Transcribe voice notes the user shares to Tripwire. | P2 |

**Known limits of signal collection.** These shape the design and must be stated to users honestly.

- Notifications miss messages that arrive while the chat is open, messages in muted groups, and long messages that are cut short. SIG-10 closes most of this gap.
- An independent app cannot block another app's payment. It can only show a warning on top. True blocking needs phone-maker integration (section 8.16).
- Some payment and banking apps hide their screens from on-screen reading. Tripwire must not depend on reading inside payment apps. SIG-07 and SIG-08 work without it.
- Android does not let ordinary apps record call audio. Call content is out of reach without phone-maker integration.

### 8.3 Counterparty ledger (LED)

| ID | Requirement | Priority |
| --- | --- | --- |
| LED-01 | Keep one encrypted ledger per counterparty. | P0 |
| LED-02 | Treat a group as a counterparty. Track each sender inside it. A private message from a group member links to the group's case. | P0 |
| LED-03 | Delete raw message text after 30 days by default. The user can choose 7, 30 or 90 days. | P0 |
| LED-04 | Let the user view any ledger, delete one ledger, or delete everything. | P0 |
| LED-05 | "Mark as trusted" stops tracking that counterparty and deletes its stored text. | P0 |
| LED-06 | Keep a case open while its last event is under 90 days old. Keep tags and stage for the life of the case, even after raw text is deleted. | P1 |
| LED-07 | When the user starts an evidence pack, exempt that ledger from automatic deletion until the user releases it. | P1 |

### 8.4 Tactic reader (TAC)

| ID | Requirement | Priority |
| --- | --- | --- |
| TAC-01 | Tag each stranger message with zero or more of the 14 tactics, each with a confidence value, in a fixed JSON format. | P0 |
| TAC-02 | Handle English, Hindi and Hinglish. | P0 |
| TAC-03 | Use up to the last six messages from the same counterparty as context. | P0 |
| TAC-04 | Reject any output that does not match the format. Retry once, then fall back to keyword rules. | P0 |
| TAC-05 | Run on the phone with no network connection. | P0 |
| TAC-06 | Skip plainly transactional notifications, such as one-time passwords and delivery updates, before calling the model. | P0 |
| TAC-07 | Handle Marathi, Telugu, Tamil, Bengali, Kannada and Gujarati, including mixed-language text. | P1 |
| TAC-08 | Queue work when the phone is hot or the battery is under 15%. Never drop events. | P1 |
| TAC-09 | Three device tiers: full model on flagship phones, small model on mid-range phones, rules only on low-end phones. | P1 |

### 8.5 Progression engine (ENG)

| ID | Requirement | Priority |
| --- | --- | --- |
| ENG-01 | For each counterparty, hold a probability for each scam family and for "benign", a current stage, and a risk score from 0 to 100. | P0 |
| ENG-02 | Update on every event in under 50 ms. | P0 |
| ENG-03 | Support two families: fake investment and digital arrest. | P0 |
| ENG-04 | Be deterministic and auditable. Store which evidence caused each change in risk. | P0 |
| ENG-05 | Use two thresholds read from the script pack. Watch: a case opens, default 40. Warn: a full-screen warning is allowed, default 60. | P0 |
| ENG-06 | Apply hard rules that allow a warning whatever the score. Example: investment tactics plus a payment to a handle that fails the `@valid` check. | P0 |
| ENG-07 | Link a tripwire moment to a case in one of two ways. Entity match: the handle, link or app appears in the ledger. Time match: the action happens within 30 minutes of an event from a counterparty above the watch threshold. | P0 |
| ENG-08 | Add three families: bank or KYC impersonation with remote access, task and job scams, courier scams. | P1 |
| ENG-09 | Let risk fall slowly when no new events arrive. The stage never moves backward. | P1 |
| ENG-10 | Tune thresholds per user from feedback, within fixed limits. | P1 |
| ENG-11 | Add further families: loan apps, sextortion, fake customer care, romance and crypto. | P2 |

### 8.6 Intervention broker (INT)

| ID | Requirement | Priority |
| --- | --- | --- |
| INT-01 | Offer three levels: quiet notification, full-screen warning, follow-up after the action. | P0 |
| INT-02 | Show a full-screen warning only at a tripwire moment, and only if risk is at or above the warn threshold or a hard rule fires. | P0 |
| INT-03 | A full-screen warning shows: the scam type in plain words, up to three reasons, the timeline, the grounded-check result, and the action buttons. | P0 |
| INT-04 | Show the warning within 300 ms of detecting the action. Use the stored case state. Do not call the language model on this path. | P0 |
| INT-05 | "Proceed anyway" must always be possible and must need a three-second hold. | P0 |
| INT-06 | Limit frequency: one quiet notification per counterparty per stage, one full-screen warning per tripwire moment. | P0 |
| INT-07 | Never claim certainty. Use "matches a known scam pattern", not "this is a scam". | P0 |
| INT-08 | After "proceed anyway", pin the "I already paid" shortcut for 24 hours and check in after one hour. | P1 |
| INT-09 | If the install was not caught in time, warn right after and offer a shortcut to uninstall. | P1 |
| INT-10 | During a call that matches the digital-arrest pattern, show a notice on top of the call screen. | P1 |

### 8.7 Grounded checks (CHK)

| ID | Requirement | Priority |
| --- | --- | --- |
| CHK-01 | `@valid` check: if the case is an investment scam or the counterparty claims to be a registered broker, and the payee handle is not in the `@valid` format, report a fail. | P0 |
| CHK-02 | Offer a link to SEBI Check so the user can verify the payee. | P0 |
| CHK-03 | Install-source check: report a fail if the app came from a link and not an app store. | P0 |
| CHK-04 | Report each check as pass, fail or unknown, with one plain sentence. Never show unknown as pass. | P0 |
| CHK-05 | Known-bad check: the handle, number or link is on the list inside the script pack. | P1 |
| CHK-06 | Name-mismatch check: the payee name on the handle differs from who the counterparty claims to be. | P1 |
| CHK-07 | Lookalike check: the app name or web address imitates a known broker or bank. | P1 |
| CHK-08 | Fraud-risk lookup for phone numbers through a partner. The government's FRI data is shared with banks and payment apps, not independent apps. | P2 |

### 8.8 Explanation and voice (EXP)

| ID | Requirement | Priority |
| --- | --- | --- |
| EXP-01 | Build each explanation from fixed templates filled with case evidence, in the user's language. | P0 |
| EXP-02 | Speak the explanation aloud using the phone's built-in voice. | P0 |
| EXP-03 | Keep explanations to short sentences, no jargon, and at most three reasons. | P0 |
| EXP-04 | Offer a replay button. Speak even in silent mode unless the user has turned speech off. | P1 |
| EXP-05 | Use an on-device Indian-language voice (Sarvam Edge) for natural speech. | P1 |
| EXP-06 | Let the language model rephrase the template naturally. It may only use facts already in the case. | P1 |

### 8.9 Trusted ally (ALY)

| ID | Requirement | Priority |
| --- | --- | --- |
| ALY-01 | Let the user name up to three allies. | P1 |
| ALY-02 | Alert allies when: a full-screen warning is shown, the user proceeds anyway, the user taps "I already paid", or protection is turned off during an active case. | P1 |
| ALY-03 | An alert carries the scam type, stage and time. It carries no message content. | P1 |
| ALY-04 | Deliver by push notification if the ally has the app, otherwise by SMS. | P1 |
| ALY-05 | Show the protected user every alert that was sent. Let them remove an ally at any time. | P1 |
| ALY-06 | One-tap calling in both directions. | P1 |
| ALY-07 | Let an ally send a "please call me before you pay" request that appears on the protected phone. No remote control. | P2 |

### 8.10 Evidence pack and recovery (EVD)

| ID | Requirement | Priority |
| --- | --- | --- |
| EVD-01 | Build a PDF with: the user's details, a dated timeline, the counterparty's numbers, handles, group names and links, the app's name and install source, the transaction amount, time, reference and payee, and quoted messages. | P0 |
| EVD-02 | Show a "Call 1930" button and a link to cybercrime.gov.in. | P0 |
| EVD-03 | Show a golden-hour countdown from the payment time. | P0 |
| EVD-04 | Build the pack with no network connection. | P0 |
| EVD-05 | Fill transaction details from the payment SMS. | P1 |
| EVD-06 | Share the pack through any app. Provide a short script to read out on the 1930 call. | P1 |
| EVD-07 | Show a next-steps checklist: call the bank's fraud line, block the handle, keep all chats, pay no "recovery fee". | P1 |
| EVD-08 | Export the complaint in the field order of the national portal, as far as the portal allows. | P2 |

### 8.11 App screens (UI)

| ID | Requirement | Priority |
| --- | --- | --- |
| UI-01 | Home screen: protection status, number of active cases, "I already paid" button. | P0 |
| UI-02 | Case list and counterparty timeline: events in order with app icons, tactics in plain words, and a stage bar. | P0 |
| UI-03 | Settings screen. | P0 |
| UI-04 | A permanent "Tripwire is protecting you" notification, which Android requires for a background service. | P0 |
| UI-05 | Manual check: the user shares a message, link, screenshot or QR code to Tripwire and gets a verdict. | P1 |
| UI-06 | Learn section: a short explainer for each scam family, in the user's language. | P1 |

### 8.12 Feedback and learning (FBK)

| ID | Requirement | Priority |
| --- | --- | --- |
| FBK-01 | After every intervention, ask: "Was this a scam?" with answers yes, no, not sure. | P0 |
| FBK-02 | Use the answers to tune that user's thresholds. | P1 |
| FBK-03 | Opt-in pattern sharing: send the tactic sequence, family, stage and outcome, with all identifiers and message text removed. | P1 |
| FBK-04 | Show the user exactly what will be shared before anything is sent. | P1 |
| FBK-05 | Improve the shared model from many phones without collecting raw messages (federated learning). | P2 |

### 8.13 Updates (UPD)

| ID | Requirement | Priority |
| --- | --- | --- |
| UPD-01 | Accept only script packs with a valid signature. Reject and keep the old pack otherwise. | P0 |
| UPD-02 | Check for a new script pack weekly. Target size under 1 MB. | P1 |
| UPD-03 | Deliver model updates separately, over Wi-Fi only. | P1 |
| UPD-04 | Roll back to the previous pack if error reports rise after an update. | P1 |

### 8.14 Settings and privacy controls (SET)

| ID | Requirement | Priority |
| --- | --- | --- |
| SET-01 | Turn each signal collector on or off. | P0 |
| SET-02 | Delete all data in one action. | P0 |
| SET-03 | Choose the retention period. | P0 |
| SET-04 | Pause protection for one hour or until tomorrow, with a reminder when it resumes. | P0 |
| SET-05 | Export all stored data in a readable file. | P1 |
| SET-06 | Optional PIN on settings, so a caller cannot easily talk the user into turning protection off. | P1 |
| SET-07 | Manage the trusted list of counterparties. | P1 |

### 8.15 Family plan (FAM)

| ID | Requirement | Priority |
| --- | --- | --- |
| FAM-01 | One subscription covers up to five phones. | P1 |
| FAM-02 | Ally view: each protected person, whether protection is on, and the time of the last alert. | P1 |
| FAM-03 | The ally view never shows message content or counterparty identities. | P1 |

### 8.16 Partner integrations (PAR)

| ID | Requirement | Priority |
| --- | --- | --- |
| PAR-01 | Phone-maker build: system-level access to notifications, true pause of installs and payment links, and survival of background limits. | P2 |
| PAR-02 | Payment-app and broker SDK: the app asks Tripwire "is there an active risk for this payee right now?" and gets a risk level and reason codes. No chat content crosses. | P2 |
| PAR-03 | Take in partner fraud signals, including FRI labels, as grounded checks. | P2 |
| PAR-04 | Call protection where the platform allows audio access: on-device transcription of the call and tactic tagging of the transcript. | P2 |
| PAR-05 | Detection of AI-cloned voices on calls. | P2 |
| PAR-06 | Insurer link: with consent, confirm that protection is active, for a premium discount. | P2 |

## 9. Scam families and script specification

Every scam family is modelled as the same six stages. Tripwire warns at stage 4 and beyond, where the user is asked to do something irreversible.

### 9.1 The six stages

| # | Stage | What the scammer is doing | Base risk |
| --- | --- | --- | --- |
| 1 | Contact | Reaches the victim uninvited | 5 |
| 2 | Hook | Offers a reward or creates a fear | 20 |
| 3 | Grooming | Builds trust or isolates the victim, often moving to a private channel | 40 |
| 4 | Commitment | Gets a first small action: install an app, share the screen, make a small payment | 60 |
| 5 | Extraction | Asks for the real money | 80 |
| 6 | Lock-in | Blocks withdrawal or escalates threats to extract more | 90 |

Base risk values are starting points. Real values come from tuning on the test set (section 16). Evidence strength adds up to 15 points. With these defaults, a case opens at Grooming and a full-screen warning is allowed from Commitment.

### 9.2 Families and priorities

| Family | Priority | Usual channels | Tripwire moments |
| --- | --- | --- | --- |
| Fake investment or trading app | P0 | WhatsApp or Telegram group, then private chat, then a sideloaded app, then UPI | App install, payment |
| Digital arrest and authority impersonation | P0 | Phone call, then WhatsApp video call, then UPI or bank transfer | Screen share, payment |
| Bank, KYC or utility impersonation with remote access | P1 | SMS or call, then a remote-access app | App install, screen share, payment |
| Task and job scam | P1 | WhatsApp or Telegram, small payouts first, then "prepaid tasks" | Payment |
| Courier or parcel scam | P1 | Call or SMS, often leading into digital arrest | Screen share, payment |
| Loan app and advance-fee | P2 | SMS or social media, then a sideloaded app | App install, payment |
| Sextortion | P2 | Video call from a stranger, then threats | Payment |
| Fake customer care | P2 | The user calls a fake support number found online | Screen share, payment |
| Romance and crypto | P2 | Dating or social apps, weeks of chat, then an "investment" | Payment |

### 9.3 Script: fake investment

| Stage | What happens | What Tripwire sees | Tripwire's response |
| --- | --- | --- | --- |
| Contact | Victim is added to a group by an unknown number, or gets an unprompted message | Group-add event; sender not in contacts | Opens a ledger |
| Hook | Profit screenshots, free tips, promised returns | Tags: guaranteed returns, fake social proof, exclusivity | None |
| Grooming | An "analyst" or "assistant" messages privately and offers a VIP or pre-IPO account | Tags: move to private chat, exclusivity, urgency; private message linked to the group | Quiet notification |
| Commitment | Victim is told to install a trading app from a link and register | App-file link in chat; app installed from outside an app store | Full-screen install warning |
| Extraction | Victim is told to deposit, often to changing account names | Tag: payment request; UPI handle in chat; payment app opened; `@valid` check fails | Full-screen payment warning; ally alert |
| Lock-in | Withdrawal is blocked; a "tax" or "fee" is demanded | Tag: fee to withdraw | Full-screen warning; "I already paid" flow offered |

### 9.4 Script: digital arrest

| Stage | What happens | What Tripwire sees | Tripwire's response |
| --- | --- | --- | --- |
| Contact | A call or message claims a parcel, SIM or bank account is linked to a crime | Call or message from a non-contact | Opens a ledger |
| Hook | Victim is "transferred" to a fake police or agency officer | Tags: authority impersonation, threat of legal action | None, or quiet notification if messages are strong |
| Grooming | Victim is moved to a video call, told to stay on camera and tell no one; fake warrants are sent | Long video call from a non-contact; tags: secrecy, urgency; document images | In-call notice: police do not arrest by video call |
| Commitment | Victim is asked to share the screen or give bank details "for verification" | Screen share or remote-access app during or after the call | Full-screen screen-share warning |
| Extraction | Victim is told to move money to a "safe" or "RBI verification" account | Tag: payment request; payment app opened during or soon after the call | Full-screen payment warning; ally alert |
| Lock-in | More transfers are demanded over days under threat | Repeated calls; repeated payment tags | Warning repeated; "I already paid" flow offered |

### 9.5 Benign lookalikes

The engine must not flag these. Each is a required case in the benign test set.

| Lookalike | Why it resembles a scam | What separates it |
| --- | --- | --- |
| A real broker's relationship manager | Stranger, talks about investing | No install link outside app stores; payment goes through the broker's app or an `@valid` handle |
| A real recruiter | Stranger, offers a job | No payment requested from the candidate |
| Bank and delivery alerts | Unknown sender, urgency | Transactional format; no request to install, share or pay a person |
| A new customer of a small business owner | Stranger, talks about payment | The stranger pays the user, not the reverse |
| A genuine investing group among acquaintances | Returns are discussed | No guarantee language, no private upsell, no install link |
| A real police or court notice | Authority | Arrives by post or in person; no video call, no demand to transfer money |

### 9.6 Script pack format

Each family is defined in data, not code, so new scams can be added by update. A family definition holds:

- family ID, name and plain-language description in each supported language
- the stages, and for each stage the tags and events that count as evidence, with weights
- the minimum evidence needed to enter each stage
- hard rules
- which grounded checks apply
- the warning templates for each tripwire moment, in each language
- the watch and warn thresholds

## 10. AI and model specification

Tripwire uses a language model only to read messages. The decision to interrupt is made by a small, rule-based tracker, so it is fast, predictable and explainable.

### 10.1 Components

| Component | Job | Approach | Priority |
| --- | --- | --- | --- |
| Entity extractor | Pull out links, UPI handles, numbers, amounts | Fixed pattern rules. No model. | P0 |
| Tactic reader | Label each message with persuasion tactics | Gemma 4 E2B, fine-tuned and compressed to 4-bit, running on the phone's NPU. Gemma 3 270M for weaker phones. | P0 |
| Progression engine | Decide family, stage and risk | Probabilistic state machine. A few kilobytes. No neural network. | P0 |
| Explanation writer | Produce the warning text | Templates filled from case evidence. Optional rephrasing by the tactic reader's model. | P0, rephrasing P1 |
| Speech output | Read the warning aloud | Android's built-in voice first. Sarvam Edge speech synthesis later. | P0, Sarvam P1 |
| Translation | Support languages the tactic reader handles poorly | Sarvam Edge translation to Hindi or English before tagging | P1 |
| Vision | Read profit screenshots and fake documents | A small on-device vision-language model | P2 |
| Speech recognition | Transcribe voice notes and, where allowed, calls | Sarvam Edge speech recognition | P2 |

**Model facts from the research report**

- Gemma 4 was released in April 2026 under the Apache 2.0 licence. Its edge versions have about 2 billion and 4 billion parameters and handle text and audio.
- Sarvam Edge speech recognition covers 10 Indian languages plus English in about 294 MB. Its speech synthesis is about 60 MB and translation about 334 MB.
- The iQOO 15 uses the Snapdragon 8 Elite Gen 5. Google measured a 0.5-billion-parameter vision model at over 100 tokens per second on that chip's NPU.

### 10.2 Why a language model is needed

Keyword rules catch "guaranteed 300% returns" in English. They miss the same tactic in mixed-language text with no fixed keywords, for example: "sir aapka allotment confirm hai, bas aaj hi karna padega, kisi ko mat batana". That sentence carries exclusivity, urgency and secrecy without one stock phrase. Scammers also rephrase constantly. A language model recognises the tactic, not the wording.

### 10.3 Why the engine is not a language model

- The step that interrupts a payment must behave the same way every time.
- Every warning must be traceable to specific evidence.
- It must run in milliseconds and cost no battery.
- It must keep working if the language model is slow, queued or unavailable.

### 10.4 Tactic reader: input and output

**Input:** the new message, up to six earlier messages from the same counterparty, the app name, and whether it is a group or private chat.

**Output:** a fixed JSON object with the tactics found, a confidence for each, and the language detected. Appendix B shows an example. Generation is constrained so only valid JSON with known tag names can be produced.

### 10.5 Training data

No public dataset of Indian multi-app scam conversations exists, so one must be built.

| Source | Use |
| --- | --- |
| Published case descriptions from I4C, the Enforcement Directorate, SEBI and state police | Ground truth for scripts and wording |
| Screenshots victims have posted publicly, with personal details removed | Real phrasing |
| Variations written by a large model offline, in Hindi, Hinglish, Marathi, Telugu and Tamil | Volume and language coverage |
| Ordinary stranger conversations: recruiters, deliveries, brokers, customers, bank alerts | Benign examples |
| Opt-in confirmed cases from users, identifiers removed | Ongoing improvement |

Rules for the dataset:

- Initial targets: 5,000 labelled scam messages and 10,000 benign messages.
- A person reviews a sample of every generated batch.
- Full conversations are split into training and test sets by conversation, never by message.
- The dataset is labelled as partly synthetic wherever results are reported.
- No user data is used without explicit opt-in.

### 10.6 Fine-tuning and packaging

1. Fine-tune the base model with low-rank adapters on a laptop or cloud GPU.
2. Merge, compress to 4-bit, and convert to the on-device format (LiteRT or the Qualcomm runtime).
3. Test on the target phone for speed, memory and heat.
4. Ship as a signed model file, downloaded on first run.

### 10.7 Quality and speed targets

| Measure | Target |
| --- | --- |
| Per-tag accuracy (macro F1) on the held-out set | 0.85 or higher |
| Benign messages given any high-risk tag | Under 3% |
| Correct family identified by the Extraction stage | 90% of scripted scams |
| Time to tag one message, flagship phone | Under 500 ms |
| Time to tag one message, mid-range phone | Under 2 s |
| Engine update time | Under 50 ms |
| Model memory while loaded | Under 3 GB on flagship; small model under 500 MB |

A research benchmark gives a reference point. On the ConScamBench-278 scam conversations, an agent first flagged scams at a median of turn 6 ([arXiv, July 2026](https://arxiv.org/html/2607.11707)).

### 10.8 Protecting the model from the scammer

A scam message is hostile input. It may contain text meant to fool the model, such as "ignore earlier instructions".

- Message text is always passed as data, never as instructions.
- Output is restricted to the fixed tag list, so the model cannot be made to say anything else.
- Keyword rules for the highest-risk tactics run alongside the model. Either can raise a tag.
- Hard rules in the engine depend on system events and grounded checks, which a message cannot fake.

### 10.9 How the system improves

- **Script packs** add new families, wording and thresholds without an app update.
- **Per-user tuning** adjusts thresholds from each user's own feedback.
- **Opt-in pattern sharing** grows the library of real scam sequences.
- **Federated learning**, later, improves the shared model without collecting messages.

## 11. System architecture

Tripwire is one Android app with a five-step pipeline on the phone and a very small cloud service that never receives message text.

&#91;embedded content: system architecture · five on-device steps, thin cloud\]

Signals flow down the left column. The progression engine is the decision point; it draws on the ledger and on grounded checks, and the broker turns its verdict into a warning, an ally alert or an evidence pack.

### 11.1 What happens when a message arrives

1. The notification reader receives the notification and checks whether the sender is a saved contact. If so, it stops.
2. The normaliser builds an event and runs the entity rules.
3. The event is written to the counterparty's ledger.
4. The tactic reader tags the message. This may be queued for a few seconds.
5. The progression engine updates family, stage and risk, and stores them in the ledger.
6. If risk has just crossed the watch threshold, the broker posts a quiet notification.

### 11.2 What happens at a tripwire moment

1. A collector reports an install, a screen share, a payment app opening or a UPI link.
2. The engine looks for a linked case by entity match, then by time match.
3. Grounded checks run on the payee handle or the app.
4. If risk is at or above the warn threshold, or a hard rule fires, the broker shows the full-screen warning.
5. The explanation is filled from stored evidence and spoken.
6. The user's choice is logged. The ally is alerted where the rules in 8.9 say so.

No language-model call sits on this path. It uses only state already stored.

### 11.3 What crosses the network

| Data | Direction | When | Contains message text |
| --- | --- | --- | --- |
| Script pack | Into the phone | Weekly | No |
| Model files | Into the phone | At setup and on model updates | No |
| Ally alert | Out | When a serious warning fires | No |
| Anonymous scam pattern | Out | Only if the user opts in | No |
| Usage counters | Out | Only if the user opts in | No |
| Evidence pack | Out | Only when the user chooses to share it | Yes, by the user's own action |

### 11.4 Technology choices

| Layer | Choice |
| --- | --- |
| App | Kotlin with Jetpack Compose |
| Background work | A foreground service, Android's notification listener service, and WorkManager for updates |
| Optional on-screen reading | Android's accessibility service |
| Stranger check | Android contacts provider |
| Payment-app and foreground detection | Usage stats, plus an intent filter for UPI links |
| Install detection | Package-added broadcasts and the install-source API |
| Model runtime | LiteRT or MediaPipe LLM Inference; or Qualcomm AI Hub and Genie on Snapdragon |
| Speech | Android TextToSpeech first; Sarvam Edge later |
| Storage | SQLite encrypted with SQLCipher; keys in the Android Keystore |
| QR decoding | ML Kit barcode scanning or ZXing |
| PDF | Android's built-in PDF writer |
| Ally alerts | Firebase Cloud Messaging; SMS as fallback |
| Update server | Signed static files on a content delivery network |
| Pattern intake | A small API that accepts only the anonymous pattern format |
| Model training | PyTorch with low-rank adapters, on a laptop or cloud GPU |

### 11.5 Staying alive in the background

Some Android phone makers stop background apps aggressively. Tripwire must run as a foreground service with a visible notification, ask for exemption from battery optimisation, restart after reboot, and tell the user on the home screen if it was stopped.

## 12. Data model

All user data lives in one encrypted database on the phone, in ten tables.

### 12.1 Tables

| Table | One row is | Main fields |
| --- | --- | --- |
| `counterparty` | A stranger or group | id, display name, type (person or group), identifiers (phone numbers, usernames, UPI handles, links), apps seen on, first seen, last seen, trusted flag |
| `counterparty_link` | A link between two counterparties | the two ids, reason (same number, same handle, group member, handoff message), confidence |
| `event` | One observation | id, counterparty id, app, type, text, extracted entities, time, source collector, text-deleted flag |
| `tactic_tag` | One tactic found in one event | event id, tag name, confidence, source (model or rule) |
| `case_state` | The engine's current view of one counterparty | counterparty id, family probabilities, stage, risk, opened at, last changed, status (watching, warned, closed, trusted) |
| `case_evidence` | One reason behind a risk change | case id, event id or check id, effect on stage or risk, time |
| `check_result` | One grounded check | case id, check type, subject (handle, app, link), result (pass, fail, unknown), explanation, time |
| `intervention` | One thing shown to the user | case id, level, tripwire moment type, reasons shown, user choice, user feedback, time |
| `evidence_pack` | One complaint pack | case id, user-entered details, transaction details, file location, created at, hold flag |
| `ally` | One trusted ally | name, phone number, has-app flag, alerts sent |

Settings and the installed script pack version are kept in a small key-value store.

### 12.2 Event types

`message`, `group_added`, `call_started`, `call_ended`, `app_installed`, `install_screen_opened`, `payment_app_opened`, `upi_link_opened`, `screen_share_started`, `remote_app_opened`, `payment_sms`, `user_reply` (only with on-screen reading).

### 12.3 Retention

| Data | Kept for |
| --- | --- |
| Text from saved contacts | Never stored |
| Raw message text from strangers | 30 days by default; user can choose 7, 30 or 90 |
| Tags, stage and risk | While the case is open: until 90 days after its last event |
| Counterparties that never reached the watch threshold | Deleted with their text |
| Evidence packs and ledgers on hold | Until the user deletes them |
| Intervention history | 12 months, without message text |

### 12.4 Anonymous pattern format

This is the only user-derived data that can leave the phone, and only with opt-in. It holds:

- scam family and final stage
- the ordered list of tactic tags and event types, with time gaps rounded to hours
- which grounded checks failed
- the outcome: stopped, proceeded, confirmed scam, confirmed genuine
- language and app names

It never holds message text, names, phone numbers, handles, links, amounts or a device identifier.

## 13. Screens and UX requirements

The app has 11 screens. The full-screen warning is the product; every other screen supports it.

### 13.1 Screen list

| Screen | Purpose | Must contain |
| --- | --- | --- |
| Welcome | Explain the product in three sentences | What it does, that chats stay on the phone, a "Start" button |
| Disclosure | Legal and honest account of what is read | Each data type, why, where it is kept, "I agree" and "No thanks" |
| Permissions | Grant access step by step | One permission per step, its reason, what stops working without it |
| Ally setup | Choose a trusted person | Contact picker, what the ally will and will not see, skip option |
| Home | Show status at a glance | Protection on or off, any protections missing, active cases, "I already paid" |
| Case list | Show counterparties being watched | Name or number, apps, stage, risk level in words |
| Counterparty timeline | Show why a case exists | Events in order with app icons, tactics in plain words, stage bar, "Mark as trusted", "Delete" |
| Full-screen warning | Interrupt at a tripwire moment | See 13.2 |
| "I already paid" flow | Get a complaint filed fast | Case picker, amount and time, golden-hour countdown, "Call 1930", pack preview, next steps |
| Ally alert view | Let the ally act | Who, scam type, stage, time, "Call now", "Mark handled" |
| Settings | Control and privacy | Collectors, language, speech, retention, pause, delete all, allies, trusted list, data sharing |

### 13.2 Full-screen warning layout

From the top of the screen to the bottom:

1. **Headline** in the user's language, one line. Example: "Stop. This payment matches a known investment scam."
2. **Up to three reasons**, each one sentence, each tied to something that happened.
3. **The timeline**: a short strip of the key events with app icons and dates.
4. **The grounded check**, in a box. Example: "This payment address is not a SEBI `@valid` address."
5. **Primary button**, large: "Don't pay" or "Don't install" or "Stop sharing".
6. **Second button**: "Call \[ally's name\] first".
7. **Third option**, text link: "Verify on SEBI Check" where it applies.
8. **Proceed option**, small, at the bottom: "Pay anyway", press and hold for three seconds.
9. **Speaker icon** to replay the spoken explanation.

### 13.3 Warning wording rules

- Say what was observed, not what the user did wrong. "A stranger sent you this app" not "You are installing a dangerous app".
- Never say "this is a scam". Say "this matches a known scam pattern".
- Give a checkable fact wherever one exists.
- Offer a way to save face: "Call someone you trust first" costs nothing and accuses no one.
- No technical terms. "Payment address" not "VPA". "App from a link" not "sideloaded APK".
- Sentences under 15 words when spoken.
- State the specific loss risk: "Money sent by UPI usually cannot be recalled."
- For digital arrest, state the fact that breaks the script: "Police never arrest anyone on a video call."

### 13.4 Accessibility and inclusion

- All warning text at a minimum of 18 sp, and it scales with the system font size.
- Every warning is spoken as well as shown.
- Buttons at least 56 dp tall, with a text label, never an icon alone.
- Colour is never the only signal. Risk levels carry a word.
- The whole app works with Android's screen reader.
- Every string exists in every supported language. No English fallback inside a warning.
- No typing is needed to respond to a warning.

### 13.5 Tone

Tripwire speaks like a calm, respectful relative. It does not alarm, shame or lecture. Quiet notifications are neutral. Full-screen warnings are firm and brief.

## 14. Non-functional requirements

Tripwire must be fast at the moment of interruption, light on the battery the rest of the time, and fully working with no network. All figures are targets.

### 14.1 Performance

| Measure | Target |
| --- | --- |
| Full-screen warning on screen after the risky action is detected | Under 300 ms |
| Engine update per event | Under 50 ms |
| Message tagged after arrival, flagship phone | Under 500 ms of model time; under 5 s end to end when queued |
| Evidence pack built | Under 2 minutes including user input; under 10 s of processing |
| App cold start to home screen | Under 2 s |

### 14.2 Battery, heat, memory and storage

| Measure | Target |
| --- | --- |
| Extra battery use per day in normal use | Under 3% |
| Model loaded in memory | Only while tagging; released after 60 s idle |
| Tagging when the phone is hot or the battery is under 15% | Queued, not dropped |
| App size without models | Under 40 MB |
| Model download, flagship tier | Stated to the user before download; Wi-Fi by default |
| Database size | Under 100 MB in normal use |

The exact size of the compressed Gemma model must be measured on the target phone. It is an open item in section 23.

### 14.3 Device support

| Tier | Phones | What runs |
| --- | --- | --- |
| A | Flagship phones with a recent NPU, 12 GB memory or more. Reference device: iQOO 15. | Full tactic reader, on-device Indian-language speech |
| B | Mid-range phones with 8 GB memory | Small tactic reader, built-in Android speech |
| C | Phones with under 8 GB memory | Keyword rules, engine, grounded checks, built-in speech |

- Minimum Android version: Android 10. To be confirmed against the features used.
- The engine, grounded checks, warnings and evidence pack must work on all three tiers.

### 14.4 Offline behaviour

- Detection, warnings, speech and the evidence pack must work in airplane mode.
- Only three things need a network: downloads and updates, ally alerts, and opening SEBI Check or the complaint portal.
- If an ally alert cannot be sent, it is queued and sent when the network returns. The user is told it is pending.

### 14.5 Reliability

- The background service restarts after a reboot and after being stopped by the system.
- If any required permission is lost, the home screen and a notification say so within one minute of the next app wake.
- Crash-free sessions: 99.5% or higher.
- A failure in the tactic reader must never stop the engine, the checks or the warnings.
- No event is lost if the app is stopped mid-processing. Events are written to the ledger before tagging.

### 14.6 Languages

| Stage | Interface and warnings | Message understanding |
| --- | --- | --- |
| P0 | English, Hindi | English, Hindi, Hinglish |
| P1 | Adds Marathi, Telugu, Tamil, Bengali, Kannada, Gujarati | Adds the same, including mixed-language text |
| P2 | Adds Malayalam, Punjabi, Odia and others on demand | Adds the same |

### 14.7 Maintainability

- Scam families, thresholds, known-bad lists and warning templates live in the script pack, not in code.
- The list of messaging apps and payment apps is data, updated through the script pack.
- Each signal collector is a separate module that can be turned off without affecting the others.

## 15. Privacy, security and compliance

Tripwire reads private messages, so its privacy rules are product requirements, not policy text. Breaking any rule in 15.1 is a release blocker.

### 15.1 Privacy rules

1. Message text never leaves the phone, except inside an evidence pack the user chooses to share.
2. Messages from saved contacts are never stored.
3. The ally never sees message content or who the counterparty is.
4. Nothing is shared for product improvement without opt-in, and the user sees it first.
5. The user can see, export and delete everything Tripwire holds.
6. Tripwire never runs hidden. A permanent notification shows it is active.
7. The person whose phone it is must give consent themselves, even when someone else installs it.
8. No advertising, and no sale of data.

### 15.2 Permissions

| Permission | Why it is needed | Needed? | If refused |
| --- | --- | --- | --- |
| Notification access | Read messages from strangers | Required | No detection |
| Show notifications | Show notices and the service notification | Required | No notices |
| Contacts | Tell strangers from known people | Strongly advised | Only senders shown as raw phone numbers count as strangers |
| Display over other apps | Put the warning on top of the install or payment screen | Advised | Falls back to an urgent full-screen notification |
| Usage access | Know when a payment app opens | Advised | Payment warnings only through UPI links and the manual check |
| On-screen reading (accessibility) | Read open chats and see the install screen | Optional | Misses messages that made no notification; install warning comes after the install |
| Phone state | Know a call is in progress | Optional | No link between calls and cases for cellular calls |
| Battery-optimisation exemption | Keep running in the background | Advised | The system may stop Tripwire |
| See installed apps and their source | Detect apps installed from links | Required for install checks | No install warnings |

**Permissions Tripwire will not request.** Google Play restricts SMS and call-log permissions to a narrow set of app types. Tripwire reads SMS content from the SMS notification instead, and does not read the call log.

### 15.3 Platform rules that constrain the design

| Rule | Effect | Response |
| --- | --- | --- |
| Since Android 15, notifications containing one-time passwords are hidden from third-party apps | Tripwire cannot see those notifications | Acceptable. Tripwire does not need one-time passwords. |
| Android 13 and later block sideloaded apps from notification and accessibility access | A Tripwire installed from a file cannot work | Distribute through Google Play only |
| Google Play requires a declaration and a prominent in-app disclosure for accessibility use, and security apps may not label themselves as accessibility tools | On-screen reading needs review and approval | Declare the fraud-prevention purpose; never set the accessibility-tool flag; keep the feature optional |
| Android 17's Advanced Protection Mode removes accessibility access from apps that are not accessibility tools | On-screen reading stops for users who turn that mode on | Core detection relies on notifications, not on-screen reading |
| Payment and banking apps may hide their screens and may react to overlays | Reading inside or drawing over them is unreliable | Detect the app opening, not its contents; show the warning before handing the UPI link on |
| Ordinary apps cannot record call audio | No call transcription | Left to phone-maker integration |
| System-signed apps can read sensitive notifications and gate installs | A phone-maker build is far more capable | Pursue phone-maker partnerships |

### 15.4 Security requirements

| Threat | Mitigation |
| --- | --- |
| A scammer on a call tells the victim to turn Tripwire off | Optional settings PIN; ally alert when protection is turned off during an active case; a plain warning before disabling |
| Another app tries to read Tripwire's data | Encrypted database; keys in the Android Keystore; data in app-private storage; no exported components that return data |
| A forged script pack or model | Signature check before use; pinned public key in the app |
| Tripwire used to spy on someone | Rules 3, 6 and 7 above; no remote access; no hidden mode; ally sees alerts only |
| A scam message tries to manipulate the model | Section 10.8 |
| A lost or stolen phone | Data is behind the device lock and encrypted |
| A fake "Tripwire" app imitates the brand | Play-only distribution; publish the official package name; in-app note that Tripwire never asks for money or passwords |
| Another app covers or imitates the warning | Warning shown from Tripwire's own verified window; brand mark the user saw during setup |

### 15.5 Law and regulation

These items need review by a lawyer before public launch. They are listed, not resolved.

- **Digital Personal Data Protection Act, 2023.** Notice in plain language, consent, purpose limits, the right to erase, and a named contact for complaints. Whether on-device processing makes the company a data fiduciary for that data needs a legal opinion.
- **Other people's messages.** Tripwire processes messages written by third parties. The legal basis for this needs review.
- **Children.** The DPDP Act sets stricter rules for users under 18. Decide whether to allow them at all.
- **Liability.** Terms must state that Tripwire is a decision aid, gives no guarantee, and is not financial or legal advice.
- **Claims.** Marketing must not promise that scams will be stopped.
- **Government marks.** Do not use SEBI, RBI, police or 1930 logos in a way that implies endorsement.
- **Evidence.** State that the evidence pack is an aid to filing a complaint, not certified evidence.

## 16. Testing and quality plan

Tripwire is tested by replaying whole scam conversations through the real pipeline and checking when it warns, and by replaying ordinary conversations and checking that it stays silent.

### 16.1 Test sets

| Set | Contents | Used to measure |
| --- | --- | --- |
| Scam scripts | Full multi-day conversations for each family, in each language, with install and payment events | Stage at first detection; warning at the right moment |
| Benign conversations | Recruiters, brokers, deliveries, customers, bank alerts, hobby and investing groups (section 9.5) | False notifications and false warnings |
| Tag set | Single messages with human-assigned tactic labels | Tactic reader accuracy |
| Adversarial set | Rephrased, misspelled, mixed-script, emoji-heavy and instruction-injecting messages | Robustness |
| Outside benchmark | ConScamBench-278 or similar public sets, where licences allow | Comparison with published work |

Test conversations are held out from training by whole conversation.

### 16.2 Test types

| Type | What it covers |
| --- | --- |
| Unit tests | Entity rules, engine transitions, hard rules, grounded checks, retention and deletion |
| Replay tests | A harness feeds recorded events into the pipeline at speed and compares the interventions with the expected ones |
| Model tests | Tag accuracy per language and per tactic; format validity; speed and memory on each device tier |
| Device tests | Real phones across makers and Android versions: background survival, permission flows, overlays over real messaging and payment apps |
| Live scenario tests | Two phones: one plays the scammer, one runs Tripwire. Covers each journey in section 7. |
| Usability tests | Sessions with people over 55 and with first-time investors, in their language: do they understand the warning and act on it? |
| Privacy tests | Network capture during use to confirm no message text leaves; database inspection; deletion checks |
| Security tests | Forged script packs, data-access attempts by another app, attempts to disable protection |
| Battery and heat tests | A day of realistic message volume on each tier |

### 16.3 Acceptance criteria for the first working version

- Both P0 families are detected before the payment request in at least 80% of scripted scams.
- The install warning and the payment warning fire in every live scenario test of the two P0 families.
- No full-screen warning fires on the benign set.
- Fewer than 2% of benign conversations produce a quiet notification.
- The full-screen warning appears in under 300 ms on the reference phone.
- Every journey in section 7 marked P0 passes in airplane mode.
- A network capture shows no message text leaving the phone.
- The evidence pack contains every field in EVD-01 for both families.

### 16.4 Acceptance criteria for public launch

- All P1 requirements pass their tests.
- Tag accuracy meets the targets in 10.7 for every P1 language.
- Usability: at least 8 of 10 test users over 55 stop the payment in the live scenario and can say why.
- Battery cost under 3% a day on the Tier B reference phone.
- Google Play accessibility declaration approved.
- Legal review in 15.5 complete.

### 16.5 Honest reporting

Results on partly synthetic data must be labelled as such. Detection claims are stated as "on our test set", never as real-world rates, until field data exists.

## 17. Analytics and telemetry

Tripwire measures itself with counts only, and only for users who opt in. It never logs message text, names, numbers, handles or links.

**Events counted**

| Event | Properties |
| --- | --- |
| Setup step completed | Step name, permission granted or refused |
| Case opened | Family, stage, language, apps involved (names only) |
| Quiet notification shown | Family, stage, tapped or not |
| Full-screen warning shown | Family, stage, tripwire moment type, grounded-check result |
| Warning outcome | Stopped, called ally, verified, proceeded |
| Feedback given | Scam, genuine, not sure |
| "I already paid" started and completed | Family, minutes since payment (rounded), pack built or not |
| Ally alert sent | Delivery method, opened or not |
| Protection state changed | Which collector, on or off, paused |
| Performance | Tagging time, warning delay, battery estimate, crash reports |

**How the success metrics in section 4 are computed**

- **Payment abandonment rate** = warnings with outcome "stopped" or "called ally", and no "I already paid" within 24 hours, divided by all payment warnings.
- **False full-screen warnings** = warnings with feedback "genuine", per active user per month.
- **Stage at first detection** comes from the test set, not from telemetry.
- **Retention** = users whose notification access is still granted 30 days after setup.

**Rules**

- Telemetry is off until the user opts in, and can be turned off at any time.
- No persistent device identifier. A random ID that resets on reinstall is used for counting.
- Small groups are not reported. Any figure covering fewer than 50 users is withheld.

## 18. Roadmap and release phases

Tripwire is built in five phases. Each phase starts only when the gate before it is passed. No dates are set; order and gates are what matter.

&#91;embedded content: roadmap · five phases, four gates\]

The filled diamonds are gates. The words under each one are the test that must pass before the next phase begins.

### Phase 1: Prototype

**Scope:** every P0 requirement in section 8. Two scam families, English and Hindi, notification reading, install and payment warnings, built-in speech, evidence pack, timeline.

**Build order inside this phase**

1. Notification reader, stranger check and ledger.
2. Entity rules.
3. Progression engine, fed by keyword tags first, so it works before the model does.
4. Install and payment collectors, the broker and the warning screen.
5. Grounded checks.
6. Tactic reader model, replacing keyword tags as the main source.
7. Evidence pack and "I already paid" flow.
8. Speech and Hindi.
9. Replay test harness and the first test sets.

**Gate:** the acceptance criteria in 16.3.

### Phase 2: Closed beta

**Scope:** the P1 detection and response features. On-screen reading, trusted ally, call and screen-share detection, cross-app linking, six more languages, Indian-language speech, feedback tuning, script-pack updates. Run with a few hundred invited families.

**Gate:** over 30 days, fewer than one false full-screen warning per user per month; 30-day retention at 60% or higher; no privacy rule broken.

### Phase 3: Public launch

**Scope:** Google Play release with the accessibility declaration approved, family plan, three more scam families, manual check, learn section, legal review complete.

**Gate:** the acceptance criteria in 16.4.

### Phase 4: Partners

**Scope:** the partner integrations in 8.16. A phone-maker build with system-level access, the payment-app and broker SDK, partner fraud signals, call protection where the platform allows.

**Gate:** the first partner is signed and a pilot has reported results.

### Phase 5: Scale

**Scope:** further scam families, image and voice-note understanding, shared learning across phones, cloned-voice detection, insurer links, and new markets: the Indian diaspora and Southeast Asia.

## 19. Business model and go-to-market

Tripwire earns mainly from companies, not consumers. Consumers rarely pay for security, and Google gives its protection away free.

### 19.1 Revenue lines

| Line | Customer | What they pay for | Pricing idea |
| --- | --- | --- | --- |
| Phone-maker licence | vivo/iQOO, HMD, Lava and others | A built-in "scam shield" to market in India | About ₹15 to ₹40 per device |
| Broker, bank and payment-app SDK | Regulated financial firms | A risk signal at payment time | Per active user, or per prevented incident |
| Insurer link | Cyber-fraud insurers | Lower claims from protected users | Referral fee |
| Family plan | Households | Ally alerts, evidence pack, up to five phones | About ₹49 to ₹99 a month |

All prices are hypotheses to test. A competing consumer app, ScamMukt, charges ₹50 a month, which suggests where the consumer ceiling sits.

### 19.2 Free and paid

| Feature | Free | Family plan |
| --- | --- | --- |
| Detection and full-screen warnings on one phone | Yes | Yes |
| Counterparty timeline | Yes | Yes |
| "Call 1930" and basic complaint steps | Yes | Yes |
| Trusted-ally alerts | No | Yes |
| Full evidence pack | No | Yes |
| Up to five phones and the ally view | No | Yes |

The safety-critical warning is never behind a paywall.

### 19.3 Market size

A bottom-up estimate, not a forecast. Assume 100 million Indian Android users aged 30 and over who use UPI. If phone-maker preloads reach 10 million devices at ₹25 each, that is about ₹25 crore a year. SDK and family-plan revenue add to this. It is a solid Indian business, and a large one only with expansion abroad. In the US alone the FBI counted $8.65 billion in investment-fraud losses in 2025.

### 19.4 Distribution

1. **Protector-led installs.** Adults install it for their parents. Marketing speaks to the adult child.
2. **Google Play** as the only consumer channel.
3. **Phone makers.** Preload or system integration. This also removes Android permission limits.
4. **Brokers and payment apps.** "Tripwire inside" through the SDK.
5. **Trust partners.** Bank branches, police cyber cells, resident associations and senior-citizen groups for awareness sessions.
6. **Earned media.** Every scam news story is a reason to install.

### 19.5 Why it can last

- **Data.** Each confirmed case adds a labelled scam sequence that competitors cannot buy.
- **Position.** Only an app on the user's side of the phone sees the whole sequence across apps.
- **Contracts.** Phone-maker and broker deals are slow to win and slow to lose.

An exclusive deal with one phone maker, or an acquisition, is a realistic and acceptable outcome.

## 20. Competitive landscape and positioning

Every competitor is limited to one channel or one moment. None follows one stranger across apps and acts at the payment.

| Product | What it does | Where it stops | Tripwire's edge |
| --- | --- | --- | --- |
| Google Scam Detection (Messages and Phone) | On-device AI checks conversations in Google Messages and calls from unknown numbers | Google's own apps only, not WhatsApp or Telegram. The AI message version is on new flagships in the US, Canada and UK. Call detection in India began on Pixel 9 and later. | Works across apps, in Indian languages, on any Android phone |
| Truecaller | Caller ID, spam blocking, AI voice detection, family protection, a paste-in scam checker | Built around calls. The checker needs the user to suspect something first. | Detects without being asked; follows the whole sequence |
| McAfee Scam Detector, Norton Genie | AI checks of texts, emails and links | One message at a time. An independent test found McAfee does not scan WhatsApp automatically. Weak in Indian languages. | Automatic, sequence-aware, Indian languages |
| Bitdefender Chat Protection | Scans links inside WhatsApp, Telegram and Messenger chats | Links only, not the conversation's tactics | Understands the conversation |
| PhonePe Protect, FRI, Navi Secure | Warn or block payments to numbers the government has flagged | Only numbers already reported. Sees the payment, not the conversation. | Catches fresh scammers from context |
| ScamMukt, Quick Heal AntiFraud.AI | Link, QR and web-address scanning; fraud call alerts | One item at a time | Tracks the whole sequence |
| Savi (US) | Screens texts, voicemails and calls with a live AI listener | Mostly cloud-based; US-focused | On-device privacy; Indian trust signals |

**Positioning statement.** For families worried about a parent being scammed, Tripwire is the scam protection that watches the whole con, not single messages, and steps in at the payment. Unlike caller-ID and link-scanning apps, it works across WhatsApp, Telegram, SMS and UPI, and it keeps every chat on the phone.

**The main competitive threat** is Google building cross-app tracking into Android itself. The response is in section 21.

## 21. Risks and mitigations

The two risks most likely to hurt Tripwire are Google closing the gap inside Android, and Android permission rules tightening further.

| Risk | Type | Mitigation |
| --- | --- | --- |
| Google adds cross-app or WhatsApp scam tracking to Android | Market | Lead on Indian languages, the evidence pack and broker SDKs; aim for a phone-maker partnership or acquisition |
| Android keeps restricting notification and accessibility access | Platform | Build the core on notification access; keep on-screen reading optional; pursue system-level integration with phone makers |
| Google Play rejects or removes the accessibility use | Distribution | Clear disclosure screen; correct declaration under fraud prevention; the app must fully work without that permission |
| False alarms make users ignore or uninstall | Product | Warn only at irreversible actions; show checkable evidence; tune per user; hold the launch gate at under one false warning a month |
| Missed scams damage trust | Product | Never promise protection; show what is and is not covered; weekly script updates |
| Messages that produce no notification are missed | Technical | On-screen reading; cross-app linking; hard rules that rely on install and payment events, not on chat text |
| The warning cannot be shown over a payment app | Technical | Warn when the payment app opens and when a UPI link is tapped, before the payment screen; phone-maker integration for true pausing |
| Scammers switch to images, voice notes and calls | Technical | Vision and speech models in later phases; engine rules based on behaviour, not wording |
| Scammers tell victims to disable Tripwire | Adoption | Settings PIN; ally alert on disabling during a case |
| Reading private chats feels intrusive | Adoption | Everything on the phone, encrypted, deleted on a schedule; contacts ignored; publish the detection logic |
| The phone stops the background service | Technical | Foreground service; battery exemption guidance per phone maker; status check on the home screen |
| The model is too slow or too large on common phones | Technical | Three device tiers; a small-model and a rules-only path |
| Weak or synthetic test data misleads the team | Technical | Hold-out by conversation; human review; label results as synthetic; add real opt-in cases in beta |
| Consumers will not pay | Business | Revenue plan rests on phone makers and financial firms; consumer plan is secondary |
| Legal exposure when a scam gets through | Legal | Decision-aid positioning; clear terms; no guarantee language anywhere |
| Misuse as a tool to monitor a family member | Ethical | No content to allies; visible notification; consent by the phone's owner; no hidden mode |
| A user is wrongly stopped from a genuine payment | Product | "Proceed anyway" always available; "Mark as trusted"; feedback lowers that user's sensitivity |

## 22. Demo script

The demo shows one scam unfolding across three apps on a phone in airplane mode, ending with a stopped payment. It runs about four and a half minutes.

**Setup:** two phones. Phone A runs Tripwire. Phone B plays the scammer and is not in Phone A's contacts. Prepare the messages, the sample app file and a UPI QR code in advance. Mirror Phone A to the screen.

| Time | What the presenter does | What the audience sees | Line to say |
| --- | --- | --- | --- |
| 0:00 to 0:30 | Shows one slide | "₹22,495 crore lost in 2025. 76% to fake investment schemes." | "Today's tools check messages. Scams are sequences." |
| 0:30 to 1:15 | Phone B adds Phone A to a group, posts a profit screenshot, then sends a private Hinglish message | On Phone A's timeline, the stage moves from Hook to Grooming and tags appear | "Notice the airplane icon. Nothing is leaving this phone." |
| 1:15 to 2:00 | On Phone A, taps the app link and starts the install | Full-screen warning, spoken in Hindi, naming the stranger and the promises | "It stayed silent until now, because this is the first step that is hard to undo." |
| 2:00 to 3:00 | Dismisses the warning, then scans the UPI QR code | Payment warning with the timeline from three apps and "Not a SEBI `@valid` address" | "No single app saw this. The payment app thought it was a normal payment." |
| 3:00 to 3:40 | Taps "I already paid" | The evidence pack with timeline, handle and reference; the ally's phone buzzes | "The first hour decides whether money is frozen. This takes two minutes." |
| 3:40 to 4:30 | Shows results and the business slide | Speed on the phone, test-set results labelled as test-set results, revenue lines | "Free for families. Phone makers and brokers pay." |

**The moment that matters** is at 2:00 to 3:00: one screen showing events from WhatsApp, the installer and UPI together, with a checkable fact.

**Fallbacks**

- If the live model is slow, use a recorded run of the same sequence through the replay harness on the same phone.
- If the overlay fails, show the warning from the notification.
- Keep a screen recording of the full run.

**Do not** claim a real-world detection rate, show any real person's chat, or use a real scam handle.

## 23. Assumptions and open questions

The design rests on seven assumptions that have not been tested on a device, and fifteen questions that need an answer before or during the build.

### 23.1 Assumptions

1. Notifications from WhatsApp and Telegram carry enough of each message to tag it.
2. A sender who is not a saved contact can be recognised from the notification and the contacts list.
3. The compressed Gemma model meets the speed and memory targets on the reference phone.
4. A warning can be drawn over the system installer and shown when a payment app opens.
5. UPI links and QR scans pass through Android's standard app chooser, so the user can pick Tripwire.
6. The `@valid` handle format stays stable and widely adopted. The research report says brokers covering over 90% of investors, and all mutual funds, have adopted it.
7. Users will grant notification access to a security app when the reason is explained plainly.

If assumption 1, 2 or 4 fails, the affected feature moves to the on-screen reading path or to phone-maker integration.

### 23.2 Open questions

**Technical**

- [ ] What are the real size, speed, memory use and heat of compressed Gemma 4 E2B on the reference phone?
- [ ] How does each messaging app show non-contact senders, group adds and long messages in its notifications?
- [ ] Does the warning overlay show reliably over the installer and the major payment apps on Android 14 to 17?
- [ ] What is the exact rule for a `@valid` handle, and does SEBI Check offer a link Tripwire can open with the handle filled in?
- [ ] Should Tripwire take Android's call-screening role to learn the caller's number? It would displace the user's caller-ID app.
- [ ] What is the minimum Android version?
- [ ] Can Sarvam Edge models be bundled under their licence?

**Product**

- [ ] Should the evidence pack be free? Charging for help after a loss sits badly with the product's purpose.
- [ ] Is 30 days of text retention too short for scams that run for months?
- [ ] Are users under 18 allowed?
- [ ] What is the right default when contacts permission is refused?

**Business and legal**

- [ ] Is the name "Tripwire" usable? A security software company already trades under it.
- [ ] Ally alerts by SMS need registered message templates under Indian telecom rules. Who registers, and what does it cost?
- [ ] Which phone maker and which broker are approached first?
- [ ] What does the legal review in 15.5 conclude?

## 24. Appendix

### A. The 14 tactic tags

| Tag | Meaning | Example, paraphrased |
| --- | --- | --- |
| `guaranteed_returns` | Promise of certain or very high profit | "Daily 5% profit, no risk" |
| `fake_social_proof` | Invented success of others | A screenshot of someone's large profit |
| `exclusivity` | Special access for a chosen few | "VIP group", "pre-IPO allotment only for members" |
| `urgency` | Pressure to act now | "Only today", "last two slots" |
| `authority_impersonation` | Claims to be police, a regulator, a bank or a known firm | "This is the CBI cyber cell" |
| `legal_threat` | Threat of arrest, a case or account freezing | "A warrant has been issued in your name" |
| `secrecy` | Told to tell no one | "Do not discuss this with family" |
| `channel_move` | Asked to move to a private chat or another app | "Message my assistant on Telegram" |
| `install_request` | Asked to install an app, usually from a link | "Download our trading app from this link" |
| `remote_access_request` | Asked to share the screen or install a remote-access app | "Share your screen so I can verify" |
| `credential_request` | Asked for a one-time password, PIN, card or bank details | "Tell me the code you received" |
| `payment_request` | Asked to send money | "Transfer to this UPI ID to activate" |
| `fee_to_withdraw` | Asked to pay a fee or tax to get money back | "Pay 18% tax to release your profit" |
| `small_win_bait` | A small early payout to build trust | "₹150 sent for your first task" |

### B. Sample tactic reader output

```json
{
  "language": "hi-Latn",
  "tags": [
    {"tag": "exclusivity", "confidence": 0.91},
    {"tag": "urgency", "confidence": 0.88},
    {"tag": "secrecy", "confidence": 0.84}
  ]
}
```

This is the expected output for the message quoted in section 10.2.

### C. Sample warning text

**Payment warning, fake investment, English**

> Stop. This payment matches a known investment scam. A stranger added you to a group six days ago and promised guaranteed profit. They sent you an app from a link, not from the Play Store. This payment address is not a SEBI `@valid` address. Registered brokers must use one. Money sent by UPI usually cannot be recalled.

**The same headline in Hindi**

> रुकिए। यह भुगतान एक जाने-पहचाने निवेश घोटाले से मेल खाता है।

**In-call notice, digital arrest, English**

> Police never arrest anyone on a video call. No officer will ask you to move money to a "safe account". You can hang up. Call someone you trust.

### D. What a UPI link contains

A UPI payment link or QR code carries the payee's handle, the payee's name, an amount and a note. Tripwire reads the handle and the name for grounded checks, shows its warning if one is due, then passes the unchanged link to the user's payment app.

### E. Evidence pack contents

1. Complainant: name, phone, email, address (typed by the user).
2. Incident summary: scam type, dates, total amount.
3. Timeline: each event with date, time and app.
4. Suspect identifiers: phone numbers, usernames, group names, UPI handles, bank details, links.
5. App details: name, package name, where it was installed from.
6. Transactions: amount, date and time, reference number, payee handle and name, the user's bank.
7. Quoted messages, with dates.
8. A note that the pack was generated on the user's phone by Tripwire.

### F. Companion document

*Tripwire: On-Device AI That Stops Cross-App Scams at the Moment of Payment*, the research report. It holds the full problem evidence, competitor research, business case and scoring.

### G. Sources

These are the pages behind the figures and platform facts in this PRD, as cited in the research report.

- [Moneylife: India is losing over ₹22,000 crore a year in cyber scams](https://www.moneylife.in/article/fraud-alert-india-is-losing-over-22000-crore-a-year-in-cyber-scams-and-the-worst-is-yet-to-come/80612.html)
- [The420: cybercrime cases and trends in India, 2025](https://the420.in/india-cybercrime-2025-losses-i4c-cpt-policy-reform/)
- [The420: ₹19,812 crore lost in 2025, I4C data](https://the420.in/india-cyber-fraud-losses-i4c-data-statewise-investment-scams/)
- [The420: first-hour freezing in Uttar Pradesh](https://the420.in/up-first-hour-cyber-fraud-freezing/)
- [Asianet News: Home Ministry warning on WhatsApp and Telegram investment scams](https://newsable.asianetnews.com/india/whatsapp-telegram-investment-scams-mha-warns-of-guaranteed-profits-articleshow-zy4elyw)
- [The Quint: fake IPO groups and trading apps](https://www.thequint.com/news/webqoof/fake-investment-trading-ipo-whatsapp-groups-and-apps-dupe-investors)
- [Paytm: SEBI validated UPI handles and SEBI Check](https://paytm.com/blog/news/sebi-validated-upi-handles-valid-sebi-check/)
- [Business Standard: DoT launches the Financial Fraud Risk Indicator](https://www.business-standard.com/industry/news/dot-launches-financial-fraud-risk-indicator-to-aid-cybercrime-detection-125052101912_1.html)
- [Sarvam: announcing Sarvam Edge](https://www.sarvam.ai/blogs/sarvam-edge)
- [Google Developers Blog: LiteRT on the Qualcomm NPU](https://developers.googleblog.com/unlocking-peak-performance-on-qualcomm-npu-with-litert/)
- [arXiv: agentic detection of conversational scams](https://arxiv.org/html/2607.11707)
- [Google: strengthening Android's scam protection](https://blog.google/security/staying-one-step-ahead-strengthening-androids-lead-in-scam-protection/)
- [TechCrunch: Google's AI scam protection in India and its gaps](https://techcrunch.com/2025/11/20/google-steps-up-ai-scam-protection-in-india-but-gaps-remain)
- [Google Play: use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- [Android Developers: Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-all)
- [The Hacker News: Android 17 and the accessibility API](https://thehackernews.com/2026/03/android-17-blocks-non-accessibility.html)
- [AV-Comparatives: mobile scam detection review 2025](https://www.av-comparatives.org/wp-content/uploads/2025/10/avc_msd_review_2025.pdf)
- [TechCrunch: Truecaller's scam checker](https://techcrunch.com/2026/09/27/truecaller-takes-its-scam-intelligence-to-the-open-web-as-it-looks-beyond-caller-id/)
- [TechCrunch: Savi](https://techcrunch.com/2026/07/07/savis-app-aims-to-protect-consumers-from-realistic-ai-scams-like-kidnappers-demanding-ransom/)
