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
- Latest release: **v40**.

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

### v31: cars, map, jobs screen, stores, bling, fairer lockpicking
- Cars: CasinoWorld.drawCar (SEDAN/SUV/TAXI/PICKUP, rolling hex wheels, headlight pool);
  moving cars array {x, lane, dir, speed, color, spin, type}; parked cars in world.parked.
  Everything past DRAW_FAR (42 m) isn't drawn.
- HUD: coupons pill (+gain flash), Jobs / Bag / Phone buttons, rotating minimap (tap = full
  map with place names, MapView), red target marker or edge arrow + distance, target line
  with place name (CasinoPlaces.placeName / PLACES).
- Doors have `access` (1 staff, 2 security): matching uniform opens them, no pick.
  Picking suspicion: guards 0.9 (<6 m) / 0.4, staff 0.45 (<8 m), civilians 0.3 (<4.5 m);
  decay 0.25/s. Lock screen shows the eye + who's watching, closes on alarm. Tested
  (T5.java): careful player never caught at staff/back/room 7/bar doors; corridor needs a
  staff uniform.
- CasinoShop: items for Threads (tailor, x -6..0 z 30..42), Gold Rush Jewelers (x -48..-40),
  and pawn gear (pro picks never snap, soft-sole shoes = 0.7x suspicion). Owned = flag
  "own:<id>", worn = Prefs "casinoStyle" (comma list per slot). Bling drawn in drawPerson
  (jacket/suit, chain levels, watch, glasses, hats incl. crown, shoes).
- Contracts have `tips`; best rating saved in Prefs "hitBest_<id>".

### v32: weapons, aiming, guards shoot back, practice range
- CasinoWeapons: knife 400, pistol 750 (+silencer 1000), .44 revolver 1200, SMG 4000 (auto),
  pump shotgun 3500 (8 pellets), bolt rifle 6000 (scope fov 16). Owned = "own:<id>"
  (old "item:pistol" maps to own:pistol); in hand = Prefs "casinoWeapon".
- Concealable guns are invisible until you aim; long guns show on your back and add
  suspicion (guards 0.5/s, civilians close 0.15/s) unless in a security uniform. Aiming
  outside the range: guards 1.2/s, others 0.6/s, some panic.
- Aiming: aimCamera() over the right shoulder (slides in front of walls), aimPitch,
  aimLook(); fire() casts real rays: rayWorld (walls/doors/ground), rayPerson (head sphere +
  body cylinder; lying bodies a low box), rangeHit. Health 100 (guards 150); headshot
  multiplier per gun. Tracers, sparks, muzzle flash, hit markers, recoil, bloom.
- heard(loud): gunshots within loud*28 m make civilians flee, guards alarm; silencer = 0.12.
  Once you hit someone with a gun, alarmed guards keep distance and shoot back
  (guardFires); player health regenerates after 5 s; at 0 you're "downed" -> wake up outside,
  contract aborted. One guard downs a still player in ~23 s.
- Bullseye Range (old empty lot, x 34..48 z 30..42): gun counter (spot id "range"), 40 s
  rounds of pop-up targets (12 in 3 rows), body 10 / head 25 / +5 per row; prize score/4
  coupons; best in Prefs "rangeBest". Shots in the range area alarm nobody.
- HUD: FIRE (hold for SMG) / PUNCH / AIM-LOWER / RELOAD / weapon switch with ammo,
  crosshair with bloom, scope overlay, HEADSHOT marker, health bar + red edges, round timer.
- Tests: /home/claude/work/casino/T6.java (body/head shots, aimbot range round, guard
  takedown, silenced vs loud).

### v33: the city, driving, police and wanted stars
- World is now x -112..112, z -82..80 (LM_STEP 0.33). CasinoCity: road grid avenues
  x = -92, -54, 54, 92 and streets z = -62 (North Ring), -36 (Back Street), 22 (The Strip),
  60 (Harbor Road); 8 m roads, 2 m lane offset, 3 m sidewalks; generated buildings with lit
  windows (seeded), skyline past the ring, street lamps; Police HQ block x 58..88 z -32..18
  (3 parked cruisers); Spray Shop garage x -76..-64 z 30..41 (booth CasinoCity.SPRAY).
  GROUND list feeds the map bitmap. The alley now opens onto Back Street.
