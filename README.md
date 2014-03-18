# Stentor

Making city data shout.

## Usage

Run

```
$ lein cljsbuild auto
```

in another termainal

### Configuration

Your `$HOME/.stentor.edn` file should contain something like the following :-

```clojure
{
 :database {:keyspace "test"}

 :web-server {:port 8010}

 :data-dir
 {:poi "/home/malcolm/src/stentor-data/drugs"
  :area "/home/malcolm/src/stentor-data/choropleth"
  }

 }
```

## License

Copyright © 2014 Mastodon C

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
