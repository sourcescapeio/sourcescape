import axios from "axios";

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
