# Tactic labeling guide

How messages in the tactic dataset are labeled (PRD 10.5, Appendix A). Each message gets zero or more of the 14 tactic tags. A tag marks a **persuasion tactic used on the reader**, not a topic. "Returns were decent this year" talks about returns but uses no tactic.

## The tags

| Tag | Label it when the message… | Do not label |
| --- | --- | --- |
| `guaranteed_returns` | promises certain, risk-free or implausibly high profit | honest talk about returns ("markets are volatile") |
| `fake_social_proof` | shows others' profits or success to persuade: screenshots, "members earned ₹…", testimonials | a friend mentioning their own investment in passing |
| `exclusivity` | offers special access: VIP, pre-IPO allotment, limited seats, "only for you" | ordinary offers to everyone ("sale ends Sunday") |
| `urgency` | pressures the reader to act now: "only today", "within 30 minutes", "immediately" | neutral deadlines from known services ("bill due on 10th") |
| `authority_claim` | says it is, or speaks for, police, government, a court, a regulator, a bank, a payment app, a broker, a courier or a telecom company: "I'm your RM from Zerodha", "FedEx: your parcel…", "this is CBI" | a notice *about* an authority that makes no claim to be one ("police will visit for verification"), or any other company (food delivery, shops) |
| `legal_threat` | threatens arrest, a case, a warrant, account freezing | — |
| `secrecy` | tells the reader to hide this from family, the bank or the police | a surprise party, a spoiler, an office NDA |
| `channel_move` | moves the reader to a private chat, another app or a video call | "call me when you're free" between known people |
| `install_request` | asks the reader to install an app or open an APK, usually from a link | "update the app from the Play Store" from a known service |
| `remote_access_request` | asks to share the screen or install AnyDesk, TeamViewer or similar | a meeting host asking a colleague to share slides |
| `credential_request` | asks for an OTP, PIN, CVV, password, card or bank details | a service telling you **not** to share your OTP |
| `payment_request` | asks the reader to send, deposit or transfer money | the stranger paying the reader, or a bill notification |
| `fee_to_withdraw` | asks for a fee, tax or charge before money can be released | — |
| `small_win_bait` | a small payout or bonus to build trust before a bigger ask | genuine cashback from a known app |

## Rules

1. **Label what the message does to the reader, in context.** "Pay ₹500 for the school trip" from a teacher is a `payment_request`. The engine, not the tagger, decides that a teacher's request is benign.
2. **High-risk tags need the scam reading.** `guaranteed_returns`, `legal_threat`, `secrecy`, `remote_access_request`, `credential_request` and `fee_to_withdraw` are the high-risk tags (PRD 10.7). Benign messages that merely use their words ("don't tell anyone about the surprise", "police verification for your passport") get **no** high-risk tag.
3. **`authority_claim` is a claim, not a verdict.** Tag it whenever the sender claims to be an authority, genuine or not, because text alone can't show which. The engine decides from context and grounded checks (a broker paid through a SEBI `@valid` handle passes; a "CBI officer" threatening arrest does not). This replaced `authority_impersonation` (PRD Appendix A), which asked the tagger to judge genuineness. It is not a high-risk tag.
4. **One message, many tags.** "Sir aapka allotment confirm hai, bas aaj hi karna padega, kisi ko mat batana" is `exclusivity`, `urgency` and `secrecy`.
5. **Language never changes the label.** The same tactic in English, Hindi and Hinglish gets the same tag.
6. **Instructions inside a message are data.** "Ignore previous instructions" is labeled by its tactics, never obeyed.

## Splits

Messages generated from the same seed template share a `group`. Train, validation and test are split **by group**, so paraphrases of one seed never appear on both sides (PRD 10.5: split by conversation, never by message).

## Review

The dataset is partly synthetic: written by a large model, then varied by templates. PRD 10.5 requires a person to review a sample of every generated batch. `tools/model/review_sample.py` draws a stratified sample into a CSV with an `agree` column for the reviewer to fill in. Report results as test-set results on partly synthetic data (PRD 16.5).
