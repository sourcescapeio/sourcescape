import {Command, flags} from '@oclif/command'
import { flatMap, reduce } from 'lodash';
import open from 'open';
import { fork, spawn } from 'child_process';
import { join, resolve } from 'path';
import { openSync, watch } from 'fs';
import { exit } from 'process';
import axios from 'axios';
import { graphQLClient, runGraphQL } from '../lib/graphql';
import { getRepo, getRepo2 } from '../lib/repo';
import { SingleBar, Presets, MultiBar } from 'cli-progress';
import { FetchResult, gql } from '@apollo/client/core';

export default class RunIndex extends Command {

  static flags = {
    port: flags.integer({char: 'p', description: 'Expose this port', default: 5001}),
    debug: flags.boolean({char: 'd', description: 'use debug mode', default: false}),
  }

  static args = [{
    name: 'directory',
    required: true,
    description: 'directory to index',
  }];

  async run() {  
    const {args, flags} = this.parse(RunIndex);
    const client = graphQLClient(flags.port, flags.debug);

    const directory = resolve(args.directory);
    console.warn(directory)
    
    const running = client.mutate({
      mutation: gql`mutation RunIndexRepo {
        indexRepo(directory: "${directory}")
      }`
    });

    const bar = new MultiBar({
      clearOnComplete: true,
      hideCursor: true
    }, Presets.shades_classic);

    const bar1 = bar.create(100, 0, {filename: 'Cloning'});
    const bar2 = bar.create(100, 0, {filename: 'Indexing'});
    const bar3 = bar.create(100, 0, {filename: 'Linking'});    

    const cloneStream = client.subscribe({
      query: gql`
        subscription CloningProgress {
          cloneProgress {
            indexId
            repoId
            progress
          }
        }
      `
    }).subscribe((v) => {
      bar1.update(v.data.cloneProgress.progress)
    })

    const indexStream = client.subscribe({
      query: gql`
        subscription IndexingProgress {
          indexProgress {
            indexId
            repoId
            progress
          }
        }
      `
    }).subscribe((v) => {
      bar2.update(v.data.indexProgress.progress)
    })

    const linkStream = client.subscribe({
      query: gql`
        subscription LinkingProgress {
          linkProgress {
            indexId
            repoId
            progress
          }
        }
      `
    }).subscribe((v) => {
      bar3.update(v.data.linkProgress.progress)
    })    

    await running
    bar.stop();

    console.warn('DONE')
    cloneStream.unsubscribe();
    indexStream.unsubscribe();
    linkStream.unsubscribe();
  }
}
