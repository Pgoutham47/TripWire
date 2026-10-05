**Tripwire: on-device AI that catches a scam as it unfolds across your apps and steps in just before the money leaves**

## 1. Winning Idea
**Product name:** Tripwire
**Track:** Open Innovation (06), because a local open-source model is the core of the product. If your edition has the **FinTech & Commerce** track (the Hyderabad and other 2026 City Battles list one), submit there instead, since it fits even better.\[1\]\[2\] Productivity and Community App are weaker fits.
**One-line pitch:** Tripwire is an on-device AI that follows each stranger who contacts you across WhatsApp, Telegram, SMS, app installs and UPI payments, recognises when you are partway through a known scam script, and stops you at the one moment that can't be undone: the payment.

My recommendation is to build this. It meets every one of your criteria, and it is one of the few ideas where on-device AI is required rather than nice to have. Reading people's private chats in the cloud is not acceptable to users or regulators. The money lost is very large. And the main gap, scams carried out over WhatsApp and Telegram in Indian languages on non-Pixel phones, is still poorly covered by the big companies. That last point is a moving target, though. Google is closing the gap quickly, so the plan below is designed around that.

---

## 2. The Problem
**Who:** Indian smartphone users, both those being targeted and those who end up losing money. The main groups are:
- salaried people aged 30–60 who get pulled into fake "stock tips" groups;
- senior citizens hit by "digital arrest" calls from people posing as police or officials;
- the family members who usually find out after the money is gone.

**Scale:**
- Ministry of Home Affairs data reported by ThePrint shows Indians lost ₹22,495 crore to cyber fraud in 2025, against ₹22,845 crore in 2024, while cases rose to 28.15 lakh from 22.68 lakh.
- The 1930 cyber-fraud helpline logged 32.4 million calls in 2025.\[3\]
- Per the same MHA data, 76% of 2025 losses went to investment frauds (Ponzi schemes, fake stock-market trading scams and crypto scams); digital arrests made up 9%.
- Digital-arrest cases went from 39,925 in 2022 to 123,672 in 2024, with ₹1,935.51 crore lost in 2024.\[3\]
- The Indian Cyber Crime Coordination Centre (I4C) estimates that true 2025 losses could reach ₹1.2 lakh crore.\[3\]\[4\] That is a projection, not a measured number, so treat it as an upper bound.
- Figures don't fully agree: another I4C-based tally gives ₹19,812.96 crore across 21,77,524 complaints for 2025.\[5\] Whichever number you use, losses are around ₹20,000 crore a year and complaints are in the millions.

**Why it hurts:**
- Most money is never recovered. Moneylife reports that three out of four rupees lost to cyber fraud in India are not recovered, and Bengaluru city police data (reported by Deccan Herald) shows only about 4% of the ₹1,261 crore lost through August 2025 was traced and returned.
- Timing decides recovery. Uttar Pradesh froze 34% of reported fraud money in September by acting in the first hour, against 31% nationally.\[6\] Victims who report late usually get nothing back.

**How often:** These scams play out over days or weeks, not in one message, and the pattern is well documented by I4C and the Enforcement Directorate (ED):
1. The victim is added to a WhatsApp or Telegram group without asking.\[7\]\[8\]
2. The group is padded with fake members posting invented success stories.\[9\]
3. The victim is asked to install a "trading app" from a private link, not an app store.\[7\]\[10\]
4. They are told to send money to accounts under various company names.\[11\]
5. The app shows fake profits to encourage more deposits.\[11\]\[12\]
6. When the victim tries to withdraw, they are blocked and removed from the group.\[11\]

**Why existing tools fail:** They check one message, one call or one link at a time. This scam is a sequence of steps, each of which looks harmless on its own. US data shows how invisible this is from the inside. According to the FBI IC3 2025 Annual Report, Operation Level Up contacted 3,780 cryptocurrency-investment-fraud victims, and 78% of them were unaware they were being scammed. The problem is not missing information; it is that the victim can't see the pattern while they are in it.

## 3. The Insight
**The insight:** a scam is a script with a fixed order of steps, and its weak point is the payment step. Fraud protection today sits either at the start (caller ID, spam filters) or after the loss (1930, freezing funds). Nobody sits in the middle, watching the same stranger across several apps and stepping in exactly when money is about to move.

**Why now:**
1. **Phones can now run a capable model offline.**
   - Gemma 4, released April 2026 under Apache 2.0, has about 2B and 4B "edge" versions that handle text and audio.\[13\]
   - Sarvam Edge runs speech recognition in 10 Indic languages plus English on the phone (74M parameters, about 294MB), plus 60MB speech synthesis and 334MB translation models.\[14\]\[15\]
   - The iQOO 15 uses Snapdragon 8 Elite Gen 5. On that chip, Google measured FastVLM-0.5B at over 100 tokens/sec decode on the NPU.\[16\]\[17\]