- CasinoTraffic: all cars. AI traffic runs on rails (lane lines + Bezier turns, junction
  reservation so crossing cars wait), queues behind same-direction cars, brakes for people;
  hard hits knock a car off its rail and it steers back. Player driving: stick up = gas,
  down = brake/reverse, sideways = steer; max 24 m/s (54 mph). 3-circle collision vs walls
  and cars, damage, smoke < 35 hp, fire at 0 -> explodes after 4.5 s (blast kills within
  5 m, chains to cars). Get in / Carjack / Steal police car via actions; Get out.
- Police: heat 0..5 (stars = ceil). crimeSeen() only counts if someone sees you or a loud
  shot was just fired; killing a cop +2, shooting/punching a cop +1, carjack +0.6, stealing a
  cruiser +1. Cruisers spawn 30-95 m away (stars = count, max 4), route along the grid until
  they can see you, then chase/ram; on foot near you, 2 cops get out. 1 star: cops arrest
  (BUSTED -> released at the station, 200 coupon fine). 2+ stars or you aiming: they shoot
  (WASTED -> outside the casino). Lose them by staying unseen 8 + 5*stars s, or the Spray
  Shop (300 coupons: repaint, repair, stars cleared).
- Casino security only chases you near the casino now.
- Tests: T7 (driving, arrests, shootouts, evading, carjack, explosion), T10 (traffic flow:
  no wrecks/gridlock over 4 min on 5 seeds).

### v34: flicker fix, lock-on aiming, fairer police (user feedback)
- Flicker: DepthChooser asks EGL for a 24-bit depth buffer (falls back to 16); near plane
  0.3 (walk) / 0.25 (aim) / 0.4 (drive); vertex shader nudges atlas-textured and emissive
  (>0.3) vertices toward the camera; layered surfaces moved >= 1 cm apart (wall decor,
  hanging signs, back-room floors, diner tiles, road markings, blood, shadows at 0.025).
- Aiming: lock-on (findLock/trackLock): aiming or hip-fire locks the crosshair on the best
  person within ~29 deg (enemies first) and tracks their chest; dragging aims by hand for
  0.45 s and drops the lock. Red brackets on the locked person. FIRE button larger.
- Police: report(amount, why, copWitness): civilian-only reports cap at 2 stars; cops add
  freely. Shots fired count once per 6 s. Cruisers per star {0,1,1,2,3,4}. Cops chase your
  last known spot (lastX/Z) and search nearby intersections; on-foot cops go there too.
  Lose them after 6 + 3*stars s unseen. Cops only see you in their view (on foot 22 m with
  facing; cruisers 26 m, not straight behind).

### v35: fight system
- New `CasinoFight.java` holds all hand-to-hand logic. Each move has its own wind-up, recovery, reach, damage, knockback and guard break: JAB, CROSS, HOOK, UPPER, KICK, ROUND, STOMP, plus BLOCK / HIT / DODGE / STAGGER. Each person tracks the move they're in (`Person.act`, `actT`, `actWind`, `actDur`).
- Controls with fists: PUNCH chains jab→cross→hook; KICK becomes a roundhouse after punches; hold BLOCK (blocks take 12%, kicks break guard); DODGE moves you in the stick direction (or back) and nothing can hit you mid-dodge; punch on a downed person = stomp.
- Parry: block within 0.22 s of a hit → attacker staggers, "COUNTER!" slow motion, and your next punch is an uppercut.
- Fighter AI comes in four styles: BRAWLER, BOXER (blocks a lot, quick), DRUNK (slow, wild hooks), BRUISER (150 HP, heavy hits). Attacks are telegraphed with a glowing fist or foot and a yellow health bar. At most 2 swing at once; a second attacker flanks. Poise: after 2–4 clean hits a fighter covers up and swings back. A swing already coming in only stops for a knockdown. Below 22 HP, 40% of fighters run.
- A hit person fights back 45% of the time (tough guys always do), runs 40%, otherwise cowers. Street toughs challenge you every 70–140 s.
- Fists knock out instead of killing ("K.O.!"). "Search pockets" on a KO'd person gives 10–80 coupons (crime). A KO'd contract target can be finished off ("Finish …").
- Sound bit 256 is the block sound (`Sfx.BLOCK`). `world.cashDue` gets paid out through the fine path in the Activity.
- Off-device balance test: /home/claude/work/casino/T13.java (stand still / button-mash / block+punch vs 1–2 fighters of each style).

