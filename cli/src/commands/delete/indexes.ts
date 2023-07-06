import {Command, Flags, Args} from '@oclif/core'
import { graphQLClient, runGraphQL } from '../../lib/graphql';
import { getRepo, getRepo2 } from '../../lib/repo';
import { gql } from '@apollo/client/core';

export default class DeleteIndex extends Command {

  static flags = {
    port: Flags.integer({char: 'p', description: 'Expose this port', default: 5001}),
    debug: Flags.boolean({char: 'd', description: 'use debug mode', default: false}),
  }

  static args = {
    repo: Args.string({required: true, description: 'repo to delete'})
  };

  async run() {  
    const {args, flags} = await this.parse(DeleteIndex);

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
