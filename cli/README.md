sourcescape-cli
===============



[![oclif](https://img.shields.io/badge/cli-oclif-brightgreen.svg)](https://oclif.io)
[![Version](https://img.shields.io/npm/v/sourcescape-cli.svg)](https://npmjs.org/package/sourcescape-cli)
[![Downloads/week](https://img.shields.io/npm/dw/sourcescape-cli.svg)](https://npmjs.org/package/sourcescape-cli)
[![License](https://img.shields.io/npm/l/sourcescape-cli.svg)](https://github.com/gukjoon/sourcescape-cli/blob/master/package.json)

<!-- toc -->
* [Usage](#usage)
* [Commands](#commands)
<!-- tocstop -->
# Usage
<!-- usage -->
```sh-session
$ npm install -g sourcescape
$ sourcescape COMMAND
running command...
$ sourcescape (-v|--version|version)
sourcescape/1.0.2 darwin-arm64 node-v17.6.0
$ sourcescape --help [COMMAND]
USAGE
  $ sourcescape COMMAND
...
```
<!-- usagestop -->
# Commands
<!-- commands -->
* [`sourcescape clean`](#sourcescape-clean)
* [`sourcescape help [COMMAND]`](#sourcescape-help-command)
* [`sourcescape start`](#sourcescape-start)
* [`sourcescape status`](#sourcescape-status)
* [`sourcescape stop`](#sourcescape-stop)

## `sourcescape clean`

Cleans out existing SourceScape containers. Used for upgrading.

```
USAGE
  $ sourcescape clean

OPTIONS
  -d, --data    Wipe datastores.
  -h, --help    show CLI help
  -i, --images  Delete images.

EXAMPLE
  $ sourcescape clean
```

_See code: [src/commands/clean.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.2/src/commands/clean.ts)_

## `sourcescape help [COMMAND]`

display help for sourcescape

```
USAGE
  $ sourcescape help [COMMAND]

ARGUMENTS
  COMMAND  command to show help for

OPTIONS
  --all  see all commands in CLI
```

_See code: [@oclif/plugin-help](https://github.com/oclif/plugin-help/blob/v3.3.1/src/commands/help.ts)_

## `sourcescape start`

Initialize SourceScape.

```
USAGE
  $ sourcescape start

OPTIONS
  -f, --force-pull  Force pull images
  -h, --help        show CLI help
  -p, --port=port   [default: 5000] Expose this port

EXAMPLE
  $ sourcescape start <YOUR_DIRECTORY>
```

_See code: [src/commands/start.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.2/src/commands/start.ts)_

## `sourcescape status`

Get which containers are running.

```
USAGE
  $ sourcescape status

OPTIONS
  -h, --help  show CLI help

EXAMPLE
  $ sourcescape status
```

_See code: [src/commands/status.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.2/src/commands/status.ts)_

## `sourcescape stop`

Shuts down running SourceScape containers.

```
USAGE
  $ sourcescape stop

EXAMPLE
  $ sourcescape stop
```

_See code: [src/commands/stop.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.2/src/commands/stop.ts)_
<!-- commandsstop -->
