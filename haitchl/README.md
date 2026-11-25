# HaitchL – DonutSMP gambling companion (Fabric, Minecraft 26.3)

Client-side only. It reads chat and shows info. It never sends payments or bets for you.

## Features
- **Bet/payment tracker**: picks up `X paid you $…` and `You paid X $…` messages, logs them (last 50 in game, all of them in `config/haitchl-payments.csv`).
- **Balance & stats HUD**: balance, payment profit/loss, gambling profit/loss, W/L and win rate, streak and pay-wall in a HaitchL-branded panel.
- **Winstreak counter**: current W/L streak and best win streak. Picks up "you won/lost … $X" messages, or add results with `/hl win` / `/hl loss`.
- **Adjustable pay-wall**: set a min and/or max. Payments outside it are flagged in chat with a **[Refund]** button that puts `/pay <player> <amount>` in your chat box (you still press Enter yourself).
- **Chat colour changer**: recolour server messages, player chat (optional), and received / sent / win / loss messages separately.

## Build
Needs **JDK 25**.

```
./gradlew build          (Windows: gradlew.bat build)
```
The jar ends up in `build/libs/haitchl-1.0.0.jar`. You can also open the folder in IntelliJ IDEA 2025.3+ and run the `build` Gradle task.

## Install
Put `haitchl-1.0.0.jar` and **Fabric API 0.166.2+26.3** (or newer for 26.3) into `.minecraft/mods` on a profile running Fabric Loader 0.19.5+ for 26.3.

## The menu
Press **Right Shift** in game (or type `/hl`) to open the HaitchL menu. Change the key in
Options > Controls > Key Binds > HaitchL. The menu has five tabs:

- **Stats**: balance, payments, profit/loss, W/L, streak. Buttons to refresh your balance, reset the session, and add a win or loss by hand.
- **Pay-wall**: on/off switch, minimum and maximum boxes (type `10k`, `5m` etc.), and whether flagged payments count in stats.
- **Colours**: chat, received, sent, win and loss colours. Type a hex code or pick from the swatches, with a live preview. Player-chat switch.
- **HUD**: show/hide, corner presets, arrow buttons to nudge it, plus the DonutSMP-only, payments-as-W/L and file-logging switches.
- **Payments**: the last 14 payments this session.

Everything saves when you close the menu.

## Commands (optional, `/haitchl` or `/hl`)
Everything in the menu can also be done by command:

| Command | What it does |
|---|---|
| `/hl stats` | Session summary |
| `/hl log` | Last 10 payments |
| `/hl bal` | Runs `/bal` to refresh the HUD balance |
| `/hl win [amount]` / `/hl loss [amount]` | Add a result by hand |
| `/hl reset` | Reset session stats |
| `/hl paywall on\|off` | Turn the pay-wall on or off |
| `/hl paywall min 10k` / `max 5m` | Set limits (`0` = no limit) |
| `/hl paywall count yes\|no` | Whether flagged payments count in stats |
| `/hl chatcolour <#hex\|name\|off>` | Colour for server messages |
| `/hl playerchat on\|off` | Also recolour player chat |
| `/hl colour received\|sent\|win\|loss <#hex\|name\|off>` | Colours per message type |
| `/hl hud on\|off` / `/hl hud pos <x> <y>` | Show, hide or move the HUD |
| `/hl autowl on\|off` | Count received payments as wins, sent as losses |
| `/hl anyserver on\|off` | Run outside DonutSMP too (for testing) |
| `/hl reload` | Reload `config/haitchl.json` |

Amounts accept `500`, `1.5k`, `2m`, `1b`, `250,000`.

## If something isn't picked up
DonutSMP's exact chat wording can change. All the patterns live in `config/haitchl.json`
(`receivedPattern`, `sentPattern`, `balancePattern`, `winPattern`, `lossPattern`). They are Java regex with
named groups `player` and `amount`. Edit one to match the real message, then run `/hl reload`.

Check DonutSMP's current rules on client mods before using it on the server.
