"""Generate distinct loop/cadence OGG arrangements for Safari Utils."""

from __future__ import annotations

import math
import json
from pathlib import Path

import numpy as np
try:
    import soundfile as sf
except ModuleNotFoundError:
    sf = None

RATE = 24_000
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/safariutils/sounds/sparkling"
SCORE_OUTPUT = ROOT / "src/main/resources/assets/safariutils/sparkling_scores.json"
SCALE = (0, 2, 4, 5, 7, 9, 11)
STEMS = ("melody", "magic", "harmony", "counter", "bass", "pulse", "drums", "grandeur",
         "bells", "harp", "choir", "horns", "finale")
RNG = np.random.default_rng(2_201_108)
SAMPLE_KINDS = ("celesta", "glass", "flute", "strings", "brass", "radiance",
                "bass", "pluck", "pad", "kick", "tom", "cymbal", "hat", "snare")
NOTE_KINDS = SAMPLE_KINDS[:9]
SAMPLE_ANCHORS = (48, 66, 84)
SAMPLE_LENGTHS = (.34, .85, 1.8, 3.8)
CAPTURE_EVENTS: dict[int, list[list[float | int]]] | None = None
CAPTURE_STEMS: dict[int, int] | None = None

# Seven deliberately sparse, distinct discovery themes. Later choices broaden the
# range and rhythm, while intensity still controls how many orchestral stems play.
SONGS = (
    dict(name="glimmering_discovery", tonic=67, bpm=96, meter=4, bars=2,
         chords=(0, 3, 4, 0), lead="celesta", style="prism", drums="shimmer",
         sections=(0, 2, 4, 1), lifts=(2,), cadence="sparkle",
         notes=((0, 1), (2, .5), (4, .5), (7, 2), (4, 1), (6, 1), (7, 2))),
    dict(name="faelight_dance", tonic=64, bpm=120, meter=3, bars=3,
         chords=(0, 4, 3, 0), lead="flute", style="fae", drums="waltz",
         sections=(0, 3, -1, 5), lifts=(3,), cadence="flutter",
         notes=((0, .5), (4, .5), (2, 1), (5, .5), (7, .5), (9, 1),
                (7, .5), (4, .5), (6, 1), (2, 1), (0, 2))),
    dict(name="starlight_ascent", tonic=62, bpm=132, meter=6, bars=2,
         chords=(0, 2, 5, 4), lead="glass", style="ascent", drums="gallop",
         sections=(0, 1, 4, 7), lifts=(2, 3), cadence="ascent",
         notes=((0, .5), (1, .5), (2, .5), (4, .5), (7, 1), (6, .5),
                (8, .5), (9, 1), (11, .5), (14, 1.5), (9, 2))),
    dict(name="enchanted_voyage", tonic=60, bpm=108, meter=4, bars=3,
         chords=(0, 5, 3, 4), lead="strings", style="voyage", drums="adventure",
         sections=(0, 4, 2, 7), lifts=(1, 3), cadence="voyage",
         notes=((0, 1), (0, .5), (4, .5), (7, 1), (9, 1), (6, .5),
                (4, .5), (11, 1), (9, .5), (13, .5), (14, 2))),
    dict(name="celestial_awakening", tonic=65, bpm=126, meter=4, bars=4,
         chords=(0, 3, 5, 4), lead="strings", style="arcane", drums="arcane",
         sections=(0, 5, 1, 8), lifts=(1, 3), cadence="arcane",
         notes=((0, .5), (4, .5), (7, 1), (5, .5), (9, .5), (12, 1),
                (11, 1), (6, .5), (13, .5), (16, 1), (14, 2))),
    dict(name="mythic_triumph", tonic=67, bpm=138, meter=4, bars=4,
         chords=(0, 4, 5, 3), lead="brass", style="triumph", drums="triumph",
         sections=(0, 4, 8, 5), lifts=(1, 2, 3), cadence="triumph",
         notes=((0, .5), (0, .5), (4, .5), (7, .5), (9, 1), (11, 1),
                (14, 1), (13, .5), (11, .5), (16, 1), (18, 1), (21, 2))),
    dict(name="infinite_wonder", tonic=69, bpm=148, meter=6, bars=4,
         chords=(0, 3, 5, 4, 0), lead="radiance", style="infinite", drums="apotheosis",
         sections=(0, 7, 3, 11, 6), lifts=(1, 3, 4), cadence="infinite",
         notes=((0, .25), (2, .25), (4, .5), (7, .5), (11, .5), (14, 1),
                (9, .5), (16, .5), (18, 1), (13, .5), (21, .5), (23, 1),
                (19, .5), (25, .5), (28, 2))),
    dict(name="astral_jubilee", tonic=62, bpm=132, meter=3, bars=6,
         chords=(0, 3, 4, 0), lead="brass", style="waltz", drums="waltz",
         sections=(0, 3, 7, 2), lifts=(1, 2), cadence="waltz",
         notes=((0, 1), (4, .5), (7, .5), (9, 1), (6, 1), (11, .5),
                (9, .5), (14, 1), (12, .5), (9, .5), (16, 2))),
    dict(name="empyrean_revelation", tonic=65, bpm=146, meter=4, bars=5,
         chords=(0, 5, 3, 4, 0), lead="radiance", style="coronation", drums="march",
         sections=(0, 5, 1, 9, 4), lifts=(1, 3), cadence="fanfare",
         notes=((0, 1), (7, 1), (4, .5), (9, .5), (11, 1), (6, 1),
                (13, 1), (9, .5), (16, .5), (14, 1), (18, 1), (21, 2))),
    dict(name="arcane_tempest", tonic=59, bpm=150, meter=5, bars=4,
         chords=(5, 3, 0, 4, 2), lead="strings", style="runic", drums="ritual",
         sections=(0, -2, 5, 1, 8), lifts=(2, 4), cadence="rune",
         notes=((0, .5), (3, .5), (7, 1), (5, .5), (2, .5), (9, 1),
                (6, .5), (11, .5), (8, 1), (4, 1), (13, 1), (10, 2))),
    dict(name="crown_of_stars", tonic=67, bpm=156, meter=4, bars=6,
         chords=(0, 4, 5, 3, 0, 4), lead="radiance", style="starborn", drums="apotheosis",
         sections=(0, 4, 8, 5, 12, 7), lifts=(1, 2, 4, 5), cadence="starborn",
         notes=((0, .5), (7, .5), (4, .5), (11, .5), (9, 1), (14, 1),
                (12, .5), (16, .5), (18, 1), (15, .5), (21, .5), (23, 1),
                (18, 1), (25, 1), (28, 2))),
)


