# S23 controller-button test evidence — 2026-11-02

The owner pasted a second graphical Linux boot log in response to the requested
physical-controller test (connect a controller, focus guest display, press
buttons). The earlier screenshot verified visible guest color bars and touch.
This response adds the expected controller-to-keyboard button events.

Excerpt from the supplied log, retaining each press/release pair:

```text
PERSIST-READ
QEMU-ANDROID-GRAPHICS
DISPLAY-READY width=1280 height=800 bpp=32 inputs=3
INPUT-KEY code=45 value=1
INPUT-KEY code=45 value=0
INPUT-KEY code=21 value=1
INPUT-KEY code=21 value=0
INPUT-KEY code=1 value=1
INPUT-KEY code=1 value=0
INPUT-KEY code=16 value=1
INPUT-KEY code=16 value=0
INPUT-KEY code=28 value=1
INPUT-KEY code=28 value=0
INPUT-KEY code=15 value=1
INPUT-KEY code=15 value=0
INPUT-KEY code=18 value=1
INPUT-KEY code=18 value=0
```

| Linux code | Guest key | Implemented controller mapping |
|---|---|---|
| 45 | x | X |
| 21 | y | Y |
| 1 | Escape | B |
| 16 | q | L1 |
| 28 | Enter | A or Start |
| 15 | Tab | Select |
| 18 | e | R1 |

Enter and e press/release pairs also appear again in the capture. Earlier
BTN_LEFT/ABS events in this same log remain the pointer/touch path. The disk UUID
is 64b99eab-ded5-4deb-8a8e-59cd3ba7137d, matching the preceding screenshot/log's
PERSIST-WRITTEN boot. This new boot therefore also verifies persistence across
these two graphical sessions.

Conclusion: the basic Milestone D goal is device verified for this fixture:
visible guest graphics, touch, and the controller-button keyboard mappings.
This relies on the owner's test context; the guest sees a virtual keyboard
and does not identify the original Android input device or controller model.
A key code alone cannot distinguish a controller from a physical keyboard.

Remaining scope: no arrow-key codes are present, so D-pad and stick mappings
still need separate evidence. Focus loss, simultaneous inputs, graphical-session
shutdown/native exit and latency have not been demonstrated. The buttons are
mapped keys, not full guest analog-gamepad axes or OUYA framework interfaces.
No Android guest, accelerated graphics, audio or performance result is implied.
The working installed f9714c9 APK should be retained for those follow-up checks.
