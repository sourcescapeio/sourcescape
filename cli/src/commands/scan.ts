import {Command, flags} from '@oclif/command'
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
    port: flags.integer({char: 'p', description: 'Expose this port', default: 5001}),
    debug: flags.boolean({char: 'd', description: 'use debug mode', default: false}),
  }

  static args = [{
    name: 'directory',
    required: true,
    description: 'directory',
  }];

  async run() {  
    const {args, flags} = this.parse(Scan);

    const fullPath = flags.debug ? resolve(args.directory) : `/external/${resolve(args.directory)}`;
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
