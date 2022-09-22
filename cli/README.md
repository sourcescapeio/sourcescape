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
$ npm install -g @sourcescape/cli
$ sourcescape COMMAND
running command...
$ sourcescape (-v|--version|version)
@sourcescape/cli/0.2.3 darwin-x64 node-v15.8.0
$ sourcescape --help [COMMAND]
USAGE
  $ sourcescape COMMAND
...
```
<!-- usagestop -->
# Commands
<!-- commands -->
* [`sourcescape clean`](#sourcescape-clean)
* [`sourcescape down`](#sourcescape-down)
* [`sourcescape help [COMMAND]`](#sourcescape-help-command)
* [`sourcescape status`](#sourcescape-status)
* [`sourcescape up`](#sourcescape-up)
* [`sourcescape watcher [FILE]`](#sourcescape-watcher-file)

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

_See code: [src/commands/clean.ts](https://github.com/sourcescapeio/sourcescape-cli/blob/v0.2.3/src/commands/clean.ts)_

## `sourcescape down`

Shuts down running SourceScape containers.

```
USAGE
  $ sourcescape down

OPTIONS
  -h, --help        show CLI help
  -n, --no-watcher  Connect to preexisting watcher

EXAMPLE
  $ sourcescape down
```

_See code: [src/commands/down.ts](https://github.com/sourcescapeio/sourcescape-cli/blob/v0.2.3/src/commands/down.ts)_

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

_See code: [@oclif/plugin-help](https://github.com/oclif/plugin-help/blob/v3.2.1/src/commands/help.ts)_

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

_See code: [src/commands/status.ts](https://github.com/sourcescapeio/sourcescape-cli/blob/v0.2.3/src/commands/status.ts)_

## `sourcescape up`

Initialize SourceScape.

```
USAGE
  $ sourcescape up

OPTIONS
  -h, --help        show CLI help
  -n, --no-watcher  Connect to preexisting watcher

EXAMPLE
  $ sourcescape up <YOUR_DIRECTORY>
```

_See code: [src/commands/up.ts](https://github.com/sourcescapeio/sourcescape-cli/blob/v0.2.3/src/commands/up.ts)_

## `sourcescape watcher [FILE]`

[DEBUG] Stand up the Watcher daemon.

```
USAGE
  $ sourcescape watcher [FILE]

OPTIONS
  -h, --help  show CLI help

EXAMPLE
  $ sourcescape watcher
```

_See code: [src/commands/watcher.ts](https://github.com/sourcescapeio/sourcescape-cli/blob/v0.2.3/src/commands/watcher.ts)_
<!-- commandsstop -->