def degree(tonic: int, value: int) -> int:
    octave, step = divmod(value, 7)
    return tonic + SCALE[step] + octave * 12


def frequency(note: int) -> float:
    return 440.0 * 2.0 ** ((note - 69) / 12.0)


def env(length: int, attack: float, release: float, sustain: float = 1.0) -> np.ndarray:
    result = np.full(length, sustain, dtype=np.float32)
    a = min(length, max(1, int(attack * RATE)))
    r = min(length, max(1, int(release * RATE)))
    result[:a] *= np.linspace(0, 1, a, dtype=np.float32)
    result[-r:] *= np.linspace(1, 0, r, dtype=np.float32)
    return result


def synth(kind: str, note: int, seconds: float) -> np.ndarray:
    length = max(1, int(seconds * RATE))
    t = np.arange(length, dtype=np.float32) / RATE
    phase = 2 * np.pi * frequency(note) * t
    if kind == "celesta":
        wave = (np.sin(phase) + .42*np.sin(2.01*phase) + .16*np.sin(4.03*phase)) * np.exp(-2.2*t)
        shape = env(length, .006, min(.3, seconds*.45))
    elif kind == "glass":
        wave = (np.sin(phase) + .45*np.sin(2.72*phase) + .22*np.sin(4.18*phase)) * np.exp(-1.25*t)
        shape = env(length, .012, min(.4, seconds*.45))
    elif kind == "flute":
        wave = np.sin(phase + .014*np.sin(2*np.pi*5.2*t)) + .14*np.sin(2*phase)
        shape = env(length, .055, min(.22, seconds*.3), .88)
    elif kind == "strings":
        wave = sum(np.sin(phase*r) for r in (.996, 1, 1.004))/3 + .16*np.sin(2*phase)
        shape = env(length, .11, min(.34, seconds*.35), .82)
    elif kind == "brass":
        wave = sum(np.sin(h*phase)/h for h in range(1, 7))
        shape = env(length, .025, min(.2, seconds*.25), .8)
    elif kind == "radiance":
        wave = .48*sum(np.sin(phase*r) for r in (.995, 1, 1.006)) + .34*np.sin(2*phase) + .12*np.sin(3*phase)
        shape = env(length, .035, min(.25, seconds*.3), .86)
    elif kind == "bass":
        wave = np.sin(phase) + .24*np.sin(2*phase)
        shape = env(length, .018, min(.18, seconds*.25), .82)
    elif kind == "pluck":
        wave = (np.sin(phase) + .3*np.sin(2*phase)) * np.exp(-5.2*t)
        shape = env(length, .004, min(.16, seconds*.5))
    else:
        wave = sum(np.sin(phase*r) for r in (.998, 1, 1.003))/3
        shape = env(length, .18, min(.48, seconds*.4), .72)
    return (wave * shape).astype(np.float32)


