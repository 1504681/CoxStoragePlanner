# CoX Storage Planner

Plan what you deposit and withdraw at each storage unit in the Chambers of Xeric, then follow the glow: the plugin outlines what to put on, what to put in and what to take out, in order, and ticks the storage unit when the chest is done. An optional supplies tracker counts your potion doses against what you want for Olm. Roles, claims and team reminders are in the separate [CoX Team Utilities](https://github.com/1504681/CoxTeamUtilities) plugin.

## Quick start

1. Open a storage unit in a raid. Its chest appears in the sidebar, named after the room.
2. With your gear and inventory laid out the way you want them at that point, click **Copy my loadout**. That writes the plan: your worn gear as `wear` lines, the inventory slot by slot, `everything else` in Deposit, *Ordered withdrawal* on.
3. Next raid, open the same storage and do what glows: purple = put on, yellow = put in, numbered orbs = take out, 1 2 3 4.

## Chests

- One chest per storage unit, named after its room: `Tekton`, `Ice Demon`, `Farming 1` / `Farming 2` (by floor), `Pre-Vanguards` and `Pre-Olm` for the End rooms, `Post-Tightrope`. A storage in a stretch the plugin can't place counts as the room before it. The dropdown lists chests in the order the raid reaches them.
- Chests only come from storages you open; a chest with empty lists does nothing.
- *Separate chests for solo raids* gives solo raids their own set of plans (a copy of the team ones to start). Inside a raid the party size picks the set; the **Team | Solo** switch overrides it.
- The sidebar icon shows inside the raid and on Mount Quidamortem (settings to always show or always hide it).

## Writing a plan

Two lists, **Deposit** and **Withdraw**, one item per line.

| Line | Means |
|---|---|
| `Xeric's aid` | matched from the start of the name, so any dose |
| `*chinchompa`, `Dragon *` | `*` and `?` are wildcards |
| `Stinkhorn mushroom, 3` | that many; a stack of 14 counts as 14 |
| `wear Scythe of vitur` | Withdraw: gear to put on, wherever it is |
| `Venator bow \| *chinchompa & Twisted buckler` | Withdraw: choices by priority. The first choice you have, on you or in the storage, is the one asked for; `&` puts several items in a choice, each with its own count |
| `everything` / `everything else` | Deposit: empty the inventory / everything the Withdraw list doesn't keep |

- A Deposit line without a number means all of them; with one, the step is done once that many went in (or the storage already holds them). A Withdraw line is done when inventory and worn gear together hold that many.
- A line whose item is nowhere, not on you and not in the storage, is skipped. Potions light fullest first. The same item on a later line means one more of it.
- **Mark by clicking**: tick it, then left-click an item in the storage to add it to Withdraw or one in the side inventory to add it to Deposit. Each click adds one more (three clicks on a stinkhorn make `Stinkhorn mushroom, 3`), and the order you click is the withdraw order. `Unmark` on the right-click menu takes one away. It switches itself off when you leave the raid.
- Under the lists the plan is shown as item icons (once the plugin has seen the items in a raid).

## Following a plan

While its storage is open a chest goes through three phases:

1. **Wear**: `wear` lines glow purple in the storage or the inventory until they're worn. Nothing else lights until they are.
2. **Deposit**: the items to put in glow yellow in the side inventory.
3. **Withdraw**: the items to take out glow in the storage, all of them, in yellow. With **Ordered withdrawal** on they're clicks 1, 2, 3 instead (*Ordered withdrawal glow* picks how many light, *Organise the inventory* whether the slots matter):
   - The next four light (*Clicks shown*): a big orb on the next click, smaller ones on the ones after, green, yellow, orange, red. A click keeps its number as you go (1 2 3 4, then 2 3 4 5).
   - Of several identical items only the last lights, with `x3` in its corner for how many clicks. A stack is one click.
   - *Lines between clicks* draws arrows from click to click. A click on an item scrolled out of view gets its orb pinned to the storage's edge, pointing the way to scroll, and the storage's scroll arrow lights.

The storage unit in your room carries a `?` while its chest still has something to do and a green tick once your inventory has it all.

![An ordered withdrawal with Lines between clicks on](https://i.imgur.com/2m6ab4d.gif)

*Ordered withdrawal with the* Lines between clicks *setting on (off by default): the numbered orbs run green to red and the arrows join them up.*

## Organise the inventory

On by default. With an ordered list, click 1 belongs in the first inventory slot the plan may use, click 2 in the next, and so on (slots holding something neither list mentions are skipped). An item of the list sitting in the wrong slot glows to go back in and comes out again in its turn (`redeposit: Elder maul (wrong slot)` in the sidebar). Two neighbours the wrong way round, and the end of the list already carried in order further down, are left alone.

A private storage holds 25 items (tiny) to 120 (massive), one slot per item or stack. When not everything fits, the plugin works in rounds: deposit what fits, withdraw what those holes ask for, deposit the rest. While it's that tight an item in the wrong slot gets a drag arrow to its slot instead of a redeposit, and with the storage full up to four items come out into empty slots to make room. The sidebar says what to do now (`Now: deposit what's outlined`), the free slots and the drags. Turn the setting off and none of this happens: no arrows, no redeposits, the list only counts what you carry.

## Supplies tracker

Off by default (*Supplies tracker* in the Sidebar settings). It counts Overload, Xeric's aid, Revitalisation and Prayer enhance in your inventory and private storage (shared storage too with the setting), as potions or doses, against the `need` you type next to each: what you want to have at Olm. Short rows go red with how much more to pick up. Defaults: team 1 Overload, 6 Xeric's aid, 3 Revitalisation, 1 Prayer enhance; solo 4 Revitalisation (*Separate doses for solo raids* keeps the two sets, the **Team | Solo** switch picks one).

A grid below shows what each party member holds (needs the core Party plugin): `3?` is an inventory whose private storage hasn't been opened yet, `×` a member without the plugin. The game only sends a storage's contents while it's open, so both storages read `?` until someone opens them in the current raid.

## Settings

| Setting | Default |
|---|---|
| **Chests** | |
| Glow items | on |
| Ordered withdrawal glow | next few clicks, biggest first |
| Clicks shown | 4 |
| Light the scroll arrow | on |
| ? and tick over the storage unit | on |
| Organise the inventory | on |
| Separate chests for solo raids | off |
| Glow colour / next click colour / end colour / wear colour | yellow / green / red / purple |
| Outline thickness | 1.5 |
| Pulse | on |
| Lines between clicks | off |
| **Sidebar** | |
| Hide away from the Chambers | on |
| Always hide the sidebar icon | off |
| Supplies tracker | off |
| **Supplies tracker** | |
| Show supplies as | potions |
| Separate doses for solo raids | off |
| Count shared storage | off |
| Count split overloads | on |

## Changelog

1.3.15
- A click on an item scrolled out of view gets its orb pinned to the edge of the storage it's beyond, with a point showing which way to scroll, and the lines between clicks run on to it.

1.3.14
- Guardians has no storage: the room is gone from the chest list, and a saved Guardians chest moves into the Post-Tightrope one (dropped if empty, left alone if both have lines).
- The Tightrope chest is called `Post-Tightrope`.

1.3.13
- Lines that overlap share what's carried: `Xeric's aid, 2` then `*aid*, 1` is three aids, not two counted twice.
- The chest Delete button is gone: chests come from the storages you open, and one with empty lists does nothing.
- Sidebar edits are applied on the client thread; the sidebar refreshes once per batch of changes; worn gear changing (ammo used up) refreshes the wear lines; a potion drunk after the storage is shut is no longer taken for a late deposit.

1.3.12
- Lines between clicks: an item clicked several times sends a fainter line on to the next item, in the colour of its last click, that fills in as the clicks are made (a third, two thirds, whole for `x3`).

1.3.11
- The second and third clicks are yellow and orange, not lime and amber: each step goes half the remaining way round the colour wheel.

1.3.10
- Each of the clicks shown has its own colour whatever is left: with two to go they're green and yellow, not green and red.
- *Lines between clicks* (off by default) joins the lit clicks up with arrows, in order.

1.3.9
- The clicks lit run green, yellow, orange, red: new *Next click colour* (green) and the *End colour* now red, blended round the colour wheel. *Glow colour* (yellow) is left for deposits and unordered withdrawals.
- The orbs behind the next click shrink more clearly with distance (20 px down to 10).

1.3.8
- Gear to wear glows stronger: a deeper purple at full strength by default, and its outline is a pixel heavier than the others.

1.3.7
- Gear to wear that isn't there no longer holds up the chest: a `wear` line with no more of its item on you or in the storage is skipped, so `wear Dragon arrow, 250` with 180 left moves on to the deposits and withdrawals.
- *Copy my loadout* writes worn ammo without a number.

1.3.6
- Choices by priority: `Venator bow | *chinchompa & Twisted buckler` asks for the bow if you have one, on you or in the storage, and otherwise for the other two. `A | B` goes by the first you have as well, instead of lighting both.
- Lines can be 100 characters, up from 60.

1.3.5
- A click keeps its number: the clicks to make when the storage opens count from 1, and after the first the rest still read 2, 3, 4 with a 5 joining them.
- The colours run from the glow colour on the next click to the end colour on the last one lit, not across the whole list.

1.3.4
- A room a raid has one of keeps its one chest: no more "Ice Demon 2" in a regular raid. An empty second chest made that way is removed.

1.3.3
- The clicks to make are numbered 1, 2, 3 from the next one, in the storage and the sidebar; no more gaps for steps already done.
- With room in the storage, an item in the wrong slot is lit to go back in right away, not after the withdrawals.
- The sidebar says what to do now, marks the rows that are up with `→`, and lists a drag as a drag while it waits.
- A full storage only sends items out into empty slots when nothing else can be done.

1.3.2
- The ? over the storage unit turns into the tick once the right items are carried, whatever slots they're in.
- *Outline thickness* takes halves and defaults to 1.5.

1.3.1
- Two neighbouring items of an ordered list carried the wrong way round are left alone.

1.3.0
- Every item counts as a storage slot: no more deposits lit than the storage has room for, and none when it's full.
- Storage too full to redeposit: an item in the wrong slot gets an arrow to drag it to its own. Storage full: up to four items come out, get their arrows, and make room for the rest.
- *Redeposit what's out of order* is now *Organise the inventory*; off means no arrows and no redeposits.
- Of several identical items in the storage only the last lights, to be clicked as often as its `x3` says.
- New settings: *Clicks shown*, *Outline thickness* (default 2). Default colours are yellow to green, by how far along the list is.
- The sidebar numbers the steps like the orbs: by click, and a step that's skipped has no number.
- The sidebar icon also shows on Mount Quidamortem.

1.2.2
- The end of an ordered list that's already carried further down in order stays put (a rune pouch in the last slot is no longer asked back).

1.2.1
- The lists are called Deposit and Withdraw.

1.2.0
- Ordered withdrawals are held to the inventory slot: items of the list carried in the wrong slot go back in and come out in place.
- A storage too full for everything is worked in rounds: put in what fits, take out, put in the rest. Free slots show in the sidebar.
- The `?` / tick is back over the tiny (default) storage unit, and shows over the massive one.

1.1.0
- Every click of an ordered withdrawal has its own number: two of five brews light as 17 and 18. Items that aren't anywhere take no number.
- The next four orbs go from the glow colour to the end colour.
- The storage's scroll arrow lights when the next item is out of view.
- Sidebar: the plan as item icons, bigger text, tidier layout. The icon sits near the bottom and hides outside the raid (settings).
- Supplies tracker is off by default, in its own settings section.
- Removed: Stamina in solo raids, the steps overlay, the chest rename field, Copy from Team / Solo.

1.0.0: first release.

## License

BSD 2-Clause, see LICENSE.
