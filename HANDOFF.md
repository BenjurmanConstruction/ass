# Easy Wake: handoff notes

Paste this into a new chat to carry on: *"Continue my Android app Easy Wake. Repo is
BenjurmanConstruction/ass on GitHub. Read HANDOFF.md first."*

## What it is

A free, ad-free Android alarm clock that grew into an "everything" app. I'm not a
coder; Claude writes all of it. The brain gym tests are the engine: they earn
Knowledge Points (KP) that unlock things on a skill tree.

## How builds work

- Pure Java, no AndroidX, no libraries. Package `app.easywake`, minSdk 26, target 34.
  All screens are built in code (only `res/layout/time_picker.xml` is XML).
- GitHub Actions (`.github/workflows/build.yml`) builds on every push to `main`,
  signs with `signing/easywake.jks` (password `easywake`, alias `easywake`) and
  publishes release `vN` with `EasyWake-vN.apk`. Version name is `1.N`.
- Download link: `https://github.com/BenjurmanConstruction/ass/releases/download/vN/EasyWake-vN.apk`
- Install on my Samsung: turn Auto Blocker off, install over the old app, turn it back on.
- Commit with a message ending in `[skip ci]` to push without making a new release.
- Latest release: **v30**.

## Standing rules

- Fun Coupons, Gold and KP are play money only. Never bought or cashed out for real
  money; no real-money gambling version.
- Market features are information only, not financial advice.
- Existing features stay unlocked; only new ones go behind the skill tree.
- Don't copy trademarked games' names or art (Tetris, Peggle, Triple Diamond): make
  original versions under our own names.
- Show the odds/payback for every casino game.
- Keep my email private.

## What's in v21

**Alarms and sleep:** alarms with MP3 or tones, pick a section of a song, fade-in,
vibrate modes, big snooze, wake screen with today's events. Sleep sounds generated
live in stereo with no loop: box fan, white, pink, brown, big floor fan, rain,
airplane cabin, or my own recording (blends the join). Sliders for tone, hum and
rumble. **Real fan** (v21): a real recording (`res/raw/real_fan.ogg`, by unfa on
Freesound; license not yet confirmed, check before any Play Store release) played by
`FanMixer`: split into hum (<400 Hz, phase-matched blends, same in both ears) and air
(random pieces per ear), so it never loops. `RealFan` decodes it. Hum and Tone sliders
work on it (tone 40 = as recorded).

**Look:** 10 accent colors, night dim and red night mode, background studio (draw,
or import a photo and crop, filter, brightness, text, save to gallery).

**Brain gym:** 24 tests (`Games`, `GameActivity`, `GameActivity2`), top-5 boards,
levels, streaks, daily challenge, badges, lessons and a facts vault, Sudoku.
Trivia and Word smarts have Normal/Hard/Expert levels (score 8/10 to unlock the next).
- Trivia: about 1,170 questions. Words: about 1,080.
- Banks: `Bank`–`Bank5` are written by hand; `Bank6` (trivia) and `Bank7` (words)
  are generated from fact tables. `Bank2.trivia()/triviaHard()/triviaExpert()` and
  `vocab()...` join them and remove duplicates.
- Jokes (about 560 in `Jokes`–`Jokes4`) and facts (about 300 in `Facts`–`Facts3`)
  are dealt from a shuffled deck (`Deck`) so nothing repeats until the pile is used.
- Speed math and dot count start easy with a 3-2-1 countdown. Number grid race has
  four races (Classic, Backwards, 6x6, Shuffle).

**Skill tree** (`Tree`, `TreeView`, `TreeActivity`): branches Casino, Market, Wealth,
Life tools, Helper, Sleep, Tables, Arcade.
- Casino: Lucky Sevens slot (3 reels, 1/3/5/7 lines, wild diamond, three jackpots,
  about 95%), Royal Riches (5 reels, 10 lines, free spins, about 92%), free-spins and
  high-roller perks.
- Tables: Blackjack, Texas Hold'em (v26: no-limit, 2-6 players, see below), Video poker
  (about 99.5% with good play), Roulette (single zero, about 97%).
- Arcade: Block Drop (falling blocks; v21: 24 rows, smaller blocks, 1.2 s start speed,
  calmer held buttons), Peg Pop (v22 rework: `PegBits` holds boards and physics; ~100-150 packed
  pegs plus capsule "bricks" joined into ramps the ball rolls down; shaded 3D look; 8 new
  boards; a ball lighting nothing new for 2.5 s pops what it lit).
