import {Command, flags} from '@oclif/command'
import { flatMap, reduce } from 'lodash';
import open from 'open';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { exit } from 'process';
import axios from 'axios';
import { graphQLClient, runGraphQL } from '../../lib/graphql';
import { getRepo, getRepo2 } from '../../lib/repo';
import { gql } from '@apollo/client/core';

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

    const client = graphQLClient(flags.port, flags.debug);
    const chosen = await getRepo2(args.repo, client);

    await client.mutate({
      mutation: gql`mutation DeleteRepoIndexes {
        deleteIndexesForRepo(id: ${chosen.id})
      }`
    });

    console.warn('COMPLETE')
  }
}