### v36: fixes from phone feedback (flicker, controls, driving, bar)
- **Flicker fix:** `Mesh.sortGround()` moves every flat ground triangle (y −0.01..0.06, facing up) to the end of the static mesh, lowest first. The renderer draws them last with depth writes off, so overlapping road/sidewalk/paint/threshold layers paint in order instead of fighting. A scan (/home/claude/work/casino/ZF.java) had found 8k+ exactly overlapping ground faces.
- The camera eases back out after a wall pulls it in (`camDistNow`), so it no longer pumps in doorways.
- Street lamps skip avenues and crosswalks (`clearOfAvenues`); one used to stand in the road at x −90.
- Headlights are now soft additive light pools: `PartSink.shadow` with a negative radius, drawn in a second pass of the shadow shader (`uCol`). The glowing road rectangle is gone.
- **Controls:**
  - Fixed MOVE stick in the bottom-left; you can grab it anywhere in the lower-left below the minimap.
  - While driving it steers only, with big GAS and BRAKE/REVERSE pedals at the bottom right. The stick only works as gas/brake if pushed past 0.7.
  - Push the stick all the way to run (1.65× speed).
  - The camera swings behind you while you walk. Dragging to look pauses that for 2 s (`lookT`).
- **Driving:** turns sharper at city speeds; hard steering above 10 m/s scrubs speed; letting go of the wheel within 20° of a road direction straightens the car. Test: T16 (corners at 12–16 m/s clean).
- **Cars are about 3× tougher:**
  - Walls only hurt above 8 m/s.
  - Car-on-car damage is lower.
  - The fire fuse is 9 s.
  - The blast radius is 5 m with less damage.
  - Cop bullets do 3 damage to your car.
- **The Last Call is open to the public:** door unlocked, no trespass zone. It's ready for bar games.
- **Lockpicking is easier:** window 0.11 − 0.014 per pin above 3 (min 0.055); overset margin 0.2.
- **Security is about half as fast:** guard trespass 0.4/s, view range 9 m, decay 0.35/s.
- **Map labels** are placed so they never overlap, long names wrap, and each has a dark backing.
- **Block Drop** pays Fun Coupons on every clear (a tenth of the points), shown live and on the game-over screen.

### v37: real pool, video slots, bigger city
- **Pool (`PoolGame.java` + `PoolActivity.java`)**
  - 8-ball with real physics on a 9-ft table: 1 ms substeps, rolling friction plus cloth drag, 0.95 ball restitution, 0.78 cushions, pocket jaws as posts, side/corner capture radii.
  - Balls are shaded per pixel as spheres. The number vector and stripe axis rotate as they roll.
  - Rules: break, groups set on the first legal pot after the break, fouls (scratch, wrong ball first, no hit) give ball in hand, the 8 early or on a foul loses, the 8 on the break is spotted.
  - **Controls:** touch the felt to aim, fine-aim strip below the table, pull the POWER bar down and release, spin ball (follow/draw), guide line with ghost ball plus object and cue paths.
  - **AI (`PoolGame.plan`):** tries the top 12 unblocked ghost-ball shots × 3 powers × 3 spins on copies and scores by position. If nothing pots, it plays a safety (edges or one-rail kick). Aim wobble = (1−skill)·0.03 rad.
  - **Opponents:** bar has Ray / Sally / Bob / Big Mo (stakes 50–500); casino has Vince / Duchess / Ice Man Ito / The Professor (stakes 500–5000). The stake goes in at the start; a win pays double; quitting forfeits. Practice is free.
  - **Tables:** `CasinoWorld.poolTable(x, z, venue)`. Casino high-stakes table (blue felt) at (11.5, −8); The Last Call table at (42.4, 11.2), with a bartender and two regulars added. Spot key "pool".
  - **Sounds:** `Sfx.CLACK`, `RAIL`, `POCKET`, `CUE`, plus `Sfx.play(ctx, id, volume)`. Fixed: `BLOCK` (v35) had no loudness entry and never played.
  - **Tests:** /home/claude/work/casino/TP*.java.
