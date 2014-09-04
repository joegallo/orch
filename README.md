# orch

This is a little thought experiment that I ended up working on after I
read Ted Dziuba's
[The S in REST](http://teddziuba.github.io/2014/08/18/the-s-in-rest/),
in which he proposes a scheme for teasing apart RESTful resources that
represent state versus those that represent values.

## An Example

Let's consider
[the example](http://teddziuba.github.io/2014/08/18/the-s-in-rest/#interoperability-with-rest)
that Ted provides:

1. A request for a RESTful resource:

    ```http
    GET /product/12345 HTTP/1.1
    Host: api.example.com
    Accept: application/json
    ```

2. Returns a pointer to a REVAT value:

    ```http
    HTTP/1.1 302 Found
    Location: http://api.example.com/values/5690ba7f-f308-4c32-b67c-56f654bbfd83
    Cache-Control: no-cache
    ```

3. Which we can then fetch at our leisure:

    ```http
    GET /values/5690ba7f-f308-4c32-b67c-56f654bbfd83 HTTP/1.1
    Host: api.example.com
    Accept: application/json
    ```

4. And the server always returns the same value:

    ```http
    HTTP/1.1 200 OK
    Date: Tue, 19 Aug 2014 16:17:24 GMT
    Expires: Tue, 19 Aug 2015 16:17:24 GMT
    Immutable:
    Content-Type: application/json

    { "id": 12345, "title": "Apple iPad Air", "price_usd": 599.99 }
    ```

So GETing `/product/12345` gives you access to the current value by
redirecting you to another URL that represents that immutable value.
You can think of this as being something like remote Clojure atoms.

## Orchestrate

Ted's approach reminded me very much of the API provided by
[Orchestrate](http://orchestrate.io/). Their service provides RESTful
resources that are both stateful and immutable, using a scheme that
has a lot in common with Ted's REVAT proposal, but has a little more
polish in a few places.

<blockquote class="twitter-tweet" lang="en"><p><a href="https://twitter.com/dozba">@dozba</a> check out what <a href="https://twitter.com/OrchestrateIO">@OrchestrateIO</a> is doing with their API, some similar ideas</p>&mdash; Crazy Joe Gallo (@CrazyJoeGallo) <a href="https://twitter.com/CrazyJoeGallo/status/501864916853477377">August 19, 2014</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

Let's see an example:

1. A request for a RESTful resource:

    ```http
    GET /v0/product/12345 HTTP/1.1
    Host: api.orchestrate.io
    Accept: application/json
    ```

2. Returns the current value (and a pointer to that value's location):

    ```http
    HTTP/1.1 200 OK
    Content-Location: /v0/product/1/refs/8f4d2c6d1202bdcd
    Etag: "8f4d2c6d1202bdcd"

    { "id": 12345, "title": "Apple iPad Air", "price_usd": 599.99 }
    ```

3. Gets against that location will always return the same value:

    ```http
    GET /v0/product/1/refs/8f4d2c6d1202bdcd HTTP/1.1
    Host: api.orchestrate.io
    Accept: application/json
    ```

    ```http
    HTTP/1.1 200 OK
    Content-Type: application/json
    ETag: "8f4d2c6d1202bdcd"

    { "id": 12345, "title": "Apple iPad Air", "price_usd": 599.99 }
    ```

The primary difference is that stateful resources like
`/product/12345` are being automatically dereferenced to their current
immutable value when you GET them. So, if you want something to
reference 'whatever the current value happens to be' then you can use
the stateful URL like `/v0/product/12345`, but if you want to
reference the current value *as you saw it* then you can include the
ref in your URL (`/v0/product/12345/refs/8f4d2c6d1202bdcd`) and future
GETs will retrieve the exact value you saw.

Server-side dereferencing to the current value isn't earth-shattering,
but it makes things a little cleaner.

Take a look [their API](https://orchestrate.io/docs/apiref#keyvalue)
for more information.

## Updates

Orchestrate's also got updates figured out already, too -- and they've
gone in a direction that's pretty similar to what Ted is thinking:

<blockquote class="twitter-tweet" lang="en"><p><a href="https://twitter.com/nwjsmith">@nwjsmith</a> POST to the value server, get your immutable URL... then POST that to the RESTful endpoint. Need a useful CAS semantic though.</p>&mdash; Ted Dziuba (@dozba) <a href="https://twitter.com/dozba/status/501828367654473728">August 19, 2014</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

In this case, too, I like what they've done -- updates are done as PUT
requests directly against the stateful resource, with a new immutable
ref being created behind the scenes. If you don't care what the
current value is, a simple PUT request will stomp on whatever is
currently there. But (and this is key now!) if you want atomic
updates, you can pass the current `Etag` in an `If-Match` header on
your PUT request.

## They're Just Remote Atoms

[Clojure's atoms](http://clojure.org/atoms) provide a uncoordinated
"way to manage shared, synchronous, independent state." But your
warranty is only valid as long as you store immutable values in them.

Orchestrate's objects are essentially remote atoms -- you can deref
them with a GET operation, and CAS them by using an If-Match. It's not
even hard to write [some code](https://github.com/joegallo/orch/blob/master/src/orch/atom.clj) that let's you [use them like Clojure
atoms](https://github.com/joegallo/orch/blob/a862c24b179b2d853d13cf5e838410bceb547026/test/orch/atom_test.clj#L18-L34).
