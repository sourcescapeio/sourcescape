import {Command, flags} from '@oclif/command'
import { flatMap, reduce } from 'lodash';
import open from 'open';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { exit } from 'process';
import axios from 'axios';
import { runGraphQL } from '../../lib/graphql';
import { getRepo } from '../../lib/repo';

export default class DeleteIndex extends Command {

  static flags = {
    port: flags.integer({char: 'p', description: 'Expose this port', default: 5001}),
    debug: flags.boolean({char: 'd', description: 'use debug mode', default: false}),
  }

  static args = [{
    name: 'repo',
    required: true,
    description: 'repo to index',
  }];

  async run() {  
    const {args, flags} = this.parse(DeleteIndex);

    const chosen = await getRepo(args.repo, flags.port, flags.debug);

    console.warn(chosen);

    const response = await runGraphQL(flags.port, flags.debug, {
      operationName: "DeleteRepoIndexes",
      query: `mutation DeleteRepoIndexes {
        deleteIndexesForRepo(id: ${chosen.id})
      }`,
      variables: {}
    });

    if (response.status !== 200) {
      console.warn('Error running indexing')
      console.error(response.data)
    }

    console.warn('COMPLETE')
  }
}