- **Video slots (`ThemeSlotMath.java` + `ThemeSlotActivity.java`)**
  - Eight original themes on one 5×3 / 20-line engine (Fruit Fiesta is 3×3 / 5 lines). Features: sticky wilds, expanding wilds, wild ×2/×3, mystery symbols, pick-3-chests, free spins with multipliers.
  - Every theme's `scale` is tuned to ~95% payback (TS.java, 1M spins).
  - The win is credited when you spin (the balance display holds it back as `pending`).
  - The island of 8 cabinets is at x 7..14, z −3, open to everyone, with a VIDEO SLOTS sign.
- **Atlas is now 1024×4096.** Themed machine screens/toppers sit at y 2048+ (`CasinoArt.THEMED` = 12); signs ≥ 28 sit at y 2440+. Preview.java now reads the `ATLAS` / `ATLAS_H` constants.
- **City:**
  - **Founders Park** (block x 58..88, z 26..56): fountain, hedges, paths, trees, benches, park lights.
  - **Gas 'N Go** (west block, z −6..18): canopy, pumps, shop, price sign. "Grab a snack" costs 10 FC and gives +35 health.
  - **Shop row** north of Back Street:
    - Burger Barn: "Eat a burger", 25 FC, full health.
    - Pixel Palace: "Play Block Drop at the arcade" (extra `arcade` skips the skill-tree lock).
    - Suds Laundromat.
  - **Street furniture** halfway between lamps on every street except The Strip (planter trees, benches, bins, hydrant + newsbox, mailboxes), all solid.
  - New PLACES and walk areas.

### v38: pathfinding, seating, serving staff (found with "Claude mode")
- **Claude mode tools:** `tools/claude-mode/` holds off-device checks in the spirit of Breach House's Claude mode. Its README lists them: motion trace with stall map, reachability sweep, nav ASCII map, seated-feet check, serving trace, perf, renders.
- **Pathfinding (`CasinoNav.java`):**
  - 0.4 m grid over the whole map. Solids are grown by `BODY` 0.32, cells are blocked by center, doorways are carved open, and road cells cost 6×.
  - A* with no corner cutting, a node cap, and string-pulled paths (40-cell window).
  - `CasinoWorld.goTo` walks straight when clear, otherwise follows the route, and repaths when the target moves more than 1.2 m or it stops making progress.
  - Used by wanderers, fighters (`stepPerson`), guard routes, guards chasing and cops on foot.
  - `crowd()` eases people apart; the player and seated people don't move, walkers give way.
  - **Results** (CM, 180 s): casino-floor bumps 11.7% → ~1%, city stalls over 1 s 97 → ~5. Every one of the 72 spots is reachable. Update cost ~0.25 ms.
- **Seating:**
  - Person `seatY` (stool 0.68, booth 0.45). Hips sit on the seat; the thigh angle is solved so the soles land on the floor, shins straight down. (Before, the knee bent the wrong way and the shins pointed out.)
  - Diner booths rebuilt with a low seat and backrest, plus a second booth guest.
  - TSit: every seated person's feet within 2 cm of the floor.
- **Serving staff (`setupServers` / `serve` / `drinking`):**
  - Staff pour at a tap, carry the glass, hand it across (bartender reach 2.3 m across the counter, diner waitress 1.9, casino cocktail waitress stands beside the guest), then return.
  - Guests hold the glass, sip every 5–12 s, and shout for another when it's empty.
  - New: casino service bar on the west wall with a "Cocktail waitress" serving every seated casino guest, and a diner "Waitress" with coffee.
