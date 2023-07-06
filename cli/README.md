sourcescape
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
$ sourcescape (--version)
sourcescape/1.0.4 darwin-arm64 node-v16.18.0
$ sourcescape --help [COMMAND]
USAGE
  $ sourcescape COMMAND
...
```
<!-- usagestop -->
# Commands
<!-- commands -->
* [`sourcescape clean`](#sourcescape-clean)
* [`sourcescape delete:indexes REPO`](#sourcescape-deleteindexes-repo)
* [`sourcescape help [COMMANDS]`](#sourcescape-help-commands)
* [`sourcescape list:repos`](#sourcescape-listrepos)
* [`sourcescape list:scans`](#sourcescape-listscans)
* [`sourcescape run-index REPO`](#sourcescape-run-index-repo)
* [`sourcescape scan DIRECTORY`](#sourcescape-scan-directory)
* [`sourcescape start`](#sourcescape-start)
* [`sourcescape status`](#sourcescape-status)
* [`sourcescape stop`](#sourcescape-stop)

## `sourcescape clean`

Cleans out existing SourceScape containers. Used for upgrading.

```
USAGE
  $ sourcescape clean [-h] [-d] [-i]

FLAGS
  -d, --data    Wipe datastores.
  -h, --help    Show CLI help.
  -i, --images  Delete images.

DESCRIPTION
  Cleans out existing SourceScape containers. Used for upgrading.

EXAMPLES
  $ sourcescape clean
```

_See code: [src/commands/clean.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/clean.ts)_

## `sourcescape delete:indexes REPO`

```
USAGE
  $ sourcescape delete:indexes REPO [-p <value>] [-d]

ARGUMENTS
  REPO  repo to delete

FLAGS
  -d, --debug         use debug mode
  -p, --port=<value>  [default: 5001] Expose this port
```

_See code: [src/commands/delete/indexes.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/delete/indexes.ts)_

## `sourcescape help [COMMANDS]`

Display help for sourcescape.

```
USAGE
  $ sourcescape help [COMMANDS] [-n]

ARGUMENTS
  COMMANDS  Command to show help for.

FLAGS
  -n, --nested-commands  Include all nested commands in the output.

DESCRIPTION
  Display help for sourcescape.
```

_See code: [@oclif/plugin-help](https://github.com/oclif/plugin-help/blob/v5.2.11/src/commands/help.ts)_

## `sourcescape list:repos`

```
USAGE
  $ sourcescape list:repos [-p <value>] [-d]

FLAGS
  -d, --debug         use debug mode
  -p, --port=<value>  [default: 5001] Expose this port
```

_See code: [src/commands/list/repos.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/list/repos.ts)_

## `sourcescape list:scans`

```
USAGE
  $ sourcescape list:scans
```

_See code: [src/commands/list/scans.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/list/scans.ts)_

## `sourcescape run-index REPO`

```
USAGE
  $ sourcescape run-index REPO [-p <value>] [-d]

ARGUMENTS
  REPO  repo to index

FLAGS
  -d, --debug         use debug mode
  -p, --port=<value>  [default: 5001] Expose this port
```

_See code: [src/commands/run-index.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/run-index.ts)_

## `sourcescape scan DIRECTORY`

```
USAGE
  $ sourcescape scan DIRECTORY [-p <value>] [-d] [-v]

ARGUMENTS
  DIRECTORY  directory

FLAGS
  -d, --debug           use debug mode
  -p, --port=<value>    [default: 5001] Expose this port
  -v, --debugDirectory  dont append docker volume prefix
```

_See code: [src/commands/scan.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/scan.ts)_

## `sourcescape start`

Initialize SourceScape.

```
USAGE
  $ sourcescape start [-h] [-f] [-p <value>]

FLAGS
  -f, --force-pull    Force pull images
  -h, --help          Show CLI help.
  -p, --port=<value>  [default: 5001] Expose this port

DESCRIPTION
  Initialize SourceScape.

EXAMPLES
  $ sourcescape start <YOUR_DIRECTORY>
```

_See code: [src/commands/start.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/start.ts)_

## `sourcescape status`

Get which containers are running.

```
USAGE
  $ sourcescape status [-h]

FLAGS
  -h, --help  Show CLI help.

DESCRIPTION
  Get which containers are running.

EXAMPLES
  $ sourcescape status
```

_See code: [src/commands/status.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/status.ts)_

## `sourcescape stop`

Shuts down running SourceScape containers.

```
USAGE
  $ sourcescape stop

DESCRIPTION
  Shuts down running SourceScape containers.

EXAMPLES
  $ sourcescape stop
```

_See code: [src/commands/stop.ts](https://github.com/sourcescapeio/sourcescape/blob/v1.0.4/src/commands/stop.ts)_
<!-- commandsstop -->
