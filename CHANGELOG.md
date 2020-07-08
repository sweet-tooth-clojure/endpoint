# Changelog

## [0.10.8] 2020-07-08

### Changed

- updated to `sweet-tooth/generate 0.1.1`, using new rewrite helper
- "not found" handler defaults to 404 instead of 200

## [0.10.7] 2020-07-05

### Added

- introduced `sweet-tooth.generate`
- added generators under `sweet-tooth.endpoint.generate.endpoint`

## [0.10.6] 2020-07-03

### Changed

- changed project name from `sweet-tooth/sweet-tooth-endpoint` to
  `sweet-tooth/endpoint`

## [0.10.5] 2020-06-30

### Changed

- `sweet-tooth.endpoint.system/shrubbery-mock` now has a new signature, making
  it easier to distinguish mock method return vals from args passed to the
  component.

## [0.10.3] 2020-06-15

### Added

- `sweet-tooth.endpoint.utils/clj-kvar` for specifying a var using a keyword.
  resolves the var in clj, returns the keyword in cljs

## [0.10.2] 2020-06-11

### Added

- spec out `sweet-tooth.endpoint.format`
- update `sweet-tooth.endpoint.format` so that it's a little easier to
  understand and more amenable to extension if it ever comes to that.

### Removed

- `sweet-tooth.endpoint.utils/format-ent`, that responsibility is now
  handled by `sweet-tooth.endpoint.format/format-entity`

## [0.10.1] 2020-06-03

### Added

- added MIT license
- spec out `sweet-tooth.endpoint.system`

### Changed

- specify init-keys in `sweet-tooth.endpoint.system/system`

## [0.10.0] 2020-06-01

### Changed

- renamed `:coll` and `:ent` reitit router expanders to `:collection`
  and `:member`. Less ambiguous, and `:member` is more meaningful,
  especially when paired with `:collection`.

## [0.9.5] 2020-06-01

### Added

- added `:sweet-tooth.endpoint.system/mocked-component-opts` option
  for shrubbery mocks to pass through opts to the mocked component

## [0.9.4] 2020-06-01

### Added

- endpoint decision maps can now be keyed by path instead of by
  expander type. These both work:

  ```clojure
  {:coll {:get {:handle-ok [}}}
  {"/" {:get {:handle-ok [}}}
  ```

## [0.9.3] 2020-05-28

### Added

- warning logging when liberator decisions aren't configured correctly

### Fixed

- `sweet-tooth.endpoint.test.harness/assert-response-contains-one-entity-like`
  fixed bug with multiple arities
- `sweet-tooth.endpoint.test.harness/assert-response-contains-entity-like`
  fixed bug with multiple arities
  

## [0.9.2] 2020-05-28

### Fixed

- Fixed `sweet-tooth.endpoint.test.harness/contains-entity?` - it
  wasn't transforming the response data correctly in order to perform
  the desired comparison.

### Undeprecated

- `sweet-tooth.endpoint.test.harness/contains-entity?`. The assert
  macros are generally more useful but this function still comes in
  handy. Maybe show a little more restraint with the deprecation
  hammer next time, Daniel.

## [0.9.1] 2020-05-23

### Changed

- Updated middleware so that responses would both return
  segment-formatted exceptions and print logs

### Added

- docs docs docs
- componentized ring stacktrace log middleware

### Fixed

- Fixed formatting bug where mixing ents of different types in the
  same vector didn't work

### Removed

- `sweet-tooth.endpoint.group-routes` the module has proved unnecessary
- functions for creating compojure routes in
  `sweet-tooth.endpoint.liberator`. If those get reintroduced they can
  have their own ns, as reitit does

## [0.9.0] 2020-05-16

### Added

- An extensible way to provide component alternatives using the
  component's configuration. Implemented with the
  `sweet-tooth.endpoint.system/init-key-alternative` multimethod,
  dispatching on the
  `:sweet-tooth.endpoint.system/init-key-alternative` key in a
  component's configuration.
- A new way to mock components using
  `sweet-tooth.endpoint.system/shrubbery-mock`

### Removed

- Previous mocking implementations, `sweet-tooth.endpoint.mock` and
  `sweet-tooth.endpoint.module.mock`. These were OK but the module
  approach was confusing.

## [0.8.3] 2020-05-10

### Changed

- Added missing `ent-type` argument to
  `sweet-tooth.endpoint.test.harness/assert-response-contains-one-entity`

## [0.8.2] 2020-05-10

### Added

- `sweet-tooth.endpoint.test.harness/assert-response-contains-one-entity-like`
  a macro for nicer assertions against an API endpoint
  response. Should only be used with endpoints that return a single
  entity. Advantage is that it uses `(is (= ...))` so you can see the
  diff between expected and actual.
- `sweet-tooth.endpoint.test.harness/assert-response-contains-entity-like`
  similar to above. assert that one entity among _all_ returned
  contains specified k/v pairs

### Changed

- `sweet-tooth.endpoint.routes.reitit/expand-routes` added a third
  argument, `keywordize-ig-refs-cljs`, a boolean defaulting to true
  that controls whether `integrant.core.Ref` types are replaced with
  the keyword they refer to in cljs compilation. Integrant refs almost
  always refer to backend components, and replacing them with keywords
  makes it easier to use the value returned by `expaned-routes`
  directly in frontend code.
- Spec'd and documented `sweet-tooth.endpoint.routes.reitit`
- `sweet-tooth.endpoint.system/system` not uses a custom init function
  that differs from `ig/init` in that, when a component's config has
  `Replacement` record, that record's `:component` key is used and the
  actual `init-key` method is not called for that component
  
### Deprecated

- `sweet-tooth.endpoint.test.harness/contains-entity?` - use
  `sweet-tooth.endpoint.test.harness/assert-response-contains-*`
  macros instead