def place(track: np.ndarray, start: float, seconds: float, note: int, kind: str,
          gain: float, pan: float = 0) -> None:
    # Preserve each pitch class while keeping every arrangement below the shrill
    # upper register that became unpleasant in the densest presets.
    while note > 93:
        note -= 12
    while note < 36:
        note += 12
    if CAPTURE_EVENTS is not None and CAPTURE_STEMS is not None and id(track) in CAPTURE_STEMS:
        duration = 0 if seconds <= .38 else 1 if seconds <= 1.0 else 2 if seconds <= 2.4 else 3
        CAPTURE_EVENTS[CAPTURE_STEMS[id(track)]].append([
            round(start * 1000), SAMPLE_KINDS.index(kind), note, duration, round(gain, 4)
        ])
    sample = synth(kind, note, seconds) * gain
    begin = int(start*RATE)
    end = min(len(track), begin+len(sample))
    if end <= begin:
        return
    sample = sample[:end-begin]
    track[begin:end, 0] += sample * math.sqrt((1-pan)*.5)
    track[begin:end, 1] += sample * math.sqrt((1+pan)*.5)


def hit(track: np.ndarray, start: float, kind: str, gain: float) -> None:
    seconds = .28 if kind != "cymbal" else .6
    length = int(seconds*RATE)
    t = np.arange(length, dtype=np.float32)/RATE
    if kind == "kick":
        sample = np.sin(2*np.pi*(78*t-48*t*t))*np.exp(-13*t)
    elif kind == "tom":
        sample = (np.sin(2*np.pi*(118*t-35*t*t)) + .35*np.sin(2*np.pi*61*t))*np.exp(-10*t)
    elif kind == "cymbal":
        sample = RNG.normal(0, 1, length).astype(np.float32)*np.exp(-7*t)
    else:
        sample = RNG.normal(0, 1, length).astype(np.float32)*np.exp(-20*t)
    begin = int(start*RATE)
    end = min(len(track), begin+length)
    if end <= begin:
        return
    if CAPTURE_EVENTS is not None and CAPTURE_STEMS is not None and id(track) in CAPTURE_STEMS:
        CAPTURE_EVENTS[CAPTURE_STEMS[id(track)]].append([
            round(start * 1000), SAMPLE_KINDS.index(kind), 60, 0, round(gain, 4)
        ])
    track[begin:end, :] += (sample[:end-begin]*gain)[:, None]


