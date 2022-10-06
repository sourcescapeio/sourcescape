import axios from "axios";
import { ApolloClient } from "@apollo/client/core";
import { HttpLink, InMemoryCache, split } from '@apollo/client/core';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { createClient } from 'graphql-ws';
import { getMainDefinition } from '@apollo/client/utilities';
import fetch from 'cross-fetch';
import Websocket from 'ws';

export async function runGraphQL(port: number, debug: boolean, graphql: any) {
  const response = await axios({
    url: debug ? `http://localhost:${port}/graphql` : `http://localhost:${port}/api/graphql`,
    method: 'POST',
    headers: {
      "content-type": "application/json",
    },
    data: JSON.stringify(graphql),
    validateStatus(status) {
        return true;
    },
  })

  return response
}

export function graphQLClient(port: number, debug: boolean){
  const uri = debug ? `http://localhost:${port}/graphql` : `http://localhost:${port}/api/graphql`;
  const url = debug ? `ws://localhost:${port}/graphql` : `ws://localhost:${port}/api/graphql`;

  console.warn(uri, url);

  const httpLink = new HttpLink({
    uri,
    fetch,
  });
  
  const wsLink = new GraphQLWsLink(createClient({
    url,
    webSocketImpl: Websocket,
    connectionParams: {
      authToken: '123'
    }
  }));
  
  const splitLink = split(
    ({ query }) => {
      const definition = getMainDefinition(query);
      return (
        definition.kind === 'OperationDefinition' &&
        definition.operation === 'subscription'
      );
    },
    wsLink,
    httpLink,
  );

  return new ApolloClient({
    link: splitLink,
    cache: new InMemoryCache(),
  });
}
