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
 
 :dbdir "/home/user/stentor-data/file-db"

 :data-dir
 {:poi "/home/user/stentor-data/drugs"
  :area "/home/user/stentor-data/choropleth"
  }

 }
```
(:dbdir points to the location of .edn database files - make sure you copy them there)

## License

Copyright © 2014 Mastodon C

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