2. **India now publishes trust signals an app can check.**
   - SEBI's `@valid` UPI handles and the SEBI Check tool went live in October 2025. Brokers covering over 90% of investors, and all mutual funds, have adopted them.\[18\]\[19\]
   - The Department of Telecommunications' Financial Fraud Risk Indicator (FRI) labels phone numbers by fraud risk. PhonePe, Paytm and Google Pay, which carry over 90% of UPI traffic, are integrating it.\[20\]\[21\]
   - The rule this enables: if a "broker" asks you to pay a handle that isn't `@valid`, that is a clear red flag.
3. **Research shows these scams can be caught before money moves.** A July 2026 arXiv paper (ConScamBench-278) found its agent first flagged scams at turn 6 (median) on that benchmark's scam conversations.\[22\] That is usually before the payment request.
4. **The big companies are leaving gaps.**
   - Google's Gemini-based message scam detection works only in Google Messages, and the Gemini-powered version runs only on the newest flagships in the US, Canada and the UK.\[23\]\[24\]
   - Google's call scam detection in India launched on Pixel 9 and later; one report puts Pixel at under 1% of India's market.\[25\]\[26\]
   - An independent test found McAfee does not scan WhatsApp messages automatically; users must check them by hand.\[27\]

## 4. The Solution
**For the user:**
- After a 90-second setup (notification access, optional on-screen reading of chats, and one "trusted ally" contact), Tripwire stays silent.
- It keeps a private, on-device record for each stranger: someone not in your contacts who messages you, adds you to a group, or calls you.
- Each time something happens, it updates where you are in a known scam script, for example: *invited to group → given a "VIP tip" → asked to move to private chat → sent an install link → asked to pay*.
- It interrupts only at points you can't undo: installing an app from a link, starting screen-sharing during a call, or opening a UPI payment to a handle linked to that stranger.
- The warning is spoken in your language and explains why. It does not just say "possible scam."
- If you still go ahead, or say "I already paid," Tripwire builds a 1930 complaint pack in one tap and alerts your trusted ally.

**Example from start to finish (Ramesh, 54, Pune, speaks Marathi):**
- **Day 1:** He is added to "Elite IPO Club 88" on WhatsApp. Tripwire opens a record for the group admin. Stage: *Hook*. No alert.
- **Day 3:** The admin posts a screenshot of "₹4.2 lakh profit" and DMs him "exclusive pre-IPO allotment." The model reads the notifications and tags guaranteed returns, exclusivity and a move to private chat. Stage: *Grooming*. A small banner appears: "This group matches a known fake-investment pattern."
- **Day 4:** He taps a link and starts installing "SATFIN Pro." Tripwire combines three signals: the APK came from a chat link, a stranger is involved, and the conversation is in the grooming stage. A full-screen warning plays in Marathi. Ramesh dismisses it.
- **Day 6:** He opens PhonePe to pay ₹2,00,000 to `satfin.trading@ybl`. The handle is not `@valid`, and a scheme that claims SEBI registration should be using one. Tripwire stops him at the UPI intent and shows:
  - the timeline of how he got here;
  - a SEBI Check deep-link;
  - one button: "Call my son first."
- He calls his son and the payment never happens. If he had paid, the pre-filled 1930 pack (screenshots, transaction ID (UTR), phone numbers, timeline) would be ready within the golden hour.\[28\]

## 5. Unique Innovation
**The unique innovation is: an on-device "scam progression tracker" that follows each stranger across all your apps and system events, matches the sequence against known scam scripts, and steps in at the irreversible point, the payment, using trust signals from Indian systems (SEBI `@valid`, SEBI Check) instead of guesswork.**

**How this differs from what exists:**
- **What it watches.** Google, McAfee, Norton, Truecaller and ScamMukt judge one artefact at a time: a message, a call, a link or a QR code.\[29\] Tripwire judges a person's behaviour over time, across apps.
- **When it acts.** Existing tools warn when a message arrives, which users learn to ignore. Tripwire warns when you're about to install, share your screen or pay. That is where a warning prevents loss.
- **Grounded checks.** "This 'SEBI-registered advisor' wants money sent to a non-@valid handle" can be verified. That makes the warning harder to ignore than an AI's opinion.
- **After the loss.** No consumer tool assembles a 1930-ready evidence pack automatically within the golden hour.

**Why it's hard to copy as a simple feature:**
- **Watching across apps is awkward for the big players.** Google's scam detection runs inside its own apps (Messages, Phone). Meta can see only WhatsApp. Banks see only the payment. PhonePe sees only UPI. No single player sees the whole sequence except an app that sits on the device on the user's behalf.
- **Data advantage.** Every confirmed scam (with consent and personal details removed) adds to a library of Indian scam scripts. Truecaller told TechCrunch at its Scam Checker launch (September 2026) that around 20,000 scam reports are live on ScamFeed in India, with about 1,300 new ones added every week. These are the company's own figures, but they show people will share reports. Tripwire would collect labelled step-by-step sequences rather than single messages.
- **Honest limit.** If Google adds cross-app tracking to Android itself, much of this edge disappears. Section 13 covers how to respond.

