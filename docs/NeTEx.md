# NeTEx

## Authorities, networks and operator

The NeTEx package has two authorities, **VR** and **HSL**. Each has its own network.
All journeys have one operator, **VR**.

## What is in the package

| NeTEx element | Id | Notes |
|---|---|---|
| `Authority` | `FTR:Authority:VR` | `CompanyNumber` 10, legal name "VR-Yhtymä Oyj" |
| `Authority` | `FTR:Authority:HSL` | `CompanyNumber` 9995, legal name "Helsingin seudun liikenne -kuntayhtymä" |
| `Network` | `FTR:Network:VR` | Main network. Has `AuthorityRef` to VR |
| `Network` in `additionalNetworks` | `FTR:Network:HSL` | Has `AuthorityRef` to HSL |
| `GroupOfLines` | `FTR:GroupOfLines:VR`, `FTR:GroupOfLines:HSL` | One in each network |
| `Operator` | `FTR:Operator:VR` | `Name` and `PrivateCode` are `VR` |

Each `Line` points to the `GroupOfLines` of its authority with `RepresentedByGroupRef`.
This follows the Entur guide
[Multiple Authorities](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/728891557/Multiple+Authorities).

## Which authority a line gets

The authority is chosen by the commuter line id of the schedule (`NeTExEntityService.authorityOf`):

| Authority | Commuter lines |
|---|---|
| HSL | A, E, I, K, L, P, U, Y |
| VR | everything else, including the state-purchased lines R, Z, D, H, M, O, G, T |

HSL lines are the ones that run only inside the zones A–E. Lines that leave the zone area
(for example T, which leaves zone E) are state-purchased and belong to VR.
Long-distance trains and other trains without a commuter line id belong to VR.

If a commuter line is in neither list, it gets VR and a warning is logged:
`event=generateNeTEx method=authorityOf outcome=default error_type=UnknownCommuterLine commuterLineId=<id> authority=VR`.
This tells us that a new line has appeared and the lists may need an update.

## Operator

VR is the operator of every journey, and `FTR:Operator:VR` is the only `Operator`. Its `Name` and `PrivateCode` are `VR`.
Schedules with the operator `hsl` are written with operator VR.
If a schedule has an operator other than `vr` or `hsl`, it is written with operator VR too, and a warning is logged:
`event=generateNeTEx method=createOperators outcome=default error_type=UnknownOperator operator=<code> operatorRef=FTR:Operator:VR`.

## Trains that are not in NeTEx

The train types `V` (henkilöliikenteen tyhjävaunujuna, empty passenger stock),
`HV` (lähiliikenteen tyhjävaunujuna, empty commuter stock) and
`MV` (kaukoliikenteen tyhjävaunujuna, empty long-distance stock) are trains without passengers.
`MUS` (museojunat, museum trains) is a museum train type. All four are filtered out by train type (`NeTExService.EXCLUDED_TYPES`),
so the commuter lines V and W, which only have `HV` trains, do not appear.
