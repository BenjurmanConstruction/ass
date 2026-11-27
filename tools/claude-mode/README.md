# Claude mode for the casino world (off the phone)

Java checks that run the real world code (CasinoWorld & co.) on a computer, the way Breach
House's Claude mode does in the browser: seeded fixed-step runs (1/30 s), numbers you can
compare before and after a change, and pictures.

Build: compile the app's pure-Java files plus these into one out dir (Android classes aren't
needed by these):

    S=app/src/main/java/app/easywake
    javac -proc:none -d out $S/Casino{World,Nav,Fight,Traffic,City,Places,Art,Weapons,Shop}.java \
        $S/{LockPick,PoolGame,ThemeSlotMath}.java tools/claude-mode/*.java

| Tool | What it tells you |
|---|---|
| `CM secs out.png x0 z0 x1 z1 [px/m]` | Motion trace: every walker's bumps and >1 s stalls by place, and a top-down map of trails (blue), bumps (orange), stalls (red) over solids |
| `Sweep` | Every interaction spot standable and reachable on the nav grid; anyone starting inside a solid |
| `NavMap x0 z0 x1 z1` | ASCII of the walk grid (`.` open, `#` margin, `X` solid) to see why a route fails |
| `TSit` | Each seated person's lowest point above the floor (feet should be ~0) |
| `TServe` | Bartender / waitresses: drinks delivered in 3 min, stuck episodes |
| `TPerf` | World build time and update cost per frame |
| `PV name x z yaw pitch dist ...` | Software-rendered views (env `HIDE=1` hides the player, `STEPS=n` runs the world first) |
| `ZF` | Overlapping coplanar faces (flicker) |
| `T13` / `T16` / `TP` / `TS` | Fights, cornering, pool AI, slot paybacks |