## 6. AI Core
**What the AI does:**
1. **Reads messages for scam tactics.** It tags each incoming message across 14 tactics: guaranteed returns, urgency, authority impersonation, secrecy, move to private chat, install request, payment request, "fee to withdraw," and so on. It handles code-mixed Hinglish, Marathi and Tamil text, where keyword rules fail.
2. **Tracks progress through scam scripts.** A small probabilistic state machine for each stranger (a hidden-Markov-style model over stages) combines the tags with system events: app install source, screen-share start, UPI handle, contact status. It outputs a scam family, a current stage and a risk score.
3. **Listens to calls (optional).** For digital-arrest calls on speakerphone, on-device speech recognition (Sarvam Edge) transcribes the call and the same tactic reader runs on the text.
4. **Explains the warning.** A small language model writes a 2–3 sentence reason in the user's language, read aloud by Sarvam's speech synthesis.
5. **Builds the evidence pack.** It pulls the UTR, handles, numbers and timeline into the 1930 / cybercrime.gov.in complaint format.

**Model layout:**
- **Tactic reader:** fine-tuned Gemma 4 E2B (or Gemma 3 270M for low-end phones), quantised to 4-bit, running on the NPU through LiteRT or Qualcomm Genie, with short structured JSON output.\[30\]
- **Progress tracker:** a few kilobytes of logic and probabilities, not a language model. It is auditable, deterministic at the decision point, and cheap.
- **Speech:** Sarvam Edge speech recognition, speech synthesis and translation (about 700MB total).\[31\]
- **Local database:** each stranger's record, plus a scam-script library that receives signed weekly updates.
- **Grounded checks:** a regular-expression check for `@valid` handles, and a SEBI Check deep-link.

**Why AI is essential:** Rules can catch "guaranteed 300% returns" in English. They can't catch "sir aapka allotment confirm hai, bas aaj hi karna padega, kisi ko mat batana" ("sir, your allotment is confirmed, you just have to do it today, don't tell anyone"). Scammers rephrase constantly, and an LLM recognises the tactic, not the exact words. The progress tracker deliberately does not use an LLM, because the step that blocks a payment must be explainable and predictable.

**Why on-device:**
- **Privacy:** private chats never leave the phone. This is the only version users or regulators would accept for something that reads WhatsApp.
- **Speed:** a payment can be blocked in under 300ms.
- **Cost:** no per-message cloud bill, which matters at Indian price points.
- **Offline:** it works on a weak connection.

**How it improves over time:**
- Scam scripts are updated weekly from opt-in, personal-details-removed reports.
- Each user's "dismiss" and "this was real" answers adjust their own thresholds.
- Later, federated learning could train shared improvements without collecting raw messages.

## 7. Competitive Analysis

| Existing Solution | What it does | Limitation | Our advantage |
|---|---|---|---|
| Google Scam Detection (Messages + Phone) | On-device Gemini Nano checks conversations in Google Messages and calls from unknown numbers; now on Galaxy S26, reportedly coming to vivo\[23\]\[32\] | Messages only (no WhatsApp/Telegram). The Gemini-powered message version is US/CA/UK flagships only. India calls launched Pixel 9+ only, in English/Hindi\[24\]\[26\] | Works across apps, in Indic languages, on any Android phone, and steps in at the payment |
| Truecaller (AI Call Scanner, Family Protection, Scam Checker) | Caller ID, spam blocking, AI voice detection, an ally who can end flagged calls, and a web tool where you paste a suspicious number, link or message\[33\]\[34\]\[35\] | Built around calls; Scam Checker needs the user to suspect something first | Detects without being asked, follows the full scam sequence, includes the evidence pack |
| McAfee Scam Detector / Norton Genie | AI checks of texts, emails and links; Norton is also available inside ChatGPT\[36\] | Independent test: McAfee does not scan WhatsApp automatically (manual check only).\[27\] Looks at one message at a time. Weak in Indic languages | Automatic, sequence-aware, Indic languages |
| Bitdefender Chat Protection | Scans links in WhatsApp/Telegram/Messenger chats using accessibility and notification access\[37\]\[38\] | Checks links only, not the conversation's tactics | Understands the conversation itself, not just links |
| PhonePe Protect / FRI / Navi Secure | Blocks or warns on payments to numbers the government flags as high fraud risk\[39\]\[40\] | Only knows numbers already reported; sees only the payment, not the conversation | Catches fresh scam numbers from conversation context, and passes FRI signals into its own checks |
| ScamMukt (₹50/mo), Quick Heal AntiFraud.AI | Link, QR and URL scanning; fraud call alerts\[41\]\[42\]\[43\] | Checks one item at a time | Tracks the whole sequence |
| Savi (US, $7M seed, July 2026) | Screens texts, voicemails and calls; a live AI "listener" on suspicious calls | Mostly cloud Gemini; US-focused\[44\] | On-device privacy, India-specific trust signals |

