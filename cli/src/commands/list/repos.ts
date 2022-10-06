import {Command, flags} from '@oclif/command'
import { flatMap, reduce } from 'lodash';
import open from 'open';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { exit } from 'process';
import axios from 'axios';
import Table from 'cli-table';
import { graphQLClient } from '../../lib/graphql';
import { gql } from '@apollo/client/core';

export default class ListRepos extends Command {

  static flags = {
    port: flags.integer({char: 'p', description: 'Expose this port', default: 5001}),
    debug: flags.boolean({char: 'd', description: 'use debug mode', default: false}),
  }  

  async run() {  
    const {flags} = this.parse(ListRepos);
  
    const client = graphQLClient(flags.port, flags.debug);

    const response = await client.query({
      query: gql`query GetRepos {
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
      }`
    })

    const table = new Table({
      head: ['Name', 'Indexes']
    })
    response.data.repos.forEach((r: any) => {      
      const indexes = r.indexes.map((i: any) => {
        const progressFlag = (i.indexProgress === 100) ? '[x]' : `[${i.indexProgress}]`;
        const dirtyFlag = i.dirty ? '*' : '';
        return `${i.sha}${dirtyFlag}${progressFlag}`
      })

      table.push([r.name, indexes.join('\n')]);
    });
    console.log(table.toString());
  }
}
