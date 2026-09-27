<div align="center">

<img src="desktopApp/icons/openflux.png" width="112" alt="OpenFlux">

# OpenFlux

**Клиент [OpenFlux](https://github.com/p1neappleXpress/OpenFlux) для Windows, macOS, Linux и Android:
профили, своя нода на VDS в пару кликов и весь трафик устройства через туннель.**

[![Latest release](https://img.shields.io/github/v/release/meepo161/OpenFluxClient?label=%D1%80%D0%B5%D0%BB%D0%B8%D0%B7&color=4F7CFF)](https://github.com/meepo161/OpenFluxClient/releases/latest)
[![Release](https://github.com/meepo161/OpenFluxClient/actions/workflows/release.yml/badge.svg)](https://github.com/meepo161/OpenFluxClient/actions/workflows/release.yml)
[![Core](https://img.shields.io/badge/%D1%8F%D0%B4%D1%80%D0%BE-p1neappleXpress%2FOpenFlux-314D9E?logo=go&logoColor=white)](https://github.com/p1neappleXpress/OpenFlux)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose%20Multiplatform-7F52FF?logo=kotlin&logoColor=white)

![Windows](https://img.shields.io/badge/Windows-10%20%7C%2011-0078D4?logo=windows&logoColor=white)
![macOS](https://img.shields.io/badge/macOS-Apple%20Silicon%20%7C%20Intel-000000?logo=apple&logoColor=white)
![Linux](https://img.shields.io/badge/Linux-x64-FCC624?logo=linux&logoColor=black)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[Скачать](#-скачать) · [Возможности](#-возможности) · [Своя нода](#-своя-нода-на-vds) · [Сборка](#-сборка-из-исходников) · [Благодарности](#-благодарности)

</div>

> [!IMPORTANT]
> **Спасибо [p1neappleXpress](https://github.com/p1neappleXpress)!** Всё, что умеет этот клиент, держится на
> [OpenFlux](https://github.com/p1neappleXpress/OpenFlux): ядре, транспортах и протоколе, которые он придумал и развивает.
> Клиент собирается ровно с его ядром (ветка `hotfix/oflx-encrypted-logging-context`), без собственных правок транспорта.

---

## ✨ Возможности

| | |
|---|---|
| 🔌 **Подключение в одно нажатие** | Большая кнопка на главной и живой статус от ядра: подключено или переподключается, скорость, объём, активный транспорт, внешний IP. |
| 🌐 **Весь трафик устройства** | На Android — VPN, на Windows — TUN на Wintun: браузеры, игры, мессенджеры и UDP идут через ноду. |
| 🧭 **Системный прокси Windows** | Включается при подключении; прежние настройки возвращаются при отключении, выходе и после сбоя. На macOS и Linux — SOCKS5 и HTTP-прокси на `127.0.0.1`. |
| 🛰️ **Своя нода на VDS** | Мастер ставит канал на ваш сервер по SSH, сам создаёт документ в Яндексе и проверяет, что трафик выходит с адреса сервера. |
| 🧾 **Профили** | Импорт `openflux://` и QR-кодов (камерой, из файла или буфера), ручное создание, режим Session с несколькими транспортами и приоритетами. |
| 🔎 **Встроенный браузер** | Вход в Яндекс и его проверки прямо в приложении; проверки ноды открываются с её адреса. |
| 🖥️ **Выходная нода** | Устройство само выпускает в интернет других: QR-код для клиентов на главной. |
| 📜 **Журнал** | Поиск, фильтр, уровни подробности ядра, скрытие ключей и ссылок. |

На компьютере — трей, светлая и тёмная тема, адаптивная раскладка, горячие клавиши и контекстные меню: программа ведёт себя как настольная, а не как растянутый телефон.

## 📦 Скачать

Все сборки лежат в одном релизе: **[Releases → последний](https://github.com/meepo161/OpenFluxClient/releases/latest)**. Рядом `SHA256SUMS.txt` для проверки.

| Система | Файл | Что это |
|---|---|---|
| 🪟 **Windows** 10/11 x64 | `OpenFlux-X.Y.Z-windows-x64.msi` | Установщик для одного пользователя, без прав администратора |
| | `OpenFlux-X.Y.Z-windows-x64-setup.exe` | То же в виде `.exe` |
| | `OpenFlux-X.Y.Z-windows-x64-portable.zip` | Без установки: распакуйте и запустите `OpenFlux.exe` |
| 🍎 **macOS** | `OpenFlux-X.Y.Z-macos-arm64.dmg` | Apple Silicon (M1 и новее) |
| | `OpenFlux-X.Y.Z-macos-x64.dmg` | Intel |
| 🐧 **Linux** x64 | `OpenFlux-X.Y.Z-linux-x64.deb` | Debian, Ubuntu, Mint: `sudo apt install ./OpenFlux-X.Y.Z-linux-x64.deb` |
| | `OpenFlux-X.Y.Z-linux-x64.tar.gz` | Любой дистрибутив: распакуйте и запустите `OpenFlux/bin/OpenFlux` |
| 🤖 **Android** 8.0+ | `OpenFlux-X.Y.Z-android-…-arm64-v8a-release.apk` | Почти все современные телефоны |
| | `…-armeabi-v7a-release.apk` | Старые 32-битные телефоны |
| | `…-universal-release.apk` | Если не уверены — подходит всем, но весит больше |

Ядро OpenFlux (и `wintun.dll` в Windows) уже внутри. Встроенный браузер на компьютере (~230 МБ) скачивается с CDN JetBrains при первом входе в Яндекс.

> [!TIP]
> **macOS:** сборки не подписаны — при первом запуске откройте приложение через правый клик → «Открыть».
> **Android:** APK из релиза обновляет прежнюю версию из релизов. Сборку, сделанную самостоятельно, сначала удалите — у неё другая подпись.

## 🚀 Быстрый старт

1. **Есть ссылка или QR от владельца ноды** → «Профили» → «Импорт» → вставьте `openflux://…`, выберите картинку с QR или отсканируйте камерой.
2. **Есть свой VDS** → «Профили» → «+» → «Создать свою ноду на VDS» ([как это работает](#-своя-нода-на-vds)).
3. Нажмите большую кнопку на главной. Внешний IP в карточке «Подключение» должен смениться на адрес ноды.

<details>
<summary><b>Какой режим выбрать на компьютере</b></summary>

| Режим | Что идёт через ноду | Права администратора |
|---|---|---|
| **Системный прокси** (по умолчанию) | Браузеры и большинство программ, которые смотрят на прокси Windows | Не нужны |
| **Весь трафик компьютера** | Всё: любые программы, игры, UDP. IPv6 блокируется, чтобы ничего не утекало мимо туннеля | Нужны: на главной есть кнопка «Перезапустить от имени администратора» |
| **Только SOCKS5 / HTTP** | Программы, которым вы сами указали `127.0.0.1:1080` (SOCKS5) или `:1081` (HTTP) | Не нужны |

</details>

## 🛰️ Своя нода на VDS

<table>
<tr>
<td width="50%"><img src="docs/images/wizard-server.png" alt="Мастер: сервер"></td>
<td width="50%"><img src="docs/images/wizard-done.png" alt="Мастер: нода готова"></td>
</tr>
</table>

Мастер ставит на сервер **отдельный канал** со своим документом, ключом, портом и systemd-юнитом. Запустите его ещё раз на том же сервере — появится ещё один независимый канал для другого пользователя, существующие не изменятся.

1. **Сервер.** Адрес, SSH-порт, логин, пароль или ключ. Отпечаток ключа сервера показывается для сверки и запоминается: подмена ключа не пройдёт молча.
2. **Документ.** Вход в Яндекс во встроенном браузере: мастер сам создаёт документ с доступом «Редактирование» по ссылке. Имя документа — название ноды, дата и случайные буквы, без адреса сервера. Можно вставить и свою ссылку.
3. **Изменения.** Сервер показывает, что именно будет сделано; для `sudo` спрашивается пароль.
4. **Проверка.** Мастер подключается через новую ноду и сравнивает внешний IP с адресом сервера. Профиль сохраняется или отдаётся другому устройству QR-кодом.

Нужен Linux с systemd (Debian, Ubuntu и похожие) и root или `sudo`. Скрипт установки скачивается по закреплённому коммиту и проверяется по SHA-256, ядро ноды — по закреплённым хешам воспроизводимой сборки.

## 🔒 Конфиденциальность

- **Пароли SSH и sudo, приватный ключ** живут только в памяти на время мастера и нигде не сохраняются.
- **Встроенный браузер** держит кэш в памяти и стирает cookies после каждого использования; вход в Яндекс остаётся только в cookies, переданных ноде (от этого можно отказаться на шаге «Изменения»).
- **Ключ канала и `.conf`** пишутся на диск только на время подключения; журнал по умолчанию скрывает ключи и ссылки на документы.

Где лежат данные на Windows: `%APPDATA%\OpenFlux` — профили и настройки; `%LOCALAPPDATA%\OpenFlux` — встроенный браузер и его журналы `browser.log` / `browser-cef.log`.

<details>
<summary><b>Горячие клавиши</b></summary>

| | |
|---|---|
| <kbd>Ctrl</kbd>+<kbd>1</kbd>…<kbd>4</kbd> | Разделы |
| <kbd>Ctrl</kbd>+<kbd>Enter</kbd> | Подключить / отключить |
| <kbd>Ctrl</kbd>+<kbd>N</kbd> | Новый профиль |
| <kbd>Ctrl</kbd>+<kbd>I</kbd> | Импорт ссылки или QR |
| <kbd>Ctrl</kbd>+<kbd>S</kbd> | Сохранить |
| <kbd>Esc</kbd> / <kbd>Enter</kbd> | Отмена / главная кнопка диалога |

</details>

## 🧩 Ядро

Клиент собирает ядро из [`meepo161/openfluxfork`](https://github.com/meepo161/openfluxfork) (ветка `fork-main`). Это ядро [p1neappleXpress/OpenFlux](https://github.com/p1neappleXpress/OpenFlux) из ветки `hotfix/oflx-encrypted-logging-context` как есть, плюс:

- `mobile/` — мост gomobile, из которого собирается `openflux.aar` для Android-приложения;
- закреплённый установщик ноды (`deploy/node-install.sh`, `provision/pin.go`) и согласованные релизы.

## 🛠️ Сборка из исходников

Нужны JDK 17, Go и git; для Android — ещё Android SDK с NDK 27 и `gomobile`.

Файлов ядра в git нет. `run` и `package*` сами собирают ядро для этой системы в `desktopApp/resources/<windows|macos|linux>` (для Windows ещё скачивают `wintun.dll` с проверкой SHA-256), если его там ещё нет: из `-PcoreDir=<путь>`, из `../OpenFlux` рядом с репозиторием или из свежего клона `fork-main`. Чтобы пересобрать ядро, удалите его из `resources/<os>`; `-PskipCore=true` запускает приложение без ядра.

```bash
./gradlew :desktopApp:run                              # запустить (ядро соберётся при первом запуске)
scripts/build-core.sh ../OpenFlux                      # ядро вручную, в т. ч. в Docker без Go
scripts/build-android-core.sh ../OpenFlux              # openflux.aar для Android
./gradlew :shared:jvmTest                              # тесты
./gradlew :androidApp:assembleDebug                    # Android: APK
./gradlew -PwindowsPackage=true :desktopApp:packageMsi # Windows: .msi (и packageExe)
./gradlew :desktopApp:packageDmg                       # macOS: .dmg
./gradlew :desktopApp:packageDeb                       # Linux: .deb (нужен fakeroot)
```

В настройках можно указать и свой файл ядра — `wintun.dll` должен лежать рядом с ним.

<details>
<summary><b>Как выходят релизы</b></summary>

Версия задаётся одним тегом `vX.Y.Z` в [`meepo161/openfluxfork`](https://github.com/meepo161/openfluxfork):

```bash
git tag -a v2.5.0 -m "OpenFlux 2.5.0" && git push origin v2.5.0   # в репозитории ядра
```

Workflow ядра запускает здесь [`release.yml`](.github/workflows/release.yml) с этим тегом как неизменяемым `core_ref`. Он собирает Windows x64, Linux x64, macOS Apple Silicon и Intel, Android APK — каждый с ядром из этого тега — и публикует всё одним релизом с `SHA256SUMS.txt`. После этого ядро публикует свой релиз с бинарниками выходной ноды того же номера. Для этого у ядра должен быть секрет `CLIENT_RELEASE_TOKEN` с правом **Actions: write** к этому репозиторию.

Перед публикацией каждая сборка проверяет, что в пакет попали нативные библиотеки Skia для своей платформы, ядро и `wintun.dll`. Ручной запуск («Run workflow») с `publish=false` собирает проверочные артефакты без релиза.

</details>

<details>
<summary><b>Устройство</b></summary>

```
desktopApp/        окно, трей, горячие клавиши, упаковка, иконки (icons/openflux.svg)
androidApp/        Android: VPN-сервис, WebView, камера, ядро через openflux.aar
shared/
  commonMain/      модели (Profile, CoreConfig, ConnectionState, NodeWizard),
                   интерфейсы сервисов, дизайн-система и экраны — общие для всех платформ
  jvmMain/
    core/          запуск ядра, IPC-статус, проверки Яндекса
    node/          мастер своей ноды (ядро --node-wizard по stdin/stdout)
    web/           встроенный браузер: KCEF off-screen, прокси-маршрутизатор, журнал
    platform/      системный прокси Windows (WinINet), права администратора
    data/          хранение настроек и профилей
```

Поток данных: экран → `ScreenModel` (Voyager) → сервисы; UI не трогает ни файлы, ни процессы.

На компьютере ядро — отдельный Go-процесс: SOCKS5 и HTTP-прокси на `127.0.0.1` или адаптер Wintun, статус и проверки Яндекса по IPC. В режиме «Весь трафик» сокеты самого ядра привязаны к физическому интерфейсу, поэтому транспорты не зацикливаются в собственный туннель. На Android то же ядро работает внутри приложения как библиотека.

</details>

## 💙 Благодарности

- **[p1neappleXpress](https://github.com/p1neappleXpress)** — автор [OpenFlux](https://github.com/p1neappleXpress/OpenFlux): ядра, транспортов через Яндекс, Mail.ru, MAX и другие сервисы, шифрования и протокола согласования, а также [OpenFluxAndroid](https://github.com/p1neappleXpress/OpenFluxAndroid). Без его работы этого клиента бы не было — спасибо!
- **[damnurmum](https://github.com/damnurmum)** — автор [OpenFlux-Android](https://github.com/damnurmum/OpenFlux-Android) и улучшений ядра: ссылки и QR-коды `openflux://`, новый протокол cups.online.
- Всем, кто присылал ошибки, тестировал ноды и транспорты и отправлял PR в OpenFlux.

Мейнтейнер клиента — **[@meepo161](https://github.com/meepo161)**. Вопросы, ошибки и предложения — в [Issues](https://github.com/meepo161/OpenFluxClient/issues).

## ⚖️ Отказ от ответственности

OpenFlux — исследовательский сетевой инструмент. Проект некоммерческий, без платных функций. Используйте его в рамках законов и правил сервисов, с которыми он работает; ответственность за применение лежит на пользователе.