**Why the gap is real:** Every competitor is limited to one channel or one moment. The scam India loses the most money to is spread across WhatsApp, Telegram, an APK and UPI over several days. The only place all of those can be seen together is on the user's own phone, by an app acting for the user.

## 8. Target Users
- **Primary, and first to benefit:** the family "protector." Typically a 25–40-year-old urban professional who installs Tripwire on a parent's phone and is the trusted ally. They are the buyer and the person who sets it up, and they've usually heard of a scam in their own circle.
- **Secondary:**
  - first-time retail investors aged 30–55 (the fake-IPO and stock-tip target group);
  - senior citizens (digital arrest);
  - job seekers (task and job scams).
- **Later:** the Indian diaspora, who are targeted heavily, and Southeast Asian markets with similar scam operations.

## 9. Business Model
**Be realistic:** consumers rarely pay for security on their own, and Google gives its protection away free. A consumer-only subscription app will struggle. The business should be built mainly on selling through other companies.

- **Phone makers (OEM licence or preload, the main route).** vivo/iQOO, HMD, Lava and others want a "scam shield" to market in India. Built into the system, Tripwire also gets around Android permission limits (see Risks). Pricing: a per-device licence of roughly ₹15–40.
- **Brokers, banks and fintechs (SDK).** SEBI and RBI are pressuring brokers and payment apps to cut fraud. A "Tripwire inside" SDK that signals risk at payment time can be sold per active user or per prevented incident. Data stays on the device; only the risk signal is shared.
- **Insurers.** Cyber-fraud insurance priced lower for Tripwire users, with a referral fee.
- **Consumers (premium family plan).** About ₹49–99/month for a family of up to 5. The free tier covers single-user detection. Paid adds ally alerts, the evidence pack and monitoring of multiple devices. ScamMukt's ₹50/month price shows where the price ceiling sits.\[43\]

**Market size (bottom-up):** Take 100 million Android users in India in the 30+ age group who use UPI. If OEM preload reaches 10 million devices at ₹25, that is about ₹25 crore a year. Broker and bank SDKs could add more. That is a solid Indian business, not a huge one, unless it expands into Southeast Asia and diaspora markets, where scam losses per victim are much higher. In the US alone, the FBI's 2025 report counted $8.65B in investment-fraud losses.\[45\]\[46\]\[47\]

**Is it crowded?** In scam detection generally, yes. In cross-app detection that intervenes at the payment, no.

## 10. MVP
**Must-have (for the hackathon):**
1. A notification-listener service that captures WhatsApp, Telegram and SMS notifications from non-contacts.
2. The on-device Gemma tactic reader: 14 tags in JSON, English/Hindi/Hinglish.
3. A per-stranger progress tracker for 2 scam families: fake investment, and digital arrest/KYC.
4. Interception at two points:
   - installing an APK that came from a chat link;
   - a UPI payment link or QR, with the `@valid` check.