- **Fix:** the hurt-fighter "run or fight on" decision reused `blockRoll`, which blocking overwrote, so it re-rolled until they ran. It now has its own field (`hurtDecided`); KOs are back to normal.

### v39: joystick fix, cars that look alive, real destruction
- **Joystick:**
  - `STICK_RING` 74 dp ring centered low in the corner; full speed at `STICK_FULL` 40 dp of travel (an inner ring shows it); dead zone 8%, then a 0.85 power curve.
  - You only grab it in the lower-left area (x < 42%, y > 42% of height), so your thumb never reaches the minimap.
- **Cars come alive (`CasinoTraffic.alive`):**
  - Pitch (±0.07 rad from acceleration), roll (±0.1 rad from speed × yaw rate), spring bounce.
  - Front wheels steer from the yaw rate (`steerVis`, up to ~32°).
  - Brake lights, reverse lights, AI turn signals.
  - Exhaust puffs (`CasinoWorld.exhaust`).
  - `drawCar(s, Car, ...)` keeps the body on its springs (`car` matrix) and the wheels on the road (`carBase`).
- **Bodies:** shoulder line, rocker strip, door seams and handles, two-level wheel arches, mirrors, plates, tailpipe, a roof lip, separate side glass.
- **Destruction:**
  - `impact(car, amount, byPlayer, dirX, dirZ, speed)` dents front / back / left / right (`dent[]` 0..1). Hooked into wall hits, car-on-car hits and blasts.
  - Ends crumple shorter, the hood buckles into a peak, side panels cave in and show bare scrapes.
  - Headlights break (light pools dim or go out).
  - Windshield spiderwebs; side glass shatters to black; bumpers come off as debris.
  - Sparks at impacts and along scrapes.
  - Debris (`traffic.debris`) bounces and settles, then clears after 45 s.
  - Radiator steam below 65 health, smoke below 35.
  - Explosions throw the shell up and blow off the hood and doors.
  - The spray shop repairs everything (`repair`).
- **Handling:**
  - Lateral slip when the turn asks for more than 11 m/s² of grip (3.5 with the handbrake).
  - New DRIFT (handbrake) button above BRAKE.
  - Skid marks (`traffic.skids`, up to 600) and a tire-screech sound (`Sfx.SKID`, bit 512).
- **Engine sound:** `Sfx.engine(ctx, on, rpm, volume)` is a looping 0.2 s four-cylinder note whose playback rate follows the revs through 5 gears; it pauses in onPause.
- **Checks:** `tools/claude-mode/CarFilm` (clean vs crashed renders) and `TDrive` (pitch, roll, slide, steer numbers). T16 corners pass at 8, 12 and 16 m/s.

### v40: see-through car windows with interiors
- **See-through parts:** a `PartSink.part` color whose alpha (top byte) is under 255 is deferred and drawn after all solid parts, blended, with no depth write (`seeM` / `seeC` / `seeG` in the Renderer, `uAlpha` in the fragment shader). Preview.java does the same, so renders match.
- **Car glass:** `SEE_GLASS` 0x6A1A2838 (tinted, see-through) for the windshield, back and side windows. Cracked windshields are `BROKEN_GLASS` (mostly opaque). Smashed side windows aren't drawn at all.
- **Interior:** cabin floor, front seats with headrests, rear bench (not on pickups), dashboard with gauges that glow while the car runs, and a steering wheel on the left.
- **Driver:** shown when an AI drives or you do: your shirt or jacket, skin and hair; AI drivers get stable random looks from `c.id`; cops wear uniforms and caps and have a partner. They sit low enough to stay under the roof (`hy = min(y1 + 0.42, top − 0.14)`).

## Ideas not built yet
- Queued (user asked): a real fight system (melee: blocks, combos, fighters who fight
  back); pool and arm wrestling at The Last Call; also offered taxi fares,
  street races, vigilante, valet, stunt jumps, darts. User asked for a strip club: declined
  (offered a goofy nightclub instead; no answer yet).

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
