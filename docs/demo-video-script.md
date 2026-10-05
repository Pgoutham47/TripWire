# Tripwire demo video script

**Length:** about 2:35 · **Audience:** hackathon judges · **Language:** English
**Format:** your face intro, then your voiceover over real recordings from the phone, with motion design (phone frame, captions, callouts, music).

Every feature shown runs in the app today. Items not built yet appear only in "What's next", worded as plans.

---

## 1. Face intro · 0:00–0:25 · you on camera

> Every day in India, someone loses their savings to a scam that didn't look like one. A stock tip in a WhatsApp group. A call from "the police". An app sent as a link. By the time they realise, the money is gone, because a UPI payment can't be undone.
>
> I'm Goutham, and this is Tripwire. It follows a scam as it unfolds, across WhatsApp, Telegram, SMS, calls and payments, and stops you at the one moment that matters: right before you pay.
>
> Let me show you.

**Filming tips:** eye level, window light on your face, a plain background, phone in your hand for the last line. Two or three takes; we'll use the best.

---

## 2. The problem · 0:25–0:37 · motion graphics

| On screen | Voiceover |
|---|---|
| A counter animates to **₹22,495 crore** · caption "lost to cyber fraud in India in 2025" · source line "MHA data, via ThePrint" | "Last year, Indians lost over twenty-two thousand crore rupees to cyber fraud." |
| **3 of every 4 rupees** · caption "never recovered" · source "Moneylife" | "Three of every four rupees were never recovered." |
| Five app icons appear one by one along a timeline: WhatsApp → Telegram → SMS → call → UPI | "Because a scam isn't one message. It's a script that plays out over days, across apps. And no single app sees all of it." |

---

## 3. One scam, start to finish · 0:37–1:30 · phone recording

| On screen | Voiceover |
|---|---|
| An SMS arrives from an unknown number: "Join our SEBI registered group, 30% guaranteed returns every month." | "Meet Tripwire. A stranger promises guaranteed returns. Tripwire is already reading the pattern, quietly, on the phone." |
| Second message: "Download our trading app SATFIN Pro: http://…apk". Tripwire's home shows the chat as High risk, Step 4 of 6; the case screen shows the timeline and tactic chips. | "Then comes an app link. Each step is matched against how real investment scams unfold." |
| The Android install screen for SATFIN Pro opens. Tripwire's warning appears on top **before** the Install button is tapped. Callout: "Before the install". | "When the install screen opens, Tripwire steps in before the app is installed, not after." |
| Tap **Don't install** → the installer closes. Callout: "Nothing installed". | "One tap, and the install is cancelled." |
| A UPI payment link opens. The full-screen warning shows the headline, reasons, the "What happened" timeline, and the red box "This payment address is not a SEBI @valid address". | "And at the moment of payment, it shows exactly why: what happened, in order, and a hard fact. Registered brokers must use a SEBI @valid address. This one doesn't." |
| Tap **Don't pay**. Callout: "Payment stopped". Slow-motion on the "Pay anyway (hold 3 seconds)" bar. | "It never blocks you. You can still go ahead, but only on purpose." |

---

## 4. Protection beyond one scam · 1:30–2:05 · fast cuts, about 5 seconds each

| On screen | Voiceover |
|---|---|
| A phone call from a stranger, then an OTP SMS arrives → alert "Don't share this code". | "A code arrives while a stranger is on the line? Tripwire warns you not to share it, without ever reading the code." |
| SMS "₹5,000 credited… sent by mistake, please return" from a mobile number → alert "This 'money credited' SMS is fake". | "A fake 'money credited' message from a mobile number? Flagged. Banks never send from one." |
| Phone checkup → "SATFIN Pro: Risky · Can see and control your screen" → **Remove app**. | "Phone checkup finds apps that can steal your codes, and helps you remove them." |
| Turning on screen access for SATFIN Pro → alert "SATFIN Pro can now control your phone". | "And if an app from a link grabs control of your screen, Tripwire raises the alarm instantly." |
| A warning in Hindi, read aloud (speaker icon pulses). | "Warnings in Hindi or English, read aloud for those who need it." |
| "I already paid" → "55 min left", **Call 1930 now**, "Call SBI: 1800 1111 09", **Build complaint pack** → PDF. | "And if money is already gone, every minute counts. Tripwire starts the clock, calls 1930 and your bank's fraud line, and builds the complaint pack in seconds." |
| Home-screen widget "Watching 1 suspicious chat · I already paid". | "Protection, one glance away." |

---

## 5. How it works · 2:05–2:22 · motion graphics diagram

Left to right, animated:
- **Signals:** WhatsApp · Telegram · SMS · calls · installs · UPI
- **On the phone:** tactic reader → scam-stage engine → hard checks (SEBI @valid, install source, payee name)
- **Response:** quiet notice → full-screen warning → instant alerts → complaint pack

Badge: **"Runs on the phone. Messages never leave it."**

| Voiceover |
|---|
| "Under the hood, Tripwire links every stranger across apps, tracks which stage of a scam they're at, and checks hard facts at the moment of payment. All of it runs on the phone. Your messages never leave it." |

---

## 5b. Our AI model: what it's for, how we built it, how we trained it · after "How it works" · motion graphics

**New voiceover, to be recorded.** Every number comes from `tools/model/README.md` (run qwen05-v3), and the on-screen footnotes say these are test-set results on partly synthetic data.