5. A spoken explanation in Hindi or Marathi using Sarvam speech synthesis (or Android's built-in voice as a fallback).
6. A one-tap 1930 evidence pack (PDF plus a dial button).
7. A timeline screen for each stranger.

**Nice-to-have:**
- Trusted-ally push alert.
- A screen-share-during-call warning.
- Call transcription on speakerphone.
- FRI-style risk lookup (mocked).

**Future:**
- System-level OEM integration.
- Federated script updates.
- Detection of AI-cloned voices.
- A bank SDK.
- Multi-device family dashboard.
- More scam families: job/task scams, sextortion, courier scams.

## 11. Technical Architecture
```
 ┌──────────────────── iQOO 15 (all on-device) ─────────────────────┐
 │  SIGNAL COLLECTORS                                                │
 │  NotificationListener ─┐  AccessibilityService (opt-in, chat view)│
 │  PackageInstaller events┤  MediaProjection/screen-share detect    │
 │  UPI intent/QR intercept┤  Contacts (is-stranger check)           │
 │                         ▼                                         │
 │  NORMALISER → event{counterparty_id, app, text, type, ts}         │
 │                         ▼                                         │
 │  AI LAYER                                                         │
 │  [Gemma 4 E2B int4 on Hexagon NPU via LiteRT/Genie]               │
 │     → tactic tags JSON                                            │
 │  [Sarvam Edge ASR] → call transcript → same reader                │
 │                         ▼                                         │
 │  KILL-CHAIN ENGINE (HMM per counterparty + script library)        │
 │     → family, stage, risk, evidence                               │
 │                         ▼                                         │
 │  INTERVENTION BROKER: gate install / pay / screen-share           │
 │     + grounded checks (@valid regex, SEBI Check deep-link)        │
 │     → overlay + Sarvam TTS explanation (Indic)                    │
 │                         ▼                                         │
 │  STORAGE: SQLCipher (encrypted ledger), Android Keystore keys     │
 │  RESPONSE: 1930 pack (PDF), ally alert                            │
 └────────────────────────────────┬──────────────────────────────────┘
                                  │ (opt-in, anonymised only)
                    ┌─────────────▼─────────────┐
                    │ CLOUD (thin): signed script│
                    │ updates, ally push (FCM),  │
                    │ anonymised pattern intake  │
                    └────────────────────────────┘
```
**Frontend:** Kotlin + Jetpack Compose.
**AI runtime:** LiteRT / MediaPipe LLM Inference, or Qualcomm AI Hub/Genie.\[16\]
**Database:** SQLite encrypted with SQLCipher.
**Backend:** Firebase Cloud Messaging for ally alerts, and a small update server for signed scam-script packs.
**Privacy:**
- Raw text never leaves the device.
- Records older than 30 days are deleted automatically.
- No message content goes into analytics.
- Prominent in-app disclosure of what is read, as Play requires.\[48\]

**Office Kit:** use it only for fine-tuning and evaluation on a laptop, since 25% of the hackathon score comes from phone-usage telemetry.\[49\]

## 12. 3–5 Minute Demo
1. **(0:00–0:30) The hook.** One slide:
   - "₹22,495 crore lost in 2025, 76% of it to fake investment schemes."\[3\]
   - "The FBI found 78% of victims didn't know they were being scammed."\[45\]
   - "Today's tools check messages. Scams are sequences."
2. **(0:30–1:15) Getting drawn in.** A second phone plays the scammer and sends a real-style sequence to the iQOO 15: a group invite, a profit screenshot, a Hinglish DM. On the Tripwire timeline, the stranger's stage moves *Hook → Grooming* live, with tags appearing. Point out that airplane mode is on.
3. **(1:15–2:00) The install trap.** The victim taps the APK link. A full-screen warning plays in Hindi: "This app came from a chat link sent by a stranger who has been promising guaranteed returns…"
4. **(2:00–3:00) The moment that shows the innovation.** The victim dismisses the warning and opens a UPI QR for `satfin.trading@ybl`. Tripwire stops the payment and shows the four-step timeline from three different apps, plus "Not a SEBI @valid handle." Point out that no single app saw this; the payment app thought it was a normal payment.
5. **(3:00–3:40) Golden hour.** Toggle "I already paid." The 1930 pack appears with UTR, handles, screenshots and timeline. The ally's phone buzzes.
6. **(3:40–4:30) Proof it's real.**
   - On-screen stats: the model runs on the NPU at about 40ms per message.
   - Detection results on a held-out set of Indian scam scripts: catches by stage and false alarms on ordinary family and business chats.
   - The business model, in one slide.

## 13. Business & Impact Validation
**Why will users care?** Almost every Indian family has a fraud story, and the losses can wipe out savings. One example: in a case handled by the Mumbai Crime Branch cyber police, scammers posing as police and CBI officers took ₹20.25 crore from an 86-year-old woman between December 26, 2024 and March 3, 2025, claiming her Aadhaar had been misused for money laundering.

**Why will they pay or adopt it?** Adults will install it on their parents' phones out of fear and love, and phone makers and brokers will pay to cut fraud and its reputational damage. Free detection drives adoption; ally alerts and the evidence pack are what people pay for.

**Why now?**
- Phone NPUs can now run Indic-language models offline.
- SEBI's `@valid` and DoT's FRI created trust signals an app can check.
- Google's protection still doesn't cover WhatsApp in India.

**Why can't a big company just copy it and dominate?** They can, partly, and that is the main risk.
- Google is the most likely to try, but its scam protection is built into its own apps (Messages and Phone), not across WhatsApp and Telegram.
- Meta covers only WhatsApp.
- Payment apps see only the payment.

The defence comes from three things: Indian scam-script data, Indic-language quality, and contracts with phone makers and brokers. Being exclusive to one OEM (say vivo/iQOO) or getting acquired is a realistic and acceptable outcome.

**Measurable impact:**
- Share of risky payments abandoned after a warning.
- Average stage at which a scam is first detected (target: before the payment request).
- False alarms per user per month (target: under 1).
- Time to file a complaint (target: under 10 minutes after a loss).

## 14. Risks

| Risk | Type | Mitigation |
|---|---|---|
| Since Android 15, notifications containing OTPs are redacted for third-party apps, and Android 17's Advanced Protection Mode removes accessibility access from non-accessibility apps\[50\]\[51\]\[52\]\[53\] | Technical/platform | Build on notification access, which still delivers ordinary chat text (only OTP notifications are redacted). Make on-screen reading optional. Pursue OEM system-level integration (system-signed apps get the permission to read sensitive notifications).\[50\] Never falsely declare `isAccessibilityTool` |
| Play policy: security apps can't call themselves accessibility tools, and must show a prominent disclosure and submit a declaration\[48\] | Distribution | Treat Play's in-app disclosure requirements as an advantage and make the disclosure a clear setup screen. The declaration form has a "Fraud prevention, security" purpose. Android 13+ blocks sideloaded apps from these permissions, so distribute through Play\[48\]\[54\]\[55\] |
| False alarms lead to people ignoring warnings | Product | Warn only at irreversible actions. Show grounded evidence. Tune thresholds for each user |
| Google adds cross-app or WhatsApp coverage to Android itself | Market | Aim for OEM partnership or acquisition, or lead in Indic languages, the evidence pack and broker SDKs |
| Scammers adapt (images instead of text, voice notes) | Technical | Add an on-device image model for profit screenshots, speech recognition for voice notes, and weekly script updates |
| Reading private chats feels creepy | Adoption | Everything on-device, encrypted, auto-deleted. Open-source the detection logic |
| Weak benchmark data for India | Technical | Build an evaluation set from I4C, ED, SEBI and police case reports plus synthetic variations. State plainly that it's synthetic, as the ConScamBench authors did\[22\] |
| Liability if a scam gets through | Business | Position it as decision support, not a guarantee, in clear terms |

## 15. Final Verdict
- **Overall: 8.3/10**
- **Innovation:** 8/10. The cross-app sequence tracking and payment-time intervention are genuinely new; scam detection as a category is not.
- **Business:** 7.5/10. Strongest through OEM and broker deals; consumer-only would be weak.
- **Feasibility:** 8.5/10. Notifications plus a quantised Gemma plus intent interception is achievable in 30 hours.
- **Hackathon/demo:** 9.5/10. A live scam unfolding across apps on a phone in airplane mode, ending in a stopped payment, is very strong theatre.

**Should you build this? Yes.**
- **Why it fits this hackathon:**
  - It is the highest-scoring option on your framework. The problem is real and expensive (about ₹20,000 crore a year in reported losses).
  - On-device AI is required for privacy, not cosmetic.
  - It plays directly to an iQOO and Snapdragon showcase. Note that vivo/iQOO users have themselves been targeted by fake "OriginOS update" scam APKs, which gives judges a reason to care.\[56\]\[57\]
- **The honest downsides:**
  - You are competing next to Google's fast-moving scam detection.
  - Android's permission rules get stricter every year.
  - Consumers alone won't sustain the business.
- **Execution and timing:**
  - Win by staying narrow (fake investment plus digital arrest, Indic languages, the payment-time intervention) and pitching phone makers and brokers, not a consumer app store.
  - The Grand Finale is October 9–11, 2026, five days away.\[58\]\[59\] Lock the demo story first, then build the two scam families and the UPI intervention properly. Leave everything else as slides.

## Sources

1. [iQOO City Battles Hyderabad: On-Device AI Hackathon (Sept 26-27, 2026) - Reskilll Blogs](https://reskilll.com/blogs/iqoo-city-battles-hyderabad-on-device-ai-hackathon-sept-2026/)
2. [iQOO Hackathon 2026 · IH2 · Wooble](https://wooble.org/companies/wooble-programs/challenges/iqoo-hackathon-2026)
3. [Fraud Alert: India Is Losing over ₹22,000 Crore a Year in Cyber Scams — and the Worst Is Yet To Come](https://www.moneylife.in/article/fraud-alert-india-is-losing-over-22000-crore-a-year-in-cyber-scams-and-the-worst-is-yet-to-come/80612.html)
4. [Top 10 Most Highlighted Cyber Crime Cases and Trends in India in 2025 - The420.in](https://the420.in/india-cybercrime-2025-losses-i4c-cpt-policy-reform/)
5. [Financial Frauds Hit Record High Across India: Over ₹19,812 Crore Lost in 2025 - The420.in](https://the420.in/india-cyber-fraud-losses-i4c-data-statewise-investment-scams/)
6. [UP Freezes 34% of Cyber Fraud Money With ‘First-Hour’ Response Strategy - The420.in](https://the420.in/up-first-hour-cyber-fraud-freezing/)
7. [WhatsApp, Telegram investment scams: MHA warns of 'guaranteed' profits](https://newsable.asianetnews.com/india/whatsapp-telegram-investment-scams-mha-warns-of-guaranteed-profits-articleshow-zy4elyw)
8. [Telegram and WhatsApp Investment Groups: Why Are They a Major Digital Trap - ApniLaw](https://www.apnilaw.com/news/criminal/cyber-crime/telegram-and-whatsapp-investment-groups-why-are-they-a-major-digital-trap/)
9. [Fake profits to human trafficking: The Whatsapp stock market scam decoded](https://www.business-standard.com/amp/finance/personal-finance/fake-profits-to-human-trafficking-the-whatsapp-stock-market-scam-decoded-124090900416_1.html)
10. [India warns against investment scams on WhatsApp and Telegram](https://thenewsmill.com/2026/07/india-warns-against-investment-scams-on-whatsapp-and-telegram/)
11. ['Exclusive' IPO Deals on WhatsApp Groups, Fake Trading Apps Target Investors](https://www.thequint.com/news/webqoof/fake-investment-trading-ipo-whatsapp-groups-and-apps-dupe-investors)
12. [Home Ministry Issues Warning: Fake WhatsApp & Telegram Investment Scams](https://www.news4hackers.com/home-ministry-issues-warning-fake-whatsapp-telegram-investment-scams)
13. [Gemma (language model)](<https://en.wikipedia.org/wiki/Gemma_(language_model)>)
14. [Sarvam Edge: A Beginner’s Guide to On-Device AI for India - Analytics Vidhya](https://www.analyticsvidhya.com/blog/2026/03/sarvam-edge/)
15. [Announcing Sarvam Edge](https://www.sarvam.ai/blogs/sarvam-edge)
16. [Unlocking Peak Performance on Qualcomm NPU with LiteRT - Google Developers Blog](https://developers.googleblog.com/unlocking-peak-performance-on-qualcomm-npu-with-litert/)
17. [iQOO 15 debuts with Snapdragon 8 Elite Gen 5 SoC, 7,000mAh battery](https://m.gsmarena.com/iqoo_15_debuts_with_snapdragon_8_elite_gen_5_soc_7000mah_battery-amp-69980.php)
18. [TeamLease RegTech - SEBI Launch of “Validated UPI Handles” and “SEBI Check” for Investor Payment Security](https://www.teamleaseregtech.com/updates/article/47435/sebi-launch-of-validated-upi-handles-and-sebi-check-for-investor-payme/)
19. [SEBI Validated UPI Handles (@valid) & SEBI Check](https://paytm.com/blog/news/sebi-validated-upi-handles-valid-sebi-check/)
20. [DoT launches 'Financial Fraud Risk Indicator' to curb cybercrime in digital payments](https://www.voicendata.com/news/dot-launches-financial-fraud-risk-indicator-to-curb-cybercrime-in-digital-payments-9149405)
21. [Friday, January 16, 2026 | 02:12 PM ISTहिंदी में पढें](https://www.business-standard.com/industry/news/dot-launches-financial-fraud-risk-indicator-to-aid-cybercrime-detection-125052101912_1.html)
22. [An Explainable Agentic System for Detection of Conversational](https://arxiv.org/pdf/2607.11707)
23. [Scam Detection comes to Samsung's S26 Phone app as Google Messages gets Gemini boost](https://9to5google.com/2026/02/25/google-messages-scam-detection-gemini/)
24. [Staying One Step Ahead: Strengthening Android’s Lead in Scam Protection](https://blog.google/security/staying-one-step-ahead-strengthening-androids-lead-in-scam-protection/)
25. [Google Launches On-Device AI Scam Detection for Indian Users Amid Surge in Digital Fraud - The Logical Indian](https://thelogicalindian.com/google-launches-on-device-ai-scam-detection-for-indian-users-amid-surge-in-digital-fraud/)
26. [google steps up ai scam protection in india but gaps remain](https://techcrunch.com/2025/11/20/google-steps-up-ai-scam-protection-in-india-but-gaps-remain)
27. <https://www.av-comparatives.org/wp-content/uploads/2025/10/avc_msd_review_2025.pdf>
28. [How to Report Cyber Fraud in India (1930 & Cybercrime Portal)](https://translate.google.com/translate?client=srp&hl=hi&sl=en&tl=hi&u=https%3A%2F%2Fdelhi-lawyers.in%2Fhow-to-report-cyber-fraud-india-1930)
29. [An Explainable Agentic System for Detection of Conversational Scams with Summary-Based Memory](https://arxiv.org/html/2607.11707)
30. [Running LLMs on-device with Qualcomm Snapdragon 8 Elite](https://grapeup.com/blog/running-llms-on-device-with-qualcomm-snapdragon-8-elite)
31. [Sarvam Edge: India's First On-Device AI That Works Without Internet](https://www.adwaitx.com/sarvam-edge-on-device-ai-india/)
32. [Google Scam Detection Feature Expected to Expand to Vivo Phones - Comparos.in](https://www.comparos.in/news/google-scam-detection-feature-expected-to-expand-to-vivo-phones)
33. [Truecaller takes its scam intelligence to the open web as it looks beyond caller ID](https://techcrunch.com/2026/09/27/truecaller-takes-its-scam-intelligence-to-the-open-web-as-it-looks-beyond-caller-id/)
34. [Truecaller launches AI Call Scanner, the AI Voice Scam Detection System » World Business Outlook](https://worldbusinessoutlook.com/truecaller-launches-ai-call-scanner-the-ai-voice-scam-detection-system/)
35. [Truecaller Family Protection](https://www.techcrunch.com/2025/12/09/truecaller-now-lets-users-protect-households-from-scam-calls/)
36. [Get instant scam checks and trusted Cyber Safety advice from Norton without leaving your ChatGPT conversation](https://www.barchart.com/story/news/568002/the-world-s-first-ai-powered-scam-detector-norton-genie-now-in-chatgpt)
37. [A Guide to Bitdefender Scam Protection for Android](https://www.bitdefender.com/consumer/support/answer/42907/)
38. [www.businesswire.com](https://www.businesswire.com/news/home/20221102005109/en)
39. [Finovate Global India: Raising Capital, Fighting Fraud, and Innovating in Payments - Finovate](https://finovate.com/finovate-global-india-raising-capital-fighting-fraud-and-innovating-in-payments/)
40. [The Department of Telecommunications (DoT) Introduces "Financial Fraud Risk Indicator (FRI)" to strengthen Cyber Fraud Prevention](https://www.ibef.org/news/the-department-of-telecommunications-dot-introduces-financial-fraud-risk-indicator-fri-to-strengthen-cyber-fraud-prevention)
41. [apps.apple.com](https://apps.apple.com/us/app/-/id6503627001)
42. [ScamMukt Launches India's Dedicated AI Scam Protection App -- Because Spotting a Scam Is Now a Basic Survival Skill for Every Indian](https://aninews.in/news/business/scammukt-launches-indias-dedicated-ai-scam-protection-app-because-spotting-a-scam-is-now-a-basic-survival-skill-for-every-indian20260529124728/)
43. [Business News](https://www.latestly.com/agency-news/business-news-scammukt-launches-indias-dedicated-ai-scam-protection-app-because-spotting-a-scam-is-now-a-basic-survival-skill-for-every-indian-7450569.html)
44. [Savi's app aims to protect consumers from realistic AI scams like kidnappers demanding ransom](https://techcrunch.com/2026/07/07/savis-app-aims-to-protect-consumers-from-realistic-ai-scams-like-kidnappers-demanding-ransom/)
45. [The Scams That Cost Americans \$20.9 Billion in 2025, guptadeepak.com](https://guptadeepak.com/scams-that-cost-americans-2025/)
46. [FBI reports 22% rise in crypto fraud losses in 2025 - TheStreet Crypto: Bitcoin and cryptocurrency news, advice, analysis and more](https://www.thestreet.com/crypto/markets/fbi-reports-22-rise-in-crypto-fraud-losses-in-2025)
47. [Why Cybercrime Losses Hit \$21 Billion in 2025](https://blog.barracuda.com/2026/05/26/cybercrime-losses-2025-fbi-ic3-report)
48. [Use of the AccessibilityService API - Play Console Help](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
49. [iQOO Hackathon 2026: India's Phone-First AI Hackathon by iQOO x Reskilll - Reskilll Blogs](https://reskilll.com/blogs/iqoo-hackathon-2026-india-phone-first-ai-hackathon-iqoo-reskilll/)
50. [Here's how Android 15 protects your two-factor authentication codes from malicious apps](https://www.androidauthority.com/android-15-two-factor-authentication-codes-3492585/)
51. [Behavior changes: all apps](https://developer.android.com/about/versions/15/behavior-changes-all)
52. [Android 17 Blocks Non-Accessibility Apps from Accessibility API to Prevent Malware Abuse](https://thehackernews.com/2026/03/android-17-blocks-non-accessibility.html)
53. [Android's Advanced Protection Mode now targets your favorite customization, automation apps - Android Authority](https://www.androidauthority.com/android-advanced-protection-mode-accessibility-apk-teardown-3640742/)
54. [Android 13's Restricted setting feature will block malicious apps from accessing your notifications](https://www.xda-developers.com/android-13-restricted-setting-notification-listener/)
55. [Android 15 can choose which permissions sideloaded apps get to use](https://www.androidpolice.com/android-15-enhanced-confirmation-mode-sideloading/)
56. [Fake ‘OriginOS Update’ scam targets Vivo, iQOO users: Police issue advisory](https://www.uniindia.com/fake-originos-updat-scam-targets-vivo-iqoo-users-police-issue-advisory/south/news/3817265.html)
57. [Kerala Police warns Vivo, iQOO users of fake ‘OriginOS Update’ cyber fraud - Storyboard18](https://www.storyboard18.com/digital/kerala-police-warns-vivo-iqoo-users-of-fake-originos-update-cyber-fraud-95788.htm)
58. [iQOO Hackathon 2026 launched as India’s first hybrid mobile development challenge](https://www.fonearena.com/blog/489783/iqoo-hackathon-2026-hybrid-mobile-development-challenge.html)
59. [iQOO Hackathon 2026 Opens Top City Battles - Techgenyz](https://techgenyz.com/iqoo-hackathon-2026-city-battles/)
