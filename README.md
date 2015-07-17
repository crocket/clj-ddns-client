# clj-ddns-client

It updates DDNS entries.

## Supported DDNS Providers

* dnsever

## What It Doesn't Do.

* It doesn't run as a daemon.

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
$ java -jar clj-ddns-client-1.0.0-standalone.jar -h
```

## License

Copyright &copy; 2015 crocket

This project is licensed under the [GNU Lesser General Public License v3.0][license].

[license]: http://www.gnu.org/licenses/lgpl-3.0.txt
