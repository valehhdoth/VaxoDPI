# VaxoDPI: Android DPI Bypass

**VaxoDPI** is an Android DPI bypass interface. Connect with a single tap and regain access to blocked sites and apps (for example, Discord) on networks that use deep packet inspection.

[![Latest release](https://img.shields.io/github/v/release/valehhdoth/VaxoDPI)](https://github.com/valehhdoth/VaxoDPI/releases/latest)

## Download

Get the latest APK from the [Releases](https://github.com/valehhdoth/VaxoDPI/releases/latest) page.

## Features

- One-tap connect and disconnect
- Connection timer and network metrics
- **Modes:** Default, Split, Disorder. If a site doesn't open, pick another mode; changes apply on the next connection.
- Turkish, English and Russian interface
- Demo (preview) mode for viewing interface states only. It makes no real connection.

### Coming soon

Per-app split, DNS settings, auto-connect.

## How it works

Android VpnService sends device traffic to the HEV Socks5 Tunnel, which forwards it to VaxoProxy, VaxoDPI's own SOCKS5 engine written in Java, listening on 127.0.0.1. For HTTPS connections VaxoProxy applies TLS record splitting and TCP segment tricks (split, low-TTL first fragment) only to servers that appear to be blocked, so unblocked sites are not slowed down. Traffic is not sent through any remote VPN server, and your IP address does not change. The app has no analytics or ads.

## Credits and license

- Tunnel: [HEV Socks5 Tunnel](https://github.com/heiher/hev-socks5-tunnel) by heiher, MIT License. Its license notice is preserved in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- VaxoDPI's own code is released under the [MIT License](LICENSE).

## Play Protect warning

Android may show a Play Protect warning when installing the APK. This is expected: the app is signed but is not distributed through the Play Store. Tap "More details" and then "Install anyway".

## Disclaimer

You are responsible for using this app in accordance with the laws of your country. The software is provided "as is", without warranty of any kind.

## Contact

Developer: Valeh · [valeh.xyz](https://valeh.xyz) · valehxyz@gmail.com
