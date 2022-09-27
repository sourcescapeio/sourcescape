import {Command, flags} from '@oclif/command'
import { flatMap, reduce } from 'lodash';
import open from 'open';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { exit } from 'process';
import axios from 'axios';
import Table from 'cli-table';

export default class ListScans extends Command {

  async run() {  
    const scansQuery = {
      operationName: "GetScans",
      query: `query GetScans {
        scans {
          id
          path
          progress
        }        
      }`,
      variables: {}
    }

    const response = await axios({
      url: 'http://localhost:9003/graphql',
      method: 'POST',
      headers: {
        "content-type": "application/json",
      },
      data: JSON.stringify(scansQuery),
      validateStatus(status) {
          return true;
      },
    })

    if (response.status !== 200) {
      console.warn('Error listing scans')
      console.error(response.data)
    }

    let table = new Table({
      head: ['Directory', 'Progress']
    });
    response.data.data.scans.forEach((r: any) => {
      table.push([r.path, r.progress])
    });
    console.log(table.toString())
  }
}
