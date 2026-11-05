# 🔥 FireFix

[![Download](https://img.shields.io/github/v/release/iliakzm/FireFix-keyboard-app?label=Download&style=for-the-badge&color=orange)](https://github.com/iliakzm/FireFix-keyboard-app/releases/latest)

a windows keyboard program for edit wrong text language and typing sounds.
You can set a different keyboard sound for each language, so you'll know which language you're typing in just by listening.
This way, your keyboard becomes mechanical too!😎


- **Fix text** – typed a sentence with the wrong layout active? Select it, press the hotkey (default `Ctrl+Shift+F9`), and it is converted (e.g. `سلام` ⇄ `sfhl`). Supports English, Persian, German and Russian layouts.
- **Typing sounds** – mechanical keyboard sound packs (Keyboard Mash, Thumb Taps, Soft Crisp Typing, Retro Terminal). Toggle with the hotkey (default `Ctrl+Shift+F10`).
- **Converter** – convert any text between layouts manually.
- Optional start with Windows, runs quietly in the system tray.

## Download

Download the latest FireFix-Setup.exe from Releases.
you can turn on run as startup botten to start program by starting your pc.

## Build from source

Requirements: Windows, JDK 17+ (with `jpackage`), Maven, and [WiX Toolset 3.x](https://wixtoolset.org/) for building the `.exe` installer.

```bat
git clone https://github.com/iliakzm/FireFix_keyboard.git
cd FireFix_keyboard
build-installer.bat
``
## Tech

Java 17 · Swing · FlatLaf · JNativeHook · JNA · jpackage

## Contact

- Telegram: [@ilia_k2](https://t.me/ilia_k2)
- Email: iliyaKazemi599@gmail.com

## License

[MIT](LICENSE)
