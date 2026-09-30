# CoX Storage Planner

A per-chest deposit and withdraw plan for Chambers of Xeric, and a count of the potion doses you have against what you want for Olm. Roles, claims and team reminders are in the separate [CoX Team Utilities](https://github.com/1504681/CoxTeamUtilities) plugin.

## Chests

Each storage unit in the raid gets its own plan: what to put in and what to take out. Open a storage unit and the chest appears under **Chests**, named after its room (`Ice Demon`, `Farming 1`, `Farming 2`, `End 1`, `End 2`; `End` and `Farming` are numbered by floor, whichever layout the game picked for them; a storage in a stretch the plugin can't place, like the one after the tightrope, counts as the room before it). Rename it, then fill the two lists: one item per line, matched from the start of the name so `Xeric's aid` is any dose, `*` and `?` as wildcards (`*chinchompa`, `Dragon *`), `Stinkhorn mushroom, 3` for a number, `everything` to empty the inventory. Numbers are quantities, so a stack of 14 juice counts as 14. A Put in line without a number means all of them; with one (`Endarkened*, 11`) the step is done once that many went in since you opened the storage, or the storage already holds that many. A Take out line is done when your inventory and worn gear together hold at least that many.

The easy way to fill the lists is to click: tick **Mark by clicking** and, with a storage open, left-clicking an item in the storage adds it to Take out and one in the side inventory to Put in (with no storage open, inventory items go to the chest picked in the sidebar). Every click adds one more, so three clicks on a stinkhorn make `Stinkhorn mushroom, 3`, and the order you click is the withdraw order. `Unmark` on the right-click menu takes one away. Marking is off again when you leave the raid.

The plugin starts with three chests filled in (the author's CM route: gear and Olm ingredients into Ice Demon, juice and stinkhorns out at Farming 1, gear and potions out in order at End 1). Edit or delete them as you like; they come back only if the plugin's chest setting is cleared.

A chest works in three phases while its storage is open: gear to wear, then things to put in, then things to take out. A Take out line starting with `wear` (`wear Scythe of vitur`) is gear to put on; it glows purple wherever it is, storage or inventory, until it's worn, and nothing else lights up until all of it is. Then the Put in items glow; `everything else` in Put in means whatever the Take out list doesn't keep. Once the inventory is clear the Take out list lights, in order. Gear you put on stays ticked off, and the same item on a later line means one more of it.

**Copy my loadout** next to Take out sets all of that up from what you're wearing and carrying right now: worn gear as `wear` lines, then the inventory slot by slot (a run of the same item becomes one line with its number, `Xeric's aid, 3`), *Withdraw in this order* ticked, and `everything else` in Put in if it was empty. Set the chest before Olm up that way once, with the inventory laid out how you want it, and the plugin walks you through it every raid: put the gear on, dump what's left, pull the rest in order, same layout.

Tick **Withdraw in this order** and the list becomes steps 1, 2, 3. While the storage is open the items still to move glow: in the side inventory what goes in, in the storage what comes out. Ordered lists light the next three items: the next one gets a big numbered orb over the item, the two after it small ones in the corner, fainter the further down the order they are; a setting switches that to only the next item, or to all of them in a gradient from the first colour to the last. Colours and the pulse are settings too, and there's an optional on-screen list of the steps.

## Supplies

Overload, Xeric's aid, Revitalisation and Prayer enhance in your inventory and private storage (shared storage too if the setting is on), shown as potions or as doses (setting), against the `need` number you type next to each: what you want to have when you get to Olm. Short rows go red with how much more to pick up.

**Team** and **Solo** at the top switch between two sets of `need` numbers; with *Stamina in solo raids* on, Solo adds a Stamina row for the running at Olm. Both use the same numbers unless *Separate doses for solo raids* is on. Inside a raid the plugin picks team or solo from the raid's party size. Defaults: team 1 Overload, 6 Xeric's aid, 3 Revitalisation, 1 Prayer enhance; solo the same with 4 Revitalisation and 1 Stamina.

When you walk into Olm you get a chat line with whatever you're still short.

Under the rows a grid shows what each party member holds in inventory + private storage, the shared storage, and the total. `3?` is an inventory whose private storage hasn't been opened yet; `×` is a member without the plugin (the game doesn't show other players' inventories, so there's nothing to know). Join a party with the core Party plugin for this.

The game only sends a storage's contents while its interface is open, so both storages show `?` until someone has opened them in the current raid (a party member's view of the shared storage is used if you haven't opened it yourself). A deposit or withdrawal made as the interface closes doesn't come back from the game either, so the plugin works those out from what left or entered your inventory.

## Settings

| Setting | Default |
|---|---|
| Show supplies as | potions |
| Separate doses for solo raids | off |
| Stamina in solo raids | off |
| Count shared storage | off |
| Count split overloads | on |
| Olm entry reminder | on |
| Steps overlay | off |
| Glow items | on |
| Ordered withdraw glow | next three, biggest first |
| Glow colour / gradient end colour / wear colour | cyan / pink / purple |
| Pulse | on |

## Changelog

1.0.0: first release.

## License

BSD 2-Clause, see LICENSE.
