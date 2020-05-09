# Changelog

## [Unreleased]

### Chaanged

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
