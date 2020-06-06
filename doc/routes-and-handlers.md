## How we define routes

Sweet Tooth has named, data-driven routes using Reitit.

This gives us:

* [Path generation](https://github.com/metosin/reitit#quick-start). We
  can write code like `(path :hosted-instance {:id 1})` which I think
  is preferable to ad-hoc `format` (or something comparable). Another
  benefit is that the path lib will validate that you've passed in the
  route params necessary to produce a path, and will give you sensible
  error messages about what's missing. A smaller benefit is that if we
  end up wanting to change the API prefix from `/api/v1` to `/api/v2`
  we do it in one place.
* [Parameter
  coercion](https://github.com/metosin/reitit#ring-example). This
  works bidirectionally: when parsing path strings, you can coerce the
  correct value out, and when generating paths you can coerce the
  route params. We don't currently make use of this but should.
* Cross-compilation. We define routes in a way that's consumable by
  both backend and frontend. When the FE generates a path using `(path
  :hosted-instance {:id 1})` we know it's using the exact same route
  table to generate the path as the backend is using to interpret
  paths.
* This is an extension of the above, but there is a single source of
  truth for routing, rather than the same path fragments duplicated
  across frontend and background
* The ability to list all acceptable routes (in the repl, `(mapv
  #(update % 1 select-keys [:name]) project.endpoint-routes/routes)`).
  This is a smaller benefit but it can be useful in itself for a
  developer to quickly see the "outline" of the API. It can also be
  useful in the future in doing things like fuzz testing or
  post-deploy sanity checks.
* In general, by representing routes as data it's a lot easier to
  compose and transform them before "finalizing" them, producing a
  function that will actually handle requests

The main drawback to this approach that I see is that the connection
between path fragments and handlers is one degree removed. I think the
connection between `:collection` and `/`, and between `:member` and
`/{id}` is easily learnable but it's till not as obvious as what you
get with `defroutes`.

## How we associate routes with handlers

Compojure route definitions are colocated with their handlers, and in
HM the route definitions have no references to their handlers. This is
necessary for cross-compilation: the code can't reference symbols that
are available only on the server side if we want the same file to
compile for the frontend.

To that end, Sweet Tooth defines a helper for building routes,
`sweet-tooth.endpoint.routes.reitit/expand-routes`. This helper's
purpose is to take a keyword corresponding to a namespace and generate
route entries that include paths and route names, but do not include
any references to handlers. This:

```clojure
[:project.backend.endpoint.user]
```

Produces a route table that essentially looks like this:

```clojure
[["/user"      {:name :users}]
 ["/user/{id}" {:name :user}]]
```

Arbitrary paths are possible. A route entry like this:

```clojure
[:project.backend.endpoint.user {::serr/path-prefix "/admin/org/{org-id}"}]
```

Produces something like this:

```clojure
[["/admin/org/{org-id}/user"      {:name :users}]
 ["/admin/org/{org-id}/user/{id}" {:name :user}]]
```

Or you could create completely arbitrary paths:

```clojure
[:project.backend.endpoint.user {::sut/expand-with [[:collection {::sut/full-path "/custom-path"}]
                                                    [:member     {::sut/full-path "/another-custom-path"}]]}]
;; =>
[["/custom-path"         {:name :users}]
 ["/another-custom-path" {:name :user}]]
```

I want to emphasize that arbitrary paths are possible, we just don't
have the isomorphism (sorry for abusing this word) between path
nesting and route nesting or handler lookup nesting.

So the question becomes, how do we associate these arbitrary paths
with the correct handlers?

I have an aversion to using nested data structures to represent nested
resources. I've found that it becomes a lot easier to get lost in
navigating the data structures, and it can get difficult to determine
what values might be cascading through the nested layers, or what the
relationships among the layers might be. Ultimately what we're
producing is a lookup table, and I personally find it much easier to
reason about such a table if there isn't any nesting.

There's some "magic" here, but at the same time I don't really
consider an aspect of a system magical if there's a clear model for
how it works. The model here is:

* Each entry passed to the `expand-routes` function is a keyword
  representing a namespace, along with a list of `expand-with`
  strategies to generate routes for that namespace
* By default, `expand-with` is `[:collection :member]`
* Each route type has a route-generation strategy associated with
  it. `:coll` and `:ent` have default strategies associated with
  them. (There's actually also a default `:singleton` route type).
* Unknown expanders are handled by... TODO
* Routes are associated with their handlers via the name of their expander

So I see this as providing defaults rather than doing something
magical. When I think of "magical" I think "difficult to inspect or
reason about."

On the other hand: this isn't exactly obvious.
