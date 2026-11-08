# Spec Delta

## Purpose
Resolve player names to UUIDs, including players who are offline or never joined.

## ADDED Requirements

### Requirement: Join reports
When a client reports a player's UUID and name, the service SHALL store the name with first-seen and last-seen times and keep earlier names of that UUID as history.

#### Scenario: Name change
- **GIVEN** UUID `U` was reported as `Alex`
- **WHEN** `U` is reported as `Alexa`
- **THEN** a lookup of `Alexa` returns `U` and `U`'s history contains `Alex`

### Requirement: Lookup by name
When a client looks up a name, the service SHALL answer case-insensitively from the register with the UUID that most recently held the name; on a miss it SHALL ask the Mojang profile API, store the answer and return it; if Mojang knows no such name, then it SHALL answer 404 `PLAYER_UNKNOWN`.

#### Scenario: Never-seen name
- **GIVEN** `Steve42` is not in the register and exists at Mojang
- **WHEN** a client looks up `steve42`
- **THEN** the service returns Mojang's UUID and stores it with source `MOJANG`

### Requirement: Mojang outage
If the Mojang profile API fails or times out during a lookup, then the service SHALL answer 503 `NAME_LOOKUP_UNAVAILABLE` and SHALL NOT store anything.

#### Scenario: Timeout
- **GIVEN** an unknown name and a Mojang API that does not answer in time
- **WHEN** a client looks the name up
- **THEN** the service answers 503