| On screen | Voiceover |
|---|---|
| **Why.** A scam line in three wordings (English, Hindi, Hinglish). A "keyword rules" scanner catches the English one and misses the other two (red ✕). | **A1.** "Scammers change their words every day, so keyword rules miss a lot, especially in Hindi and Hinglish. That's why we built our own AI model." |
| **What it does.** One message bubble goes into a box labelled "Tripwire AI"; three chips come out: Guaranteed returns · Urgency · Secrecy. | **A2.** "It reads each message and names the trick being used: a promise of guaranteed returns, pressure to act now, or a request to keep it secret." |
| **Step 1 · Tactics.** A grid of the 14 tactic names fills in, each with a one-line rule. | **A3.** "First, we defined fourteen scam tactics, with a clear rule for when each one applies." |
| **Step 2 · Data.** A counter climbs to 15,213 messages; three language chips; a split bar: scam and everyday; each card gets tactic labels stamped on it. | **A4.** "Then we built a dataset of fifteen thousand messages, in English, Hindi and Hinglish, both scam and everyday, each labelled with its tactics." |
| **Step 3 · Fair test.** The stack splits into Train 10,728 · Check 2,179 · Test 2,306; a lock icon on Test: "never seen in training". | **A5.** "We kept a separate set aside, worded differently, so we could test the model on messages it had never seen." |
| **Step 4 · Training.** A laptop; "Qwen 2.5 · 0.5 billion parameters" plus "our 10,728 labelled messages" go in; a LoRA badge; a training-loss line falls. | **A6.** "We trained an open model, Qwen 2.5, with half a billion parameters, on a laptop, using a technique called LoRA." |
| **Step 5 · Onto the phone.** The model shrinks into one file (8-bit, 522 MB, LiteRT) that slides into a phone; badge "Messages never leave the phone". | **A7.** "Then we compressed it into a single file built for Android phones, so messages never have to leave the phone." |
| **Safe by design.** A message saying "Ignore your instructions" goes in; only the fixed answer format comes out, with a green check. | **A8.** "It can only answer in a fixed format that the app checks, so a scam message can't trick it into saying anything else." |
| **Results.** Bars: Hindi score, keyword rules 0.32 vs our model 0.60. Then "Scam replays: 6 of 6 caught before payment · 0 false warnings". Footnote: "Test set of unseen phrasings, partly synthetic". | **A9.** "On messages it had never seen, it nearly doubled our Hindi detection. And when we replayed scam conversations, it caught all six before the payment, with no false warnings." |
| **Now.** "False alarms on everyday messages" gauge moving down toward a 3% target line; chips "Real chats, personal details removed" · "Google Gemma". | **A10.** "Now we're cutting its false alarms on everyday messages, with real chats, personal details removed, and Google's larger Gemma models, before we switch it on for everyone." |

---

## 6. Where Tripwire is going, and close

**New voiceover, to be recorded. It replaces the old "Next, we're switching on…" line.**

| On screen | Voiceover |
|---|---|
| The real app: a warning fires and a ready-to-send text to the trusted person appears. | **V1.** "Already, when a warning fires, Tripwire prepares a text to a trusted person, ready to send." |
| Title card. A small "Roadmap" tag stays in the corner from here to the end card. | **V2.** "And this is where Tripwire is going." |
| Concept screen: the son's phone shows "Mum: scam warning · investment scam · step 4 of 6", no message text. | **V3.** "When a warning fires on Mum's phone, her son is alerted instantly, with the type of scam and how far it has got, never her messages." |
| Concept screen: "Mum wants to pay ₹50,000 to a new contact" with **Approve** / **Stop**. | **V4.** "Before a big payment to a stranger goes out, he approves it or stops it, from his own phone." |
| One warning cycling through Hindi, Telugu, Tamil, Bengali and Marathi. | **V5.** "And every warning speaks her language: Hindi, Telugu, Tamil, Bengali, Marathi and more." |

**End card:** Tripwire logo · "Stops the scam before the payment." · GitHub link
**T1.** "Tripwire. Stops the scam before the payment."

---

## Recording plan

**Recorded on the Android emulator, done** (`video/raw/`): scene 3 in one take (`s3_scam_story.mp4`, with SMS, the install screen and a UPI link) and the scene 4 clips: `s4a_otp_guard`, `s4b_fake_credit`, `s4c_malware_checkup`, `s4d_checkup`, `s4e_hindi_warning`, `s4f_already_paid` and `s4g_widget`. The emulator has no WhatsApp, so the story arrives by SMS; the voiceover says "message".

**Face intro, done** (`video/raw/face/`): three takes presented by Geeta, about 31 seconds, with burned-in subtitles. The first take opens at "In India…" (the recording starts after "Every day").

**Voiceover, done** (`video/raw/voiceover.m4a`): one take covering scenes 2–6. The build cuts it into lines (`VO_CUTS` in `video/build.py`), removes a repeated "starts" and a false start, shortens long pauses and plays it 7% faster. The closing line "Tripwire. Stops the scam before the payment." was not recorded, so the end card is silent.

**Output:** `python3 video/build.py` writes `video/tripwire-demo.mp4` (1080p, 30 fps, 4:21, −16 LUFS) with captions burned in throughout, and `video/tripwire-demo.srt` with the same captions. Voice clean-up per take is in `TAKES`; caption timing comes from Whisper word timings, cached in `video/build/captions/`. Cut for length: the widget line, "How it works" (scene 5), A5 (its train / check / test split is now a bar in A4's scene) and A8. Their lines stay in the cut lists in `video/build.py`, so any can return. Music: a bed built from GarageBand's Apple Loops (Chillwave, "Harmonic Waves" parts plus "Reverse Hat Beat 01"), which dips under speech and comes up on the end card; see `LAYERS` and `BED_DB` in `video/build.py`.
