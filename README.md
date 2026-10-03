# CoX Storage Planner

A per-chest deposit and withdraw plan for Chambers of Xeric, and an optional count of the potion doses you have against what you want for Olm. Roles, claims and team reminders are in the separate [CoX Team Utilities](https://github.com/1504681/CoxTeamUtilities) plugin.

## Chests

Each storage unit in the raid gets its own plan: what to deposit and what to withdraw. Open a storage unit and the chest appears under **Chests**, named after its room (`Ice Demon`, `Farming 1`, `Farming 2`; the End rooms are `Pre-Vanguards` and `Pre-Olm`, after what you're packing for; `Farming` is numbered by floor, whichever layout the game picked for them; a storage in a stretch the plugin can't place, like the one after the tightrope, counts as the room before it). The dropdown lists chests in the order the raid reaches them. Fill the two lists: one item per line, matched from the start of the name so `Xeric's aid` is any dose, `*` and `?` as wildcards (`*chinchompa`, `Dragon *`), `Stinkhorn mushroom, 3` for a number, `Ayak | Sang* staff*` for either, `everything` to empty the inventory. A Withdraw line with `|` goes by the first choice you have, on you or in the storage, and `&` puts several items in a choice: `Venator bow | *chinchompa & Twisted buckler` is the bow, or without one the chinchompas and the buckler. The choices passed over don't light up and aren't kept, so `everything else` deposits a buckler you carry while the bow is there. With `&`, a count belongs to its own item (`Black chinchompa, 500 & Twisted buckler`). Numbers are quantities, so a stack of 14 juice counts as 14. A Deposit line without a number means all of them; with one (`Endarkened*, 11`) the step is done once that many went in since you opened the storage, or the storage already holds that many. A Withdraw line is done when your inventory and worn gear together hold at least that many. One whose item is nowhere, not on you and not in the storage, is skipped so the order moves on; potions light up fullest first, so the 3-doses wait until the 4s are gone.

The easy way to fill the lists is to click: tick **Mark by clicking** and, with a storage open, left-clicking an item in the storage adds it to Withdraw and one in the side inventory to Deposit (with no storage open, inventory items go to the chest picked in the sidebar). Every click adds one more, so three clicks on a stinkhorn make `Stinkhorn mushroom, 3`, and the order you click is the withdraw order. `Unmark` on the right-click menu takes one away. Marking is off again when you leave the raid.

A chest works in three phases while its storage is open: gear to wear, then things to deposit, then things to withdraw. A Withdraw line starting with `wear` (`wear Scythe of vitur`) is gear to put on; it glows purple wherever it is, storage or inventory, until it's worn, and nothing else lights up until all of it is. Then the Deposit items glow; `everything else` in Deposit means whatever the Withdraw list doesn't keep. Once the inventory is clear the Withdraw list lights, in order. Gear you put on stays ticked off, and the same item on a later line means one more of it.

With *Organise the inventory* on (default), an ordered list is an inventory layout: click 1 belongs in the first slot the plan may use, click 2 in the next (slots holding something neither list mentions are left alone and skipped). Anything in one of those slots that isn't what belongs there glows to go in, an item of the list in the wrong slot included (`redeposit: Elder maul (wrong slot)` in the sidebar), and comes out again when its turn comes; that goes for one that lands in the wrong slot halfway through the withdrawals too, which is asked back in before the next click. What already sits in its slot stays, and two neighbours of the list the wrong way round count as right (clicks 9 and 10 made as 10 and 9 are left that way). So does the end of the list when you carry it further down in the right order, below where the rest will land: a rune pouch kept in the last slot is left there.

A private storage holds 25 items (tiny) to 120 (massive), every item taking a slot (a stack takes one). When not everything fits, the plugin goes in rounds: it lights only as many deposits as there are free slots, then the withdrawals those holes ask for, then the next deposits. While it's that tight, an item of the list carried in the wrong slot isn't redeposited: it gets an arrow to the slot it belongs in (drag it there; what's in that slot swaps places with it). With the storage full and no hole where its item would land, up to four items of the list come out into whatever slots are empty, get their arrows, and what they pushed aside goes into the room they left; then the rounds carry on. The sidebar says what to do now (`Now: deposit what's outlined`), the free slots, and the drags, with `→` on the rows that are up. With storage and inventory both full it says to make room (drop something or use the shared storage). Turn *Organise the inventory* off and none of this happens: no arrows, no redeposits, the list only counts what you carry.

**Copy my loadout** next to Withdraw sets all of that up from what you're wearing and carrying right now: worn gear as `wear` lines, then the inventory slot by slot (a run of the same item becomes one line with its number, `Xeric's aid, 3`), *Ordered withdrawal* ticked, and `everything else` in Deposit if it was empty. Set the chest before Olm up that way once, with the inventory laid out how you want it, and the plugin walks you through it every raid: put the gear on, dump what's left, pull the rest in order, same layout.

With *Separate chests for solo raids* on, solo raids get their own set of chest plans (it starts as a copy of the team ones): inside a raid the party size picks the set (click the **Team | Solo** switch to override it for that raid), outside it the switch does.

Tick **Ordered withdrawal** and the list becomes clicks 1, 2, 3. While the storage is open the items still to move glow: in the side inventory what goes in, in the storage what comes out. The clicks still to make when you open the storage are numbered 1, 2, 3, so nothing is skipped for what's already done, and each keeps its number as you go (1 2 3 4, then 2 3 4 5); and a step whose item isn't anywhere (not on you, not in the storage) takes none. Clicking any of several identical items in the storage takes the first of them and leaves the rest in place, so only the last of a kind lights: `Xeric's aid, 3` is that one aid with `x3` in its corner, clicked three times. A stack is one click whatever the count. The next four clicks light (*Clicks shown*): the next one gets a big orb over the item, the ones after it small ones in the corner. The colour runs round the colour wheel from the next click colour on the next click to the end colour on the last one lit: green, yellow, orange, red by default. A setting switches the glow to only the next click, or to all of them; *Outline thickness* sets how heavy the outline is. When the next item is scrolled out of view, the storage's scroll arrow lights, up or down (setting). The storage unit in the room you're in carries a `?` while its chest still has something to wear, put in or take out, and a green tick once the inventory says it's all done (setting); the mark only asks for the right items, not for the right slots.

Under the lists the sidebar shows the plan as icons, what goes in and what comes out, `…` after the fourth. An item gets its icon once the plugin has seen it in a raid; until then the start of its name stands in.

The sidebar icon sits near the bottom of the toolbar and only shows inside the raid and on Mount Quidamortem outside it; *Hide away from the Chambers* and *Always hide the sidebar icon* change that.

## Supplies

Off by default: turn on *Supplies tracker* (Sidebar section of the settings). Its own settings are in the *Supplies tracker* section below that.

Overload, Xeric's aid, Revitalisation and Prayer enhance in your inventory and private storage (shared storage too if the setting is on), shown as potions or as doses (setting), against the `need` number you type next to each: what you want to have when you get to Olm. Short rows go red with how much more to pick up.

With *Separate doses for solo raids* on, **Team** and **Solo** at the top switch between two sets of `need` numbers. Inside a raid the plugin picks team or solo from the raid's party size. Clicking the switch during a raid overrides that for the rest of it, so a duo can run the solo numbers and chests. Defaults: team 1 Overload, 6 Xeric's aid, 3 Revitalisation, 1 Prayer enhance; solo the same with 4 Revitalisation.

Under the rows a grid shows what each party member holds in inventory + private storage, the shared storage, and the total. `3?` is an inventory whose private storage hasn't been opened yet; `×` is a member without the plugin or with its tracker off (the game doesn't show other players' inventories, so there's nothing to know). Join a party with the core Party plugin for this.

The game only sends a storage's contents while its interface is open, so both storages show `?` until someone has opened them in the current raid (a party member's view of the shared storage is used if you haven't opened it yourself). A deposit or withdrawal made as the interface closes doesn't come back from the game either, so the plugin works those out from what left or entered your inventory.

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
