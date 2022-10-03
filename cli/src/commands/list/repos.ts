import {Command, flags} from '@oclif/command'
import { flatMap, reduce } from 'lodash';
import open from 'open';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { exit } from 'process';
import axios from 'axios';
import Table from 'cli-table';
import { runGraphQL } from '../../lib/graphql';

export default class ListRepos extends Command {

  static flags = {
    port: flags.integer({char: 'p', description: 'Expose this port', default: 5001}),
    debug: flags.boolean({char: 'd', description: 'use debug mode', default: false}),
  }  

  async run() {  
    const {flags} = this.parse(ListRepos);
  
    const response = await runGraphQL(flags.port, flags.debug, {
      operationName: "GetRepos",
      query: `query GetRepos {
        repos {
          id
          path
          name
          intent
          indexes {
            id
            dirty
            sha
            cloneProgress
            indexProgress
          }
        }
      }`,
      variables: {}
    });

    const table = new Table({
      head: ['Name', '', 'Status', 'Indexes']
    })
    response.data.data.repos.forEach((r: any) => {      
      const indexes = r.indexes.map((i: any) => {
        const progressFlag = (i.indexProgress === 100) ? '[x]' : `[${i.indexProgress}]`;
        const dirtyFlag = i.dirty ? '*' : '';
        return `${i.sha}${dirtyFlag}${progressFlag}`
      })

      table.push([r.name, r.id, r.intent, indexes.join('\n')]);
    });
    console.log(table.toString());
  }
}
