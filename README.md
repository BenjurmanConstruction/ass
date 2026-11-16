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
- Right-click a clip for a menu: delete, duplicate, edit notes, or join all the track's clips into one. Right-click a track name to delete the track.
- Snap is optional: set Snap to Off (top bar), or click **Grid snap** in the piano roll, and clips and notes move and stretch freely. Holding Alt while dragging a note also ignores the grid.
- `Delete` removes the selected clip, `Ctrl+D` duplicates it, `Ctrl+C` / `Ctrl+V` copy and paste at the playhead.
- Click the bar numbers to move the playhead. Drag in the thin strip above them to set the loop region; click the strip to toggle looping.
- `Ctrl+wheel` zooms.

**Piano roll** (select a clip, or double-click one)
- Draw tool: click to add a note, drag to set its length. Drag notes to move, drag the right edge to resize, right-click (or right-drag) to erase.
- Select tool, or Shift-drag with the draw tool: box-select. `Ctrl+A` selects all.
- Arrow keys move the selection (Shift+Up/Down = octave). `Ctrl+C/X/V`, `Ctrl+D` duplicate, `Delete`.
- Drag in the velocity lane at the bottom to set velocities. Quantize snaps note starts to the current Snap value.
- Drum tracks show the same editor as a 12-row drum grid.

**Chord helper** (second toolbar row in the piano roll)
- Choose the song's Key and Major/Minor. The seven chords that belong to that key appear as buttons: click one to hear it and drop it in at the playhead, which then steps forward so you can click out a progression.
- "Add a progression…" fills in a ready-made sequence (pop, rock, blues, minor rock and more) in your key.
- Show scale dims every note outside the key, so the bright rows are the safe ones for melodies and bass lines.
- One-key chords makes the keys `A S D F G H J` play (and record) those seven chords.

**Playing and recording**
- An on-screen keyboard above the tabs shows which computer key plays what, lights up as you play, and can be clicked. The home row from `A` to `\\` is twelve white keys left to right; `W E T Y U O P ]` are the black keys; `Z` / `X` change octave. On drum tracks `A` to `\\` are the 12 drums in order.
- **Writing notes** (on by default): while the song is stopped, every key you press is written into the clip at the playhead, which then steps forward by the Snap value. Hold keys together to write a chord. Turn it off to play without writing.
- Arm record (`R`), press play, and what you play is written into the clip under the playhead (a new clip is created if there isn't one), quantized to Snap.

**Shortcuts**
`Space` play/stop · `Home` back to start · `M` metronome · `R` record arm · `1` `2` `3` piano roll / instrument / mixer · `Ctrl+Z` / `Ctrl+Shift+Z` undo/redo · `Ctrl+S` save · `Ctrl+Shift+S` save as · `Ctrl+O` open · `Ctrl+N` new · `Ctrl+E` export WAV

**Sound**
- Real instruments: **Electric Bass** (Squier Jazz bass), **Electric Piano** (Wurlitzer EP200) and **Grand Piano** (Steinway), each recorded at two dynamics across the keyboard, with attack, release, tone, gain and vibrato controls. Pick them from the Sound menu in the Instrument tab.
- Beat kits and basses: **Boom**, **Club** and **Punch** kits made from recorded electronic drum hits, and six beat basses (808 Bass, Thick, Smooth, Trance, Hard, DnB) that play across the keyboard.
- Synth: two oscillators plus noise, unison (up to 5 detuned voices spread in stereo), multimode resonant filter with its own envelope, amp envelope, LFO to pitch and filter, 13 presets.
- Drums: the **Rock Kit** is a real acoustic kit (24" kick, snare, rimshot, sidestick, hats, three toms, china, crash, ride) with several velocity layers and alternating takes per drum, so soft and hard hits sound different. Three synthesized electronic kits (808, 909, lo-fi) are also included. Every pad has level / tune / decay. Closed hat chokes open hat.
- Mixer: per-track 3-band EQ, drive, one-knob compressor, pan, reverb and delay sends, mute/solo, meters.
- Humanize (piano roll) adds small random timing and velocity changes so programmed parts feel played. Tempo-synced ping-pong delay, reverb, master limiter.
- Export renders offline through the exact same signal path as playback, at 44.1 or 48 kHz, 16 or 24 bit.

Projects save as `.sable` files (plain JSON).

## Layout

```
main.js          Electron main process: window, menu, file dialogs
preload.js       the bridge exposed to the UI (save / open / export)
src/index.html   UI shell
src/styles.css
src/js/core.js       project model, presets, undo history, knob control
src/js/engine.js     audio graph, synth + drum voices, sampler, scheduler, offline render, WAV encoder
src/js/kit-rock.js   sample map for the Rock Kit
src/samples/rock/    the Rock Kit one-shots (Ogg Vorbis)
src/js/arrange.js    arrangement view
src/js/pianoroll.js  piano roll / drum grid
src/js/panels.js     instrument editor and mixer
src/js/app.js        transport, shortcuts, files, export dialog
```

`src/index.html` also opens directly in Chrome for quick UI work; there, save and export fall back to browser downloads.

## Credits

- **Rock Kit:** [Big Rusty Drums](https://github.com/sfzinstruments/karoryfer.big-rusty-drums) by Karoryfer Samples, CC0 1.0 (public domain). Close and overhead microphones were mixed down to stereo one-shots.
- **Electric Bass:** [Growlybass](https://github.com/sfzinstruments/karoryfer.growlybass) by Karoryfer Samples, CC0 1.0.
- **Electric Piano:** Wurlitzer EP200 samples by [Greg Sullivan](http://www.sullivang.net/), from [GregSullivan.E-Pianos](https://github.com/sfzinstruments/GregSullivan.E-Pianos), licensed [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). A subset of notes was trimmed, level-matched and converted to Ogg Vorbis.
- **Grand Piano:** [Splendid Grand Piano](https://github.com/sfzinstruments/SplendidGrandPiano), Steinway samples released to the public domain by Akai.
- **Boom / Club / Punch kits and the beat basses:** the [Sonic Pi sample set](https://github.com/sonic-pi-net/sonic-pi/tree/dev/etc/samples), CC0 1.0.

Details are in `src/samples/CREDITS.txt`.

## Not in this version

Audio tracks, loading your own samples, MIDI controller input, VST plugins, automation lanes and tempo changes.
