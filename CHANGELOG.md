# Gorda Driver App release notes

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.15(2026-10-06)](https://github.com/DevAlexandreCR/gorda-driver/compare/2.0.15...2.0.14)

### Fixed

- Stop the in-trip price card from flickering between the live meter and a stale reading from a previous trip, caused by a leaked metering ticker that kept publishing into the shared fee stream; the metering service now enforces a single ticker per instance, gates fee updates by the session that produced them, and stops any running meter before starting a new one.
- Restore the tracked trip's elapsed time and fares when the metering service is restarted by the system after a process death, instead of running a stateless meter with zero fares and the wrong elapsed time; the service now resumes the tracked trip from local recovery storage, or stops itself when there is nothing to restore.
- Block starting a self-service trip from Home while another trip is active (assigned, in progress, or not yet synced); it previously stopped the live trip's meter and left it frozen after the API rejected the self trip. The driver now sees a message to end the current trip first, with a shortcut to the current trip screen.
- Start metering a trip that was started from the admin panel, or whose local meter data was lost, from the server's trip start time, instead of showing "Trip running…" with no meter; the service now meters elapsed time from the server, distance from zero, and live fares or the saved pricing snapshot, tells the driver that earlier distance could not be recovered, and never starts the meter with zero fares.
- Stop telling the driver a finished trip "has been canceled"; a trip ending with status `terminated` now shows a distinct "Service finished" toast, while a genuinely canceled trip still shows "Service has been canceled". Both messages still navigate home from the current-service screen and clear the local ride-recovery state.
- Stop showing the applicant screen's "Service assigned! Preparing your service…" view (and then silently going home after 8s) when a service the driver applied to was actually assigned to another driver. The apply screen now shows a "Checking assignment…" state while the assignment is verified, then either proceeds to the preparing-service view when assigned to this driver, or shows "The service was assigned to another driver" and returns home immediately otherwise. Also fixed `ServiceRepository.validateAssignment` comparing driver IDs by reference instead of value, and it now resolves to "not assigned" instead of hanging when there is no signed-in user.

## [2.0.14(2026-08-01)](https://github.com/DevAlexandreCR/gorda-driver/compare/2.0.14...2.0.13)

### Added

- Send a location heartbeat every 30 seconds while connected so dispatch sees live positions on the map.
- Automatically reconnect if presence was evicted (HTTP 410); stop with a rejected connection state if another session took over (HTTP 409).
- Hide services directed to another driver from the pending feed (home list and voice/sound alerts) and reject applying to a service directed to someone else; `Service` gained a `directed_to` field.
- Show a confirmation dialog with the pickup and destination when the driver taps "Apply" on a pending service, only navigating to the apply screen once the driver confirms.
- Add a "start own trip" action for connected, available drivers: one-step start reusing the existing multiplier dialog and taximeter, with the GPS fix captured silently.
- Meter self trips immediately without connectivity; sync the creation, and the terminal data if the trip ended offline, as a single deferred request once the network returns.
- Allow canceling a self trip within the configurable window delivered by the ride-fees snapshot; normal assigned services are unaffected.

### Fixed

- Show a loading state ("Servicio asignado, preparando…") on the apply screen while the assigned service syncs, instead of navigating to a blank Home screen on slow connections; falls back to Home automatically after 8 seconds if the sync stalls.
- Style Material alert dialogs with the app palette (elevated surface, 24dp corners, accent buttons without forced uppercase) instead of the default gray Material look; affects the logout-error and force-disconnect dialogs.
- Move the notifications-mute button from a floating corner button into the app bar, next to the "Connected" switch. It previously overlaid the bottom-right corner of every NavHost-hosted screen (Home, Apply, Map, History, Profile), which on Home blocked the "start own trip" button; that button is now unobstructed.

## [2.0.12(2026-06-12)](https://github.com/DevAlexandreCR/gorda-driver/compare/2.0.12...2.0.11)

### Added

- Add vehicle connect flow and profile vehicle picker for the extracted vehicles roster. [#102](https://github.com/DevAlexandreCR/gorda-driver/pull/102)

## [2.0.11(2026-06-08)](https://github.com/DevAlexandreCR/gorda-driver/compare/2.0.11...2.0.10)

### Added

- Show the client completed services count badge in the service list and current service detail.

# Release Notes for 2.0.*

## [2.0.0(2026-04-14)](https://github.com/DevAlexandreCR/gorda-driver/compare/2.0.0...1.2.4)

### Added

- Change firestore by SQL database

# Release Notes for 1.2.*

## [1.2.4 (2025-11-30)](https://github.com/DevAlexandreCR/gorda-driver/compare/v1.2.4...v1.2.3)

### Added

- Destination added to detail. ([#99])(https://github.com/DevAlexandreCR/gorda-driver/pull/99) 

# Release Notes for 1.1.*

## [1.1.6 (2025-03-19)](https://github.com/DevAlexandreCR/gorda-driver/compare/v1.1.6...v1.1.5)

### Fixed

- Error when connection lost. ([#71](https://github.com/DevAlexandreCR/gorda-driver/pull/71))

## [1.1.5 (2024-08-19)](https://github.com/DevAlexandreCR/gorda-driver/compare/v1.1.5...v1.1.4)

### Fixed

- Errors when service disappear. ([#69](https://github.com/DevAlexandreCR/gorda-driver/pull/69))

## [1.1.2 (2024-08-19)](https://github.com/DevAlexandreCR/gorda-driver/compare/v1.1.2...v1.1.1)

### Fixed

- Errors from ServiceAdapter and location. ([#62](https://github.com/DevAlexandreCR/gorda-driver/pull/62))

## [1.1.1 (2024-08-12)](https://github.com/DevAlexandreCR/gorda-driver/compare/v1.1.1...v1.1.0)

### Fixed

- Errors from LocationService. ([#59](https://github.com/DevAlexandreCR/gorda-driver/pull/59))

## [1.1.0 (2024-07-29)](https://github.com/DevAlexandreCR/gorda-driver/compare/v1.1.0...v1.0.33)

### Added

- Add meter service. ([#47](https://github.com/DevAlexandreCR/gorda-driver/pull/57))
