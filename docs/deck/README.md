# Pace Pilot deck

`deck.html` is the presentation. `pace-pilot.mp4` is a rendered, silent walkthrough of it.

## Run the deck

Open it over a local server (needed so the browser can load `gsap.min.js`):

```sh
cd docs/deck
python3 -m http.server 8000
```

Then open http://localhost:8000/deck.html.

You can also just double-click `deck.html`, as long as `gsap.min.js` stays next to it.

## Controls

- Right arrow / Space / click: next (reveals the next point, then the next slide)
- Left arrow: previous slide
- F: fullscreen (press it before presenting)

## Files

- `deck.html` — the current deck
- `gsap.min.js` — animation library, keep it beside `deck.html`
- `pace-pilot.mp4` — silent video version, 1920x1080
- `archive/deck-v1.html` — the earlier pacer-only version
