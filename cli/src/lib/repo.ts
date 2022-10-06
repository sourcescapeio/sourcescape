import { ApolloClient, gql } from '@apollo/client/core';
import { runGraphQL } from './graphql';

export async function getRepo2(repo: string, client: ApolloClient<any>) {
  const indexes = await client.query({
    query: gql`
      query GetRepos {
        repos {
          id
          path
          name
        }        
      }
    `
  });

  const { repos } = indexes.data;

  const found1 = repos.filter((r: any) => (r.path === repo));
  const found2 = repos.filter((r: any) => (r.id === repo));
  const found3 = repos.filter((r: any) => (r.name === repo));

  let chosen: any;

  if (found1.length === 1) {
    chosen = found1[0];
  } else if (found2.length === 1) {
    chosen = found2[0];
  } else if (found3.length === 1) {
    chosen = found3[0];
  } else if (found3.length > 2) {
    throw new Error(`multiple repos with name ${repo}. use id or full path to select`)
  } else {
    throw new Error("invalid id")
  }

  return chosen;
}

export async function getRepo(repo: string, port: number, debug: boolean) {
  const indexes = await runGraphQL(port, debug, {
    operationName: "GetRepos",
    query: `query GetRepos {
      repos {
        id
        path
        name
        intent
        indexes {
          id
          sha
          cloneProgress
          indexProgress
        }
      }
    }`,
    variables: {}
  });

  const found1 = indexes.data.data.repos.filter((r: any) => (r.path === repo));
  const found2 = indexes.data.data.repos.filter((r: any) => (r.id === repo));
  const found3 = indexes.data.data.repos.filter((r: any) => (r.name === repo));

  let chosen: any;

  if (found1.length === 1) {
    chosen = found1[0];
  } else if (found2.length === 1) {
    chosen = found2[0];
  } else if (found3.length === 1) {
    chosen = found3[0];
  } else if (found3.length > 2) {
    throw new Error(`multiple repos with name ${repo}. use id or full path to select`)
  } else {
    throw new Error("invalid id")
  }

  return chosen;
}