def chord(tonic: int, value: int) -> tuple[int, int, int]:
    return tuple(degree(tonic-12, value+offset) for offset in (0, 2, 4))


def tracks(seconds: float) -> dict[str, np.ndarray]:
    return {name: np.zeros((int(seconds*RATE), 2), dtype=np.float32) for name in STEMS}


def add_drums(track: np.ndarray, style: str, beats: int, beat: float) -> None:
    for step in range(beats*2):
        at = step*beat/2
        if style == "shimmer":
            if step % 4 == 0: hit(track, at, "kick", .055)
            if step % 2: hit(track, at, "hat", .018)
        elif style == "gallop":
            if step % 3 in (0, 2): hit(track, at, "tom", .075)
            if step % 6 == 3: hit(track, at, "snare", .06)
        elif style == "heartbeat":
            if step % 4 in (0, 1): hit(track, at, "kick", .07 if step % 4 == 0 else .045)
        elif style == "waltz":
            if step % 6 == 0: hit(track, at, "kick", .075)
            if step % 6 in (2, 4): hit(track, at, "snare", .04)
        elif style == "crystal":
            if step % 4 == 0: hit(track, at, "tom", .055)
            if step % 2: hit(track, at, "hat", .028)
        elif style == "adventure":
            hit(track, at, "kick" if step % 4 == 0 else "snare" if step % 4 == 2 else "hat", .065)
        elif style == "march":
            hit(track, at, "kick" if step % 4 == 0 else "snare" if step % 2 == 0 else "hat", .085)
        elif style == "ritual":
            hit(track, at, "tom" if step % 6 in (0, 3) else "hat", .095)
            if step % 12 == 0: hit(track, at, "cymbal", .035)
        elif style == "arcane":
            hit(track, at, "kick" if step % 4 == 0 else "tom" if step % 4 == 2 else "hat", .105)
            if step % 8 == 6: hit(track, at, "snare", .075)
        elif style == "triumph":
            hit(track, at, "kick" if step % 4 == 0 else "snare" if step % 4 == 2 else "hat", .12)
            if step % 8 == 0: hit(track, at, "cymbal", .05)
        elif style == "apotheosis":
            hit(track, at, "tom" if step % 4 == 0 else "snare" if step % 4 == 2 else "hat", .135)
            if step % 6 == 0: hit(track, at, "kick", .1)
            if step % 8 == 0: hit(track, at, "cymbal", .065)
        else:
            hit(track, at, "tom" if step % 4 == 0 else "snare" if step % 4 == 2 else "hat", .09)
            if step % 8 == 0: hit(track, at, "cymbal", .035)


