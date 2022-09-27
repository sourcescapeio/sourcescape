import {Command, flags} from '@oclif/command'
import { flatMap, reduce } from 'lodash';
import open from 'open';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { exit } from 'process';
import axios from 'axios';
import { runGraphQL } from '../lib/graphql';

export default class RunIndex extends Command {

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
    const {args, flags} = this.parse(RunIndex);

    const indexes = await runGraphQL(flags.port, flags.debug, {
      operationName: "GetRepos",
      query: `query GetRepos {
        repos {
          id
          path
          name
          intent
          indexes {
            id
            cloneProgress
            indexProgress
          }
        }
      }`,
      variables: {}
    });

    const found1 = indexes.data.data.repos.filter((r: any) => (r.path === args.repo));
    const found2 = indexes.data.data.repos.filter((r: any) => (r.id === args.repo));
    const found3 = indexes.data.data.repos.filter((r: any) => (r.name === args.repo));

    let chosen: any;

    if (found1.length === 1) {
      chosen = found1[0];
    } else if (found2.length === 1) {
      chosen = found2[0];
    } else if (found3.length === 1) {
      chosen = found3[0];
    } else if (found3.length > 2) {
      throw new Error(`multiple repos with name ${args.repo}. use id or full path to select`)
    } else {
      throw new Error("invalid id")
    }
    console.warn(chosen)

    // const fullPath = flags.debug ? resolve(args.directory) : `/external/${resolve(args.directory)}`;
    // console.warn(fullPath);

    const response = await runGraphQL(flags.port, flags.debug, {
      operationName: "SelectRepo",
      query: `mutation SelectRepo {
        selectRepo(id: ${chosen.id})
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
