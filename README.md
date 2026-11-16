# Sable

A desktop DAW built on Electron: arrangement timeline, piano roll, built-in synth and drum machine, mixer with effects, project files and WAV export. No runtime dependencies; the only thing `npm install` fetches is Electron itself.

## Run it

You need Node.js (LTS) installed: https://nodejs.org/en/download

- **Windows:** double-click `Run Sable (Windows).bat`
- **macOS / Linux:** double-click `Run Sable (Mac-Linux).command`

The first run downloads Electron (a minute or two); after that it opens straight away. From a terminal the same thing is `npm install` then `npm start`.

## Build an installer

- **Windows:** double-click `Build Installer (Windows).bat`
- **macOS / Linux:** double-click `Build Installer (Mac-Linux).command`

The installer lands in the `dist` folder (`.exe` on Windows, `.dmg` on macOS, AppImage on Linux). An installer can only be built on the OS it is for.

## Using it

**Arrangement**
- Double-click an empty spot in a lane to create a clip. Drag to move (also between tracks of the same type), drag the right edge to resize, Alt-drag to copy. Hold Shift for 1/16 precision instead of whole beats.
- `Delete` removes the selected clip, `Ctrl+D` duplicates it, `Ctrl+C` / `Ctrl+V` copy and paste at the playhead.
- Click the bar numbers to move the playhead. Drag in the thin strip above them to set the loop region; click the strip to toggle looping.
- `Ctrl+wheel` zooms.

**Piano roll** (select a clip, or double-click one)
- Draw tool: click to add a note, drag to set its length. Drag notes to move, drag the right edge to resize, right-click (or right-drag) to erase.
- Select tool, or Shift-drag with the draw tool: box-select. `Ctrl+A` selects all.
- Arrow keys move the selection (Shift+Up/Down = octave). `Ctrl+C/X/V`, `Ctrl+D` duplicate, `Delete`.
- Drag in the velocity lane at the bottom to set velocities. Quantize snaps note starts to the current Snap value.
- Drum tracks show the same editor as a 12-row drum grid.

**Playing and recording**
- Computer keyboard plays the selected track: `A S D F G H J K` are white keys, `W E T Y U` black keys, `Z` / `X` change octave. On drum tracks the same keys trigger the 12 pads.
- Arm record (`R`), press play, and what you play is written into the clip under the playhead (a new clip is created if there isn't one), quantized to Snap.

**Shortcuts**
`Space` play/stop · `Home` back to start · `M` metronome · `R` record arm · `1` `2` `3` piano roll / instrument / mixer · `Ctrl+Z` / `Ctrl+Shift+Z` undo/redo · `Ctrl+S` save · `Ctrl+Shift+S` save as · `Ctrl+O` open · `Ctrl+N` new · `Ctrl+E` export WAV

**Sound**
- Synth: two oscillators plus noise, multimode resonant filter with its own envelope, amp envelope, LFO to pitch and filter, 10 presets.
- Drums: 12 synthesized pads (kick, snare, clap, rim, hats, toms, cowbell, crash, ride) with level / tune / decay each, three kits. Closed hat chokes open hat.
- Mixer: per-track 3-band EQ, drive, pan, reverb and delay sends, mute/solo, meters. Tempo-synced ping-pong delay, reverb, master limiter.
- Export renders offline through the exact same signal path as playback, at 44.1 or 48 kHz, 16 or 24 bit.

Projects save as `.sable` files (plain JSON).

## Layout

```
main.js          Electron main process: window, menu, file dialogs
preload.js       the bridge exposed to the UI (save / open / export)
src/index.html   UI shell
src/styles.css
src/js/core.js       project model, presets, undo history, knob control
src/js/engine.js     audio graph, synth + drum voices, scheduler, offline render, WAV encoder
src/js/arrange.js    arrangement view
src/js/pianoroll.js  piano roll / drum grid
src/js/panels.js     instrument editor and mixer
src/js/app.js        transport, shortcuts, files, export dialog
```

`src/index.html` also opens directly in Chrome for quick UI work; there, save and export fall back to browser downloads.

## Not in this version

Audio tracks / sample import, MIDI controller input, VST plugins, automation lanes and tempo changes.
