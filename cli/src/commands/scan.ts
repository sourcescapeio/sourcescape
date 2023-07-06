import {Command, Flags, Args} from '@oclif/core'
import { flatMap, reduce } from 'lodash';
import open from 'open';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { exit } from 'process';
import axios from 'axios';
import { runGraphQL } from '../lib/graphql';

export default class Scan extends Command {

  static flags = {
    port: Flags.integer({char: 'p', description: 'Expose this port', default: 5001}),
    debug: Flags.boolean({char: 'd', description: 'use debug mode', default: false}),
    debugDirectory: Flags.boolean({char: 'v', description: 'dont append docker volume prefix', default: false}),
  }

  static args = {
    directory: Args.string({required: true, description: 'directory'}),
  };

  async run() {  
    const {args, flags} = await this.parse(Scan);

    const fullPath = flags.debugDirectory ? resolve(args.directory) : `/external/${resolve(args.directory)}`;
    console.warn(fullPath);

    const response = await runGraphQL(flags.port, flags.debug, {
      operationName: "ScanDirectory",
      query: `mutation ScanDirectory {
        createScan(path: "${fullPath}") {
          id
          path
        }        
      }`,
      variables: {}
    });

    if (response.status !== 200) {
      console.warn('Error requesting scan')
      console.error(response.data)
    }

    console.warn('COMPLETE')
  }
}
