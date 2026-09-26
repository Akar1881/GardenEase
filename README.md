# GardenEase For Hypixel SkyBlock

GardenEase is a free Fabric mod I built because I got tired of holding W for three hours straight while farming. Your fingers shouldn't pay the price for a good grind. It runs on Minecraft 26.2 and is built specifically for Hypixel SkyBlock's garden, though honestly it works on any server or farm layout where you're moving in straight lines back and forth.

---

### What it actually does

It holds your movement keys for you. That's the core of it. You define a group of lanes (each lane is one pass across your farm), and the macro runs them in order, looping forever until you stop it. It also holds your attack key the whole time so your tool keeps hitting crops.

It works on every farm type — mushrooms, sugarcane, wheat, carrots, potatoes, pumpkins, melons, cactus, whatever. As long as you can break it by walking through it and swinging a tool, this mod handles it. Multi-layer farms (like stacked mushroom rooms where you fall between floors) are also fully supported.

GardenEase also includes crop Profiles for common Hypixel SkyBlock S-shape farms. Profiles run through the same macro lifecycle as groups, including attack control, movement cleanup, teleport protection, and failsafes.

---

### Profiles

Open `/fm`, select the **Profiles** tab, and choose a crop profile. Available profiles include wheat, carrot, potato, sugar cane, nether wart, cocoa beans, mushroom, cactus, melon, pumpkin, wild rose, and sunflower/moonflower profile.

Set the farm's global end X, Y, and Z coordinates, then save the profile settings. You can also fill the fields from your current position. Start the selected profile from the Profiles tab or use the **Start Profile Farm Key** from Minecraft's keybind settings.

The profile start key automatically chooses the initial left/right direction from your current position and facing direction. During the run it follows an S-shaped path: it farms across a lane, advances to the wall, reverses direction, and continues through the next lane until it reaches the configured endpoint.

Profile names are prefixed with `S-SHAPE` to make the intended movement pattern clear. Crop-specific metadata controls details such as row movement, pitch hints, and mushroom S-shape handling.

---

### The two modes

**Duration mode** is the simple one. You tell each lane "hold W for 14.5 seconds" and the mod just does exactly that. Good for farms where your lanes are the same length every time and you don't care about being pixel-perfect on the turn.

**CORDS mode** is for when you want precision. Instead of timing, you set a 3D end coordinate (X, Y, Z) for each lane and the mod holds the key until you actually reach that point. It detects arrival in two ways: if you walk past the coordinate (pass-through), or if you stop right up against a wall at the end (wall-stop). CORDS mode is better for uneven farms, farms with different lane lengths, or multi-layer setups where your Y position matters.

You can switch a group between modes at any time from the GUI or by editing the lane files.

---

### Setting up a group

**Recording mode** is by far the easiest way to set up. Run `/fm record <groupname>` and it turns on. From there it depends on which mode you're in:

- **Duration mode**: just walk your farm like normal. Hold the movement key, walk the full lane, release. The mod records how long you held the key and saves it as a lane. Hold less than 0.5 seconds and it ignores it so you don't accidentally save junk.

- **CORDS mode**: walk to the end of a lane, hold RIGHT SHIFT to freeze your movement in place, then press the movement key (or combo like W+A) you want that lane to use. The mod saves your current position as the end coordinate. Release all keys, then move to the next lane and repeat. There's a 150ms debounce window so you can press W and then add A without it saving W-only by mistake.

Run `/fm record` again (no name needed) to stop.

**Manual setup via the GUI** is the other option. Open it with `/fm` and you get the full editor. In the lane list you'll see fields for the name, duration or coordinates depending on mode, the movement pattern, and a Gauss toggle. You can also use `/fm setcords <group> <lane>` to snap end coordinates to your current position without opening the GUI.

---

### Gaussian movement

