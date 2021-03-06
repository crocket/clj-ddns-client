# clj-ddns-client

It updates DDNS entries.

## Supported DDNS Providers

* dnsever

## What It Doesn't Do.

* It doesn't run as a daemon.

## Build

* Install JDK and leiningen
* Execute `lein uberjar` in the same directory as README.md.

## Installation

* Download a latest release jar file and config.sample.edn
* Copy config.sample.edn as config.edn
* Configure config.edn accordingly.
* Tweak JVM memory options to minimize memory usage.
  * -Xmx
  * -XX:MaxMetaspaceSize
  * etc...

## Usage

```
$ java -jar clj-ddns-client-x.x.x-standalone.jar -h
```

x.x.x is a placeholder for a version

## License

Copyright &copy; 2015 crocket

This project is licensed under the [Mozilla Public License 2.0][license].

[license]: http://www.mozilla.org/MPL/2.0/