- **Penny Palace** (v27, inside the Slot Parlor unlock): `PennyActivity`, math in
  `PennyMath` (SlotMath machines 5-8: Jade Dragon Link hold & spin, Fortune Drums pick
  bonus, Thunder Jackpot Wheel, Lucky 7s Jackpot; all have free spins), shared jackpots in
  `JackpotBank` (MINI/MINOR/MAJOR/GRAND progressives + 2 must-hit-by mysteries; saved in
  Prefs `jackpotBank`). Credits: 100 credits = 1 coupon, kept in Prefs `pennyCredits`,
  cashed out as a ticket. Each spin rolls once for a progressive (chance per credit bet);
  machines just present it. ~98% total payback incl. jackpots (4M-spin sims). Owner chose
  progressives only; didn't want pick/wild/collect themes beyond that. Owner wants free
  spins on machines.
- **Texas Hold'em** (v26): `HoldemTable` (pure engine: NL betting, all-ins, side pots;
  tested 25k hands, no chip leaks), `HoldemBot` (8 characters with styles, tilt, and lots
  of trash talk; owner wants them to talk a LOT, especially when winning), `HoldemActivity`
  (lobby with blinds/table size/buy-in, drawn table, bubbles, chat log, raise slider,
  quick-chat replies). Your table stack is saved in Prefs `hemStack` and returned to the
  wallet if the app closes.
- **Slot Parlor** (v23, `ParlorActivity`, `MachineActivity`, `SlotMath`): outside the skill
  tree, one 75 KP unlock (id `parlor` in `treeOwned`), entry at top of Games and tools.
  Five machines, all math in `SlotMath` with frames played back by `MachineView`:
  Blazing Bars (3-reel, 5 lines, 94.2% exact), Thunder Plains (1,024 ways, free spins with
  2x/3x wilds, 93.6%), Coin Vault (243 ways + hold & spin, Mini/Minor/Major/Grand, Grand
  ~1 in 270k, 94.3%), Jackpot Wheel (1 line + wheel, 93.2% exact), Gem Cascade (pay
  anywhere, tumbles, growing free-spin multiplier, 96.1%). Paybacks from 5M-spin sims;
  re-run a sim after any pay change and update the odds text in `MachineActivity`.
  v24: owner found v23 joyless, so all five were retuned for fun (~98-99% payback,
  bonuses 2-3x more often, more wins above the bet) and made interactive: tap reels or
  SPIN to stop early, tap the resting machine to spin, tap to start each bonus, Thunder
  Plains lets you pick free spins (all x1, half x2, a third x3), BIG/MEGA/EPIC WIN
  celebration at 10x/25x/50x, chasing cabinet lights. Owner prefers fun over realism.
- Paper trading **Performance page** (v25, `PerformanceActivity`, math in `PaperPerf`):
  account value over time vs the same money in SPY, scoreboard (realized/unrealized,
  win rate, avg win/loss, best/worst sale, worst dip), holdings with weights and today's
  change, allocation bar. History is rebuilt by undoing trades from current holdings and
  pricing each day at the close. `Book` now stores structured events (`t`, flag `ev`);
  the old 60-line text log is parsed once into events (`Book.readOldLog`). Every trade,
  deposit, reset and open logs an event.
- Market: portfolio, paper trading (1 KP = $10), price alerts (background check
  about every 15 min), economic calendar (Fed, minutes, CPI, PPI, PCE, GDP, jobs
  through Dec 2027, with live numbers from FRED), market Q&A.
- Life tools: weather (Open-Meteo), goals, reminders, recipes, water reminder.
- Helper: safe lookups (Wikipedia and a dictionary).
- Removed: the market news feed (its 40 KP is refunded automatically).

**Helper** (`AssistantActivity`, `Helper2`, `Calc`): keyword matching, not an AI.
Opens with nine topic buttons, and number questions open a small form. It does
alarms, memos, timers, sums, unit conversions, job-site sums (concrete, drywall,
paint, stairs, board feet), tips, dates, dice, market quotes, lookups and app
progress.

**Other:** profile with stats, activity log, 14-day chart and a weekly verdict from
"Scholar" to "Degenerate gambler" (`Stats`, `ProfileActivity`). Game sounds made in
code (`Sfx`), with on / wins only / off. Settings has an internet switch, game sounds,
player name and uninstall.

### 3D casino floor (v28)
- My tools → "Walk the casino (3D)". Free; each game inside still needs its own unlock.
  Landscape, OpenGL ES 2.0, no engine. Files: CasinoArt (all textures, drawn by code through
  a Painter), CasinoWorld (pure Java: room, colliders, game spots, people, punching,
  security, camera), CasinoActivity (GL renderer, joystick, look-drag, Punch/Play buttons,
  speech bubbles, live jackpot board refreshed every 2 s from Prefs "jackpotBank").