Every lane has a Gauss toggle (the ON/OFF button labeled G in the editor). When it's on, the mod has a 5% chance each tick to briefly flicker one of the held movement keys off and back on. The flicker duration is randomly picked from a gaussian distribution centered around 50ms (min 10ms, max 150ms). The result is that your movement has tiny irregular stutters that look more human than a perfectly smooth walk.

It's a subtle effect and won't mess up your farming — 50ms of a key release is not enough to stop you mid-lane. It just makes the pattern harder to detect as a bot.

---

### Failsafes

The failsafes stop the macro automatically if something unexpected happens. They're built using Mixins so they hook directly into game events rather than polling in a loop — faster and harder to break.

- **Yaw/Pitch change**: if your camera moves by more than 1 degree the macro stops. If an admin comes and pushes your camera around, you stop.
- **Teleport**: if you get teleported to a different position or world, the macro stops. The exact position you were at *before* the teleport gets saved as the resume point (not the destination).
- **Stuck**: if your 3D position (X, Y, and Z together) doesn't change by at least 0.01 blocks over a check interval, the stuck timer starts. After 0.8–1.4 seconds of confirmed no movement, the macro stops and plays an alarm. This is suppressed near the end of a CORDS lane (so you don't false-alarm by stopping at a wall), and suppressed during the last 3 seconds of a DURATION lane (end-of-lane grace period).
- **Hotbar slot change**: if you switch to a different hotbar slot, the macro stops.
- **Held item change**: if the item in your hand changes, the macro stops. Useful if your tool breaks.
- **Speed change**: if your movement speed changes, the macro stops. The speed is read from the TAB list header/footer and there's a 3-second delay before this check activates, so the initial teleport lag when starting doesn't trigger it.

When any failsafe fires, the mod waits a random 0.8–1.4 seconds (human reaction time), then stops and plays the `ENTITY_EXPERIENCE_ORB_PICKUP` sound in a loop for 5 seconds to get your attention.

---

### Resume

When the macro stops mid-lane, it saves everything: the group name, which lane you were on, how far through that lane you were (for Duration mode), and your exact X/Y/Z. That data sticks around until you either resume or start a fresh run.

A pulsing orange box is drawn at the saved position. It's visible even through walls, so you can see from across the room or through blocks exactly where you need to stand.

When you're ready, walk back to that spot and run `/fm resume <groupname>`. If you're not close enough (within 2 blocks in any direction) it'll tell you the exact coordinates. Once you're in range it picks up right where it left off — same lane, same time offset.

---

### Loop commands

At the bottom of the group editor you can set a loop command. After all lanes finish, the macro runs this command (with a random 1–1.5 second delay first, then simulated typing time based on how long the command is), then starts the lanes over from the beginning. On Hypixel SkyBlock you'd set this to `/warp garden` or whatever warp gets you back to your farm.

---

### Commands

| Command | What it does |
|---|---|
| `/fm` or `/fm gui` | Opens the GUI |
| `/fm list` | Lists all your groups |
| `/fm create <name> [loopcommand]` | Creates a new group |
| `/fm delete <name>` | Deletes a group |
| `/fm start <name> [lane]` | Starts a group from a specific lane |
| `/fm stop` | Stops the macro |
| `/fm record <name>` | Starts recording; run again with no args to stop |
| `/fm setcords <group> <lane>` | Sets end coords for a CORDS lane to your position |
| `/fm resume <name>` | Resumes from saved pause data |
| `/fm help` | Shows command list in chat |

---

### Settings

- **Emergency stop key**: default is `0`. Change it in Minecraft's keybind settings.
- **Start Profile Farm Key**: unbound by default. Assign it in Minecraft's keybind settings to start the selected Profile with automatic direction detection.
- **Profiles**: selected crop profile and farm endpoint coordinates are saved as global settings in `gardenease.json`, separate from individual farm groups.
- **Sprint**: toggle whether the macro sprints while running lanes. Useful for some farm types that need you moving faster.

---

### Support

Discord: https://discord.gg/36HBbutZqg

Join for support or to grab the latest jar from the updates channel.

---

*this is not a cheat and will never be*