def magic_pattern(track: np.ndarray, song: dict, chords: list[tuple[int, int, int]], beat: float) -> None:
    total_beats = song["meter"]*song["bars"]
    style = song["style"]
    for step in range(total_beats*2):
        chord_notes = chords[min(len(chords)-1, step*len(chords)//(total_beats*2))]
        if style == "prism": index = (0, 1, 2, 1)[step % 4]
        elif style == "ascent": index = step % 3
        elif style == "dawn":
            if step % 4 not in (1, 3): continue
            index = 2 if step % 8 == 3 else 1
        elif style == "waltz": index = (0, 2, 1, 2, 1, 2)[step % 6]
        elif style == "crystal": index = (2, 0, 1, 2, 1, 0, 2)[step % 7]
        elif style == "fae": index = (2, 1, 2, 0, 1, 2, 1, 0, 2, 1)[step % 10]
        elif style == "runic": index = (0, 0, 2, 1, 0, 2, 0, 1)[step % 8]
        elif style == "voyage": index = (0, 0, 2, 1)[step % 4]
        elif style == "coronation": index = (0, 2, 2, 1)[step % 4]
        elif style == "moonlit": index = (2, 1, 0, 2, 1, 2)[step % 6]
        elif style == "arcane": index = (0, 2, 1, 2, 0, 1, 2, 1)[step % 8]
        elif style == "triumph": index = (0, 1, 2, 2, 1, 2, 0, 2)[step % 8]
        elif style == "starborn": index = (0, 2, 0, 1, 2, 1, 2)[step % 7]
        elif style == "wonder": index = (0, 2, 1, 2, 0, 1, 1, 2, 0, 2)[step % 10]
        elif style == "empyrean": index = (0, 2, 1, 2, 2, 0, 1, 2, 1, 2)[step % 10]
        elif style == "infinite": index = (0, 1, 2, 0, 2, 1, 2, 0, 1)[step % 9]
        else: index = (0, 1, 2, 1, 2, 0)[step % 6]
        place(track, step*beat/2, beat*.38, chord_notes[index]+12,
              "glass" if style in ("crystal", "radiance") else "celesta", .065,
              -.55 + 1.1*(step % 5)/4)


def render_score(song_index: int, song: dict) -> list[list[list[float | int]]]:
    global CAPTURE_EVENTS, CAPTURE_STEMS
    beat = 60/song["bpm"]
    # A long evolving body avoids the obvious short restart heard in the first
    # iteration. Only manually entered durations over 30 seconds need to loop it.
    body_seconds = 30.0
    total_beats = math.ceil(body_seconds/beat)
    body = tracks(body_seconds)
    CAPTURE_EVENTS = {index: [] for index in range(len(STEMS))}
    CAPTURE_STEMS = {id(track): index for index, track in enumerate(body.values())}
    chord_seconds = song["meter"]*beat
    chord_count = math.ceil(body_seconds/chord_seconds)
    chords = [chord(song["tonic"], song["chords"][index % len(song["chords"])])
              for index in range(chord_count)]
    # Keep every accompaniment voice on the same musical grid as the melody.
    # Dividing 30 seconds evenly by chord count introduced small timing drift.
    chord_span = chord_seconds

    cursor = 0.0
    note_index = 0
    section_offsets = song["sections"]
    while cursor < body_seconds-.05:
        value, duration = song["notes"][note_index % len(song["notes"])]
        section = note_index // len(song["notes"])
        value += section_offsets[section % len(section_offsets)]
        if section % len(section_offsets) in song["lifts"] and note_index % 4 == 0:
            value += 7
        seconds = min(duration*beat*.9, body_seconds-cursor-.01)
        place(body["melody"], cursor, max(.05, seconds), degree(song["tonic"], value),
              song["lead"], .225, -.18 if note_index % 2 == 0 else .18)
        cursor += duration*beat
        note_index += 1

    for index, notes in enumerate(chords):
        start = index*chord_span
        for voice_index, note in enumerate(notes):
            place(body["harmony"], start, chord_span*.96, note, "pad", .078,
                  -.42+voice_index*.42)
        place(body["bass"], start, chord_span*.9, notes[0]-12, "bass", .145)
        counter = notes[2]+12 if index % 2 == 0 else notes[1]+12
        place(body["counter"], start+beat, chord_span-beat, counter,
              "flute" if song_index < 5 else "strings", .096,
              .35 if index % 2 == 0 else -.35)

    expanded_song = dict(song)
    expanded_song["bars"] = math.ceil(total_beats/song["meter"])
    magic_pattern(body["magic"], expanded_song, chords, beat)
    for step in range(total_beats):
        notes = chords[min(len(chords)-1, step*len(chords)//total_beats)]
        place(body["pulse"], step*beat, beat*.3, notes[0]+(12 if step % 2 else 0),
              "pluck", .062, -.28 if step % 2 == 0 else .28)
    add_drums(body["drums"], song["drums"], total_beats, beat)
    for index, notes in enumerate(chords):
        start = index*chord_span
        for note in notes:
            place(body["grandeur"], start, chord_span*.7, note+12,
                  "brass" if song_index >= 6 else "strings", .058)
        place(body["grandeur"], start+chord_span-beat, beat*.8, notes[2]+24,
              "glass", .056)
        place(body["bells"], start+beat/2, beat, notes[2]+12,
              "glass", .054, .3 if index % 2 else -.3)
        for step in range(3):
            place(body["harp"], start+beat*step, beat*.72,
                  notes[step]+12, "pluck", .047,
                  -.4+step*.4)
        for voice_index, note in enumerate(notes):
            place(body["choir"], start, chord_span*.98, note+12, "pad",
                  .038, -.35+voice_index*.35)
        place(body["horns"], start, chord_span*.7, notes[0], "brass",
              .055, -.18)
        place(body["horns"], start+beat, chord_span-beat, notes[2], "brass",
              .051, .18)
        for voice_index, note in enumerate(notes):
            place(body["finale"], start, chord_span*.95, note+12,
                  "radiance", .041, -.4+voice_index*.4)

    # Final safety pass: every onset shares a sixteenth-note grid. This prevents
    # fractional arrangement offsets from accumulating differently between stems.
    grid_ms = beat * 250
    result = []
    for index in range(len(STEMS)):
        events = []
        for event in CAPTURE_EVENTS[index]:
            event[0] = round(round(event[0] / grid_ms) * grid_ms)
            if event[0] < 30_000:
                events.append(event)
        result.append(sorted(events, key=lambda event: event[0]))
    CAPTURE_EVENTS = None
    CAPTURE_STEMS = None
    return result


def render_samples() -> dict[str, dict]:
    if sf is None:
        raise RuntimeError("soundfile is required when regenerating reusable samples")
    events = {}
    for kind in NOTE_KINDS:
        for anchor in SAMPLE_ANCHORS:
            for duration_index, seconds in enumerate(SAMPLE_LENGTHS):
                sample = synth(kind, anchor, seconds)
                peak = float(np.max(np.abs(sample)))
                if peak > .82:
                    sample *= .82 / peak
                name = f"sample_{kind}_{anchor}_{duration_index}"
                sf.write(OUTPUT/f"{name}.ogg", sample, RATE, format="OGG", subtype="VORBIS")
                events[f"sparkling.sample.{kind}.{anchor}.{duration_index}"] = {
                    "sounds": [f"safariutils:sparkling/{name}"]
                }
    for kind in SAMPLE_KINDS[9:]:
        seconds = .65 if kind == "cymbal" else .34
        track = np.zeros((int(seconds*RATE), 2), dtype=np.float32)
        hit(track, 0, kind, .82)
        name = f"sample_{kind}"
        sf.write(OUTPUT/f"{name}.ogg", track, RATE, format="OGG", subtype="VORBIS")
        events[f"sparkling.sample.{kind}"] = {
            "sounds": [f"safariutils:sparkling/{name}"]
        }
    return events


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    scores = [render_score(index, song) for index, song in enumerate(SONGS)]
    SCORE_OUTPUT.write_text(json.dumps({"kinds": SAMPLE_KINDS, "songs": scores},
                                       separators=(",", ":")), encoding="utf-8")
    # Scores can be revised without rewriting the stable sample bank. This keeps
    # composition work available in lightweight development environments.
    if sf is None:
        print(f"Generated {len(scores)} compact scores; kept existing OGG samples")
        return
    for old in OUTPUT.glob("*.ogg"):
        old.unlink()
    events = render_samples()
    sounds_json = ROOT / "src/main/resources/assets/safariutils/sounds.json"
    sounds_json.write_text(json.dumps(events, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(events)} reusable OGG samples and {len(scores)} compact scores")


if __name__ == "__main__":
    main()
