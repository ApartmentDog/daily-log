# R.A.D.S. Privacy Policy

> **DRAFT, last updated 2026-11-06.** Written from how the app works today. It is not legal advice. Have a lawyer review it, add a real contact address, and re-check it against the code before a public release.

R.A.D.S. is a food and health tracking app. This page explains what it stores, what it sends off your phone, and to whom.

## The short version

- There is **no R.A.D.S. account and no R.A.D.S. server**. We do not receive your data.
- Your profile, food log, and settings are stored **on your phone**.
- The app contains **no ads, analytics, or tracking libraries**.
- Some features send data to **other companies' services** (listed below). Those services have their own privacy policies.

## What stays on your phone

- Your profile: name (optional), age, sex, height, weight, goals, health conditions, allergies, and the limits you set.
- Your food log and the foods you mark as safe.
- Your Gemini and USDA API keys, if you add them. They are stored in Android's encrypted storage.
- Data read from Health Connect (steps, weight, exercise, calories burned), if you grant permission. It is read on your phone and used in the app's own screens. It is not sent anywhere by R.A.D.S.

## What leaves your phone

| Feature | What is sent | Where |
|---|---|---|
| Searching for a food | The words you type | USDA FoodData Central (api.nal.usda.gov) and Open Food Facts (world.openfoodfacts.org) |
| Scanning a barcode | The barcode number | Open Food Facts; USDA for related lookups |
| Photo or gallery logging, and some fallback estimates | The photo you choose, and/or the food description, plus a prompt | Google Gemini (generativelanguage.googleapis.com), using **your own** API key |
| Barcode scanner | Camera image is processed by Google's code scanner component | Google Play services |

Your profile (conditions, allergies, limits) is **not** included in these requests. The app applies your conditions and limits on your phone, after the food data comes back.

Requests are made over HTTPS. Because you supply your own Gemini key, usage of Gemini is billed to and governed by **your** Google account and Google's terms.

## Backups

Android may include this app's data in your device's backup to your Google account (the app allows backups). If you do not want your health log backed up, turn off app backups for this app in your phone's settings or uninstall the app to delete its data.

## Your choices

- Skip any optional field (name, conditions, allergies) in onboarding.
- Change or delete your profile in Setup, or clear the app's storage in Android settings to remove everything.
- Do not add a Gemini key and no photos or descriptions will be sent to Gemini.
- Revoke Health Connect access at any time in Android settings.

## Not medical advice

R.A.D.S. gives **general food guidance only**. It can be wrong, especially for AI photo estimates, restaurant food, and foods with incomplete data. Default limits are general starting points, not personalized advice. Always follow your doctor's or dietitian's instructions. Allergy checks only catch what is written in a food's name or ingredients and cannot detect cross-contact.

## Children

R.A.D.S. is not directed at children under 13.

## Changes

If this policy changes, the updated version will be posted here with a new date.

## Contact

[Add a contact email before release.]