- Layout: Penny Palace + live jackpot board (NW wall), penny island with seated regulars,
  Slot Parlor (N wall), classic slots island (Lucky Sevens / Royal Riches), Hold'em table
  with Rusty, Big Tony, Grandma Mae, Chad, Jolene (they trash-talk), blackjack, roulette,
  video poker bar (E wall), cashier cage (SW, opens Wallet). Door/spawn on the south wall.
- Punching: hit someone and they fly back, fall, get up and mouth off; ~30% are "tough" and
  swing back. 3 hits in 60 s sends Security, who walks you out (fade, back to the door).
  Sfx.PUNCH / Sfx.WHOOSH added.
- Blood (v29, user asked): blocky red spray on every hit, splats on the carpet that fade after
  45 s (max 140), bloody nose + shirt stain for 12 s, drips while standing. Still cubes, no gore.
- Preview without a phone: /home/claude/work/casino/Preview.java is a software renderer
  using the same lighting as the shader; it writes PNGs of several views.

### The 3D world, v30: the strip, secrets, lockpicking, contracts
- Files: CasinoWorld (engine: thick walls with doorways, regions with ceilings, zones,
  baked light map, doors NPCs open, cars, suspicion, takedowns, bodies, disguises, pistol,
  actions), CasinoPlaces (every place + chips/notes/loot/hide spots + patrol routes),
  CasinoMissions (4 contracts: counter/Dexter Pike 800, fixer/Nadia Kroll 1000,
  gm/Richard Vance 1200, loan/Vinnie Marrow 1500; payout() and rating()), LockPick (pure
  pin-tumbler logic), CasinoActivity (GL + overlay + LockView + phone dialogs).
- Map (x west-east, z north-south): casino -18..18 x -14..14; back rooms z -24..-14
  (corridor, High Limit room [key card "hlkey" in GM desk], vault 6 pins, security office,
  GM office 4 pins + safe 5 pins); staff door 3 pins at (15.8,-14); back door 5 pins at
  (18,-15.5) to the alley x 18..26; parking lot x -48..-18; strip: sidewalk z 14..18,
  street z 18..26 (crosswalks x -14 and 22), sidewalk 26..30; across: Lucky Pawn (shop:
  5 picks/25 coupons, pistol 750), Midnight Diner (freezer), Starlite Motel room 7 (4 pins),
  empty lot; The Last Call bar x 26..48 z 4..14 (4 pins).
- Saved in Prefs: "casinoFlags" (comma list: door:, loot:, chip:, note:, key:, item:pistol,
  allchips), "lockpicks" (starts 5), "hitDone_<id>" (repeat jobs pay less).
- 12 lucky chips (all 12 = 2,500 coupons once), 9 lore notes, stashes 120..1500 coupons.
- High Limit room machines open MachineActivity with extra "vip" (bets 500..5000).
- Contracts: takedown from behind (targets die, others KO), drag + hide bodies, take
  uniforms (staff/cook = staff zones, security = private zones; vault = nobody), eye meter,
  alarm -> guards chase -> thrown out to the sidewalk. Silent +50%, accident (car/push)
  +25%, -20% per bystander killed.
- Logic tests: /home/claude/work/casino/T2.java (routes loop, silent takedown, hiding,
  alarm, body found, locks, car accident). Previews: Preview.java + Views.java.

## Ideas not built yet

- Fan sounds: the v21 "Real fan" recording is mostly 60 Hz hum, inaudible on a phone
  speaker. Six phone-friendly fan samples were made (box, window, AC, purifier,
  oscillating, CC0 ceiling-fan recording from localhostnick/-open-sleep-sounds); waiting
  on the owner's pick to build them in.

- Backup and restore of points, unlocks and stats (recommended next).
- Daily brain workout, achievements wall, sleep log, habit tracker.
- Home-screen widget.
- Slot bonus rounds; pass-and-play two-player mode.
- Themed trivia packs; more Expert trivia (it's the smallest pool).
- Multiplayer online would need a paid server; a real AI chatbot needs a paid key.
- Publishing on the Play Store ($25, plus a testing period).

## Notes for Claude

- The workspace has no Android SDK and no network installs, so builds only happen
  on GitHub. Check syntax locally with `javac -proc:none` and ignore the
  missing-Android-class errors.
- Edit with small Python patch scripts that assert each match. Files hold real
  characters like `•`, not `•` escapes, so match on those.
- After pushing, poll `gh api repos/BenjurmanConstruction/ass/actions/runs`.
