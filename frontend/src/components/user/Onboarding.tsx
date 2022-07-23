import axios from "axios";
import { Loading } from "components/shared/Loading";
import { useEffect, useState } from "react";
import { Navigate } from "react-router";

export function LocalOnboardingContainer() {

  const [loading, setLoading] = useState(true);
  const [repos, setRepos] = useState([]);
  const [complete, setComplete] = useState(false);

  const completeNux = () => {
    return axios({
      method: 'PUT',
      url: `/api/orgs/-1/nux`,
    }).then(() => {
      setComplete(true);
    }).catch((e) => {
      // this.setState({
      //   error: e.message,
      //   loading: false,
      // });
      return Promise.reject(e);
    });
  }  

  // this should be all in redux
  useEffect(() => {
    axios({
      method: 'GET',
      url: '/api/orgs/-1/repos'
    }).then((resp) => {
      console.warn(resp.data);
      setLoading(false);
      setRepos(resp.data)
    })
  }, []);

  if(loading) {
    return <Loading loading={true} />
  }

  if(complete) {
    return <Navigate to="/console" />
  }

  if (repos.length === 0) {
    return <div>Scanning...</div>
  }

  return <div>
    Indexing...
    <pre>{JSON.stringify(repos, null, 2)}</pre>
  </div>
}
