# VaxoDPI: Android DPI Bypass

**VaxoDPI** is an Android DPI bypass interface. Connect with a single tap and regain access to blocked sites and apps (for example, Discord) on networks that use deep packet inspection.

[![Latest release](https://img.shields.io/github/v/release/valehhdoth/VaxoDPI)](https://github.com/valehhdoth/VaxoDPI/releases/latest)

## Download

Get the latest APK from the [Releases](https://github.com/valehhdoth/VaxoDPI/releases/latest) page.

## Features

- One-tap connect and disconnect
- Connection timer and network metrics
- **Modes:** Default, Split, Disorder, OOB. If a site doesn't open, pick another mode; changes apply on the next connection.
- Turkish, English and Russian interface
- Demo (preview) mode for viewing interface states only. It makes no real connection.

### Coming soon

VPN service, per-app split, DNS settings, auto-connect.

## How it works

VaxoDPI uses the [ByeDPI](https://github.com/hufrea/byedpi) engine to get around DPI-based blocking. The engine alters how outgoing connection packets are sent (for example TLS split and out-of-order sending) so that DPI systems cannot recognize them. VaxoDPI adds an Android interface and mode selection on top of it.

## Credits and license

- VaxoDPI is based on [ByeDPI](https://github.com/hufrea/byedpi) by **hufrea**, licensed under the MIT License. ByeDPI's copyright and license notice is preserved in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- VaxoDPI is not developed by the author of ByeDPI and is not an official ByeDPI release.
- VaxoDPI's own code is released under the [MIT License](LICENSE).

## Disclaimer

You are responsible for using this app in accordance with the laws of your country. The software is provided "as is", without warranty of any kind.

## Contact

Developer: Valeh · [valeh.xyz](https://valeh.xyz) · valehxyz@gmail.com
