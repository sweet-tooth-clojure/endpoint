# Changelog

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
